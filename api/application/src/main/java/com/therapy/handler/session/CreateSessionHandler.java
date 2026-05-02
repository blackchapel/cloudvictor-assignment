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
import com.therapy.util.IdGenerator;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

public class CreateSessionHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Static client reused across warm Lambda invocations — avoids re-initialising
    // the HTTP connection pool on every request (AWS SDK v2 best practice).
    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final SessionRepository REPO = new SessionRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        if (!caller.isTherapist()) {
            return ApiGatewayUtils.forbidden("Only therapists can create sessions.");
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

        // Required field validation
        String title = body.hasNonNull("title") ? body.get("title").asText("").trim() : null;
        String scheduledAt = body.hasNonNull("scheduledAt") ? body.get("scheduledAt").asText("").trim() : null;
        if (isBlank(title))       return ApiGatewayUtils.badRequest("'title' is required and must not be blank.");
        if (isBlank(scheduledAt)) return ApiGatewayUtils.badRequest("'scheduledAt' is required and must not be blank.");

        if (!body.hasNonNull("durationMinutes")) {
            return ApiGatewayUtils.badRequest("'durationMinutes' is required.");
        }
        int duration = body.get("durationMinutes").asInt(0);
        if (duration < 15) {
            return ApiGatewayUtils.badRequest("'durationMinutes' must be at least 15.");
        }
        if (duration > Session.MAX_DURATION_MINUTES) {
            return ApiGatewayUtils.badRequest("'durationMinutes' must not exceed " + Session.MAX_DURATION_MINUTES + ".");
        }

        // Validate scheduledAt is a parseable ISO-8601 datetime and is in the future
        Instant scheduledInstant;
        try {
            scheduledInstant = Instant.parse(scheduledAt);
        } catch (DateTimeParseException e) {
            return ApiGatewayUtils.badRequest("'scheduledAt' must be a valid ISO-8601 datetime (e.g. 2024-06-01T10:00:00Z).");
        }
        if (!scheduledInstant.isAfter(Instant.now())) {
            return ApiGatewayUtils.badRequest("'scheduledAt' must be a future datetime.");
        }

        if (title.length() > Session.TITLE_MAX_CHARS) {
            return ApiGatewayUtils.badRequest("'title' must not exceed " + Session.TITLE_MAX_CHARS + " characters.");
        }

        String description = body.hasNonNull("description") ? body.get("description").asText("").trim() : null;
        if (description != null && description.length() > Session.DESCRIPTION_MAX_CHARS) {
            return ApiGatewayUtils.badRequest("'description' must not exceed " + Session.DESCRIPTION_MAX_CHARS + " characters.");
        }

        // Overlap check: reject if the therapist has any non-CANCELLED session whose
        // time window [existingStart, existingStart + existingDuration) overlaps with
        // [scheduledInstant, scheduledInstant + duration).
        // Query window: [newStart - MAX_DURATION, newEnd] — bounded by the 4-hour cap.
        Instant newEnd = scheduledInstant.plusSeconds((long) duration * 60);
        Instant queryFrom = scheduledInstant.minusSeconds((long) Session.MAX_DURATION_MINUTES * 60);
        List<Session> candidates;
        try {
            candidates = REPO.findByTherapistInRange(
                    caller.getUserId(), queryFrom.toString(), newEnd.toString());
        } catch (Exception e) {
            context.getLogger().log("Overlap check error [therapistId=" + caller.getUserId() + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }
        for (Session existing : candidates) {
            if ("CANCELLED".equals(existing.getStatus())) continue;
            Instant existingStart = Instant.parse(existing.getScheduledAt());
            Instant existingEnd   = existingStart.plusSeconds((long) existing.getDurationMinutes() * 60);
            if (existingStart.isBefore(newEnd) && existingEnd.isAfter(scheduledInstant)) {
                return ApiGatewayUtils.conflict(
                        "Session overlaps with existing session '" + existing.getSessionId() + "' (" +
                        existing.getScheduledAt() + ", " + existing.getDurationMinutes() + " min).");
            }
        }

        String now = Instant.now().toString();
        Session session = new Session();
        session.setSessionId(IdGenerator.sessionId());
        session.setTherapistId(caller.getUserId());
        session.setTitle(title);
        session.setScheduledAt(scheduledAt);
        session.setDurationMinutes(duration);
        session.setStatus("SCHEDULED");
        session.setIsAvailable(true);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        if (!isBlank(description)) session.setDescription(description);

        try {
            REPO.create(session);
        } catch (Exception e) {
            context.getLogger().log("CreateSession error [therapistId=" + caller.getUserId() + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        return ApiGatewayUtils.created(session);
    }
}
