package com.therapy.handler.session;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.handler.BaseHandler;
import com.therapy.model.CallerContext;
import com.therapy.model.Session;
import com.therapy.repository.SessionRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.Optional;

public class StartSessionHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final SessionRepository REPO = new SessionRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        if (!caller.isTherapist()) {
            return ApiGatewayUtils.forbidden("Only therapists can start sessions.");
        }

        String sessionId = ApiGatewayUtils.getPathParam(event, "sessionId");
        if (isBlank(sessionId)) {
            return ApiGatewayUtils.badRequest("'sessionId' path parameter is required.");
        }

        Optional<Session> opt;
        try {
            opt = REPO.findById(sessionId);
        } catch (Exception e) {
            context.getLogger().log("StartSession lookup error [sessionId=" + sessionId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        if (opt.isEmpty()) {
            return ApiGatewayUtils.notFound("Session not found.");
        }

        Session session = opt.get();

        if (!caller.getUserId().equals(session.getTherapistId())) {
            return ApiGatewayUtils.forbidden("You do not have permission to start this session.");
        }

        if (!"SCHEDULED".equals(session.getStatus())) {
            return ApiGatewayUtils.unprocessable(
                    "Session cannot be started. Current status is '" + session.getStatus()
                    + "'; expected SCHEDULED.");
        }

        if (isBlank(session.getConfirmedAppointmentId())) {
            return ApiGatewayUtils.unprocessable(
                    "Session cannot be started without a CONFIRMED appointment.");
        }

        Instant now = Instant.now();
        Instant scheduledAt;
        try {
            scheduledAt = Instant.parse(session.getScheduledAt());
        } catch (Exception e) {
            context.getLogger().log("StartSession bad scheduledAt [sessionId=" + sessionId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        // Must be on or after scheduledAt
        if (now.isBefore(scheduledAt)) {
            return ApiGatewayUtils.unprocessable(
                    "Session cannot be started before its scheduled time."
                    + " Scheduled: " + session.getScheduledAt()
                    + ", current time: " + now);
        }

        // Must be before the session end time (scheduledAt + durationMinutes)
        Instant sessionEnd = scheduledAt.plusSeconds((long) session.getDurationMinutes() * 60);
        if (now.isAfter(sessionEnd)) {
            return ApiGatewayUtils.unprocessable(
                    "Session window has passed. The session ended at " + sessionEnd
                    + " (scheduledAt + durationMinutes). Cannot start after the window closes.");
        }

        String nowStr = now.toString();
        try {
            REPO.markStarted(sessionId, nowStr);
        } catch (ConditionalCheckFailedException e) {
            return ApiGatewayUtils.unprocessable("Session status changed before the operation could complete. Please retry.");
        } catch (Exception e) {
            context.getLogger().log("StartSession update error [sessionId=" + sessionId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        Optional<Session> updated = REPO.findById(sessionId);
        if (updated.isEmpty()) return ApiGatewayUtils.notFound("Session not found.");
        return ApiGatewayUtils.ok(updated.get());
    }
}
