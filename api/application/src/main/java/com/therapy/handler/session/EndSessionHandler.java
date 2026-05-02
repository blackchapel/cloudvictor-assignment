package com.therapy.handler.session;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.handler.BaseHandler;
import com.therapy.model.Appointment;
import com.therapy.model.CallerContext;
import com.therapy.model.Session;
import com.therapy.repository.AppointmentRepository;
import com.therapy.repository.SessionRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.Optional;

public class EndSessionHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final SessionRepository     SESSION_REPO = new SessionRepository(DDB);
    private static final AppointmentRepository APPT_REPO    = new AppointmentRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        if (!caller.isTherapist()) {
            return ApiGatewayUtils.forbidden("Only therapists can end sessions.");
        }

        String sessionId = ApiGatewayUtils.getPathParam(event, "sessionId");
        if (isBlank(sessionId)) {
            return ApiGatewayUtils.badRequest("'sessionId' path parameter is required.");
        }

        Optional<Session> opt;
        try {
            opt = SESSION_REPO.findById(sessionId);
        } catch (Exception e) {
            context.getLogger().log("EndSession lookup error [sessionId=" + sessionId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        if (opt.isEmpty()) {
            return ApiGatewayUtils.notFound("Session not found.");
        }

        Session session = opt.get();

        if (!caller.getUserId().equals(session.getTherapistId())) {
            return ApiGatewayUtils.forbidden("You do not have permission to end this session.");
        }

        if (!"IN_PROGRESS".equals(session.getStatus())) {
            return ApiGatewayUtils.unprocessable(
                    "Session cannot be ended. Current status is '" + session.getStatus()
                    + "'; expected IN_PROGRESS.");
        }

        String nowStr = Instant.now().toString();
        try {
            SESSION_REPO.markCompleted(sessionId, nowStr);
        } catch (ConditionalCheckFailedException e) {
            return ApiGatewayUtils.unprocessable("Session status changed before the operation could complete. Please retry.");
        } catch (Exception e) {
            context.getLogger().log("EndSession update error [sessionId=" + sessionId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        // Complete the confirmed appointment
        String confirmedApptId = session.getConfirmedAppointmentId();
        if (!isBlank(confirmedApptId)) {
            try {
                Optional<Appointment> apptOpt = APPT_REPO.findById(confirmedApptId);
                if (apptOpt.isPresent() && "CONFIRMED".equals(apptOpt.get().getStatus())) {
                    APPT_REPO.updateStatus(confirmedApptId, "COMPLETED", nowStr);
                }
            } catch (Exception e) {
                // Log but do not fail — session is already completed
                context.getLogger().log("EndSession appointment completion error [apptId=" + confirmedApptId + "]: " + e.getMessage());
            }
        }

        Optional<Session> updated = SESSION_REPO.findById(sessionId);
        if (updated.isEmpty()) return ApiGatewayUtils.notFound("Session not found.");
        return ApiGatewayUtils.ok(updated.get());
    }
}
