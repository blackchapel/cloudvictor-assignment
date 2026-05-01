package com.therapy.handler.session;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.handler.BaseHandler;
import com.therapy.model.CallerContext;
import com.therapy.model.Session;
import com.therapy.repository.AppointmentRepository;
import com.therapy.repository.SessionRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Optional;

public class GetSessionHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final SessionRepository REPO = new SessionRepository(DDB);
    private static final AppointmentRepository APPT_REPO = new AppointmentRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        String sessionId = ApiGatewayUtils.getPathParam(event, "sessionId");
        if (isBlank(sessionId)) {
            return ApiGatewayUtils.badRequest("'sessionId' path parameter is required.");
        }

        Optional<Session> opt;
        try {
            opt = REPO.findById(sessionId);
        } catch (Exception e) {
            context.getLogger().log("GetSession error [sessionId=" + sessionId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        if (opt.isEmpty()) {
            return ApiGatewayUtils.notFound("Session not found.");
        }

        Session session = opt.get();

        if (caller.isTherapist()) {
            if (!caller.getUserId().equals(session.getTherapistId())) {
                return ApiGatewayUtils.forbidden("You do not have permission to access this session.");
            }
        } else {
            // Client may view if session is available OR they have an active appointment
            boolean canView = Boolean.TRUE.equals(session.getIsAvailable());
            if (!canView) {
                try {
                    canView = APPT_REPO.hasActiveAppointment(caller.getUserId(), sessionId);
                } catch (Exception e) {
                    context.getLogger().log("GetSession appointment check error: " + e.getMessage());
                    return ApiGatewayUtils.internalError();
                }
            }
            if (!canView) {
                return ApiGatewayUtils.forbidden("You do not have permission to access this session.");
            }
            session.setPrivateNotes(null);
            // sharedNotes visible only once session has started or completed
            String status = session.getStatus();
            if (!"IN_PROGRESS".equals(status) && !"COMPLETED".equals(status)) {
                session.setSharedNotes(null);
            }
        }

        return ApiGatewayUtils.ok(session);
    }
}
