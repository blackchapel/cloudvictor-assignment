package com.therapy.handler.session;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.therapy.handler.BaseHandler;
import com.therapy.model.CallerContext;
import com.therapy.model.Session;
import com.therapy.repository.SessionRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UpdateSessionHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final SessionRepository REPO = new SessionRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        if (!caller.isTherapist()) {
            return ApiGatewayUtils.forbidden("Only therapists can update sessions.");
        }

        String sessionId = ApiGatewayUtils.getPathParam(event, "sessionId");
        if (isBlank(sessionId)) {
            return ApiGatewayUtils.badRequest("'sessionId' path parameter is required.");
        }

        if (!ApiGatewayUtils.hasBody(event)) {
            return ApiGatewayUtils.badRequest("Request body is required.");
        }

        JsonNode body;
        try {
            body = ApiGatewayUtils.MAPPER.readTree(event.getBody());
        } catch (Exception e) {
            return ApiGatewayUtils.badRequest("Request body is not valid JSON.");
        }

        // Verify session exists and caller owns it before attempting update
        Optional<Session> opt;
        try {
            opt = REPO.findById(sessionId);
        } catch (Exception e) {
            context.getLogger().log("UpdateSession lookup error [sessionId=" + sessionId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }
        if (opt.isEmpty()) {
            return ApiGatewayUtils.notFound("Session not found.");
        }
        if (!caller.getUserId().equals(opt.get().getTherapistId())) {
            return ApiGatewayUtils.forbidden("You do not have permission to update this session.");
        }

        Map<String, Object> fields = new HashMap<>();

        if (body.hasNonNull("title")) {
            String title = body.get("title").asText("").trim();
            if (title.isBlank()) return ApiGatewayUtils.badRequest("'title' must not be blank.");
            if (title.length() > Session.TITLE_MAX_CHARS)
                return ApiGatewayUtils.badRequest("'title' must not exceed " + Session.TITLE_MAX_CHARS + " characters.");
            fields.put("title", title);
        }
        if (body.hasNonNull("description")) {
            String description = body.get("description").asText("").trim();
            if (description.length() > Session.DESCRIPTION_MAX_CHARS)
                return ApiGatewayUtils.badRequest("'description' must not exceed " + Session.DESCRIPTION_MAX_CHARS + " characters.");
            fields.put("description", description);
        }
        if (body.hasNonNull("scheduledAt")) {
            String scheduledAt = body.get("scheduledAt").asText("").trim();
            if (scheduledAt.isBlank()) return ApiGatewayUtils.badRequest("'scheduledAt' must not be blank.");
            String status = opt.get().getStatus();
            if (("CONFIRMED".equals(status) || "COMPLETED".equals(status)
                    || "IN_PROGRESS".equals(status) || "CANCELLED".equals(status))
                    && !opt.get().getScheduledAt().equals(scheduledAt)) {
                return ApiGatewayUtils.badRequest("Cannot change 'scheduledAt' for sessions that has " + status + " status.");
            }
            try {
                Instant.parse(scheduledAt);
            } catch (DateTimeParseException e) {
                return ApiGatewayUtils.badRequest("'scheduledAt' must be a valid ISO-8601 datetime.");
            }
            fields.put("scheduledAt", scheduledAt);
        }
        if (body.hasNonNull("durationMinutes")) {
            int dur = body.get("durationMinutes").asInt(0);
            if (dur < 15) return ApiGatewayUtils.badRequest("'durationMinutes' must be at least 15.");
            fields.put("durationMinutes", dur);
        }
        if (body.hasNonNull("privateNotes")) {
            if (!caller.isTherapist())
                return ApiGatewayUtils.forbidden("Only therapists may update privateNotes.");
            String privateNotes = body.get("privateNotes").asText("").trim();
            if (privateNotes.length() > Session.PRIVATE_NOTES_MAX_CHARS)
                return ApiGatewayUtils.badRequest("'privateNotes' must not exceed " + Session.PRIVATE_NOTES_MAX_CHARS + " characters.");
            fields.put("privateNotes", privateNotes);
        }
        if (body.hasNonNull("sharedNotes")) {
            String sharedNotes = body.get("sharedNotes").asText("").trim();
            if (sharedNotes.length() > Session.SHARED_NOTES_MAX_CHARS)
                return ApiGatewayUtils.badRequest("'sharedNotes' must not exceed " + Session.SHARED_NOTES_MAX_CHARS + " characters.");
            fields.put("sharedNotes", sharedNotes);
        }

        if (fields.isEmpty()) {
            // Nothing to update — return current state
            return ApiGatewayUtils.ok(opt.get());
        }

        try {
            REPO.updateWithOwnerCheck(sessionId, caller.getUserId(), fields);
        } catch (ConditionalCheckFailedException e) {
            return ApiGatewayUtils.forbidden("You do not have permission to update this session.");
        } catch (Exception e) {
            context.getLogger().log("UpdateSession error [sessionId=" + sessionId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        Optional<Session> updated = REPO.findById(sessionId);
        if (updated.isEmpty()) return ApiGatewayUtils.notFound("Session not found.");
        return ApiGatewayUtils.ok(updated.get());
    }
}
