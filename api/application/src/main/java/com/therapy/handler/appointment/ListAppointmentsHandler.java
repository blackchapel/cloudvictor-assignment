package com.therapy.handler.appointment;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.handler.BaseHandler;
import com.therapy.model.Appointment;
import com.therapy.model.CallerContext;
import com.therapy.model.PaginatedList;
import com.therapy.model.Session;
import com.therapy.repository.AppointmentRepository;
import com.therapy.repository.SessionRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;
import java.util.Optional;

public class ListAppointmentsHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final AppointmentRepository REPO         = new AppointmentRepository(DDB);
    private static final SessionRepository     SESSION_REPO = new SessionRepository(DDB);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE     = 100;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        int pageSize = Math.min(
                ApiGatewayUtils.getQueryParamInt(event, "pageSize", DEFAULT_PAGE_SIZE),
                MAX_PAGE_SIZE);
        int page = Math.max(ApiGatewayUtils.getQueryParamInt(event, "page", 1), 1);

        List<Appointment> appointments;
        try {
            if (caller.isTherapist()) {
                // Therapist must supply sessionId; verify they own the session
                String sessionId = ApiGatewayUtils.getQueryParam(event, "sessionId");
                if (isBlank(sessionId)) {
                    return ApiGatewayUtils.badRequest("'sessionId' query parameter is required for therapists.");
                }
                Optional<Session> sessionOpt = SESSION_REPO.findById(sessionId);
                if (sessionOpt.isEmpty()) {
                    return ApiGatewayUtils.notFound("Session not found.");
                }
                if (!caller.getUserId().equals(sessionOpt.get().getTherapistId())) {
                    return ApiGatewayUtils.forbidden("You do not own this session.");
                }
                appointments = REPO.listBySession(sessionId, pageSize, null);
            } else {
                // Client lists only their own appointments, optional status filter
                String statusFilter = ApiGatewayUtils.getQueryParam(event, "status");
                appointments = REPO.listByClient(caller.getUserId(), null, statusFilter, pageSize, null);
            }
        } catch (Exception e) {
            context.getLogger().log("ListAppointments error [userId=" + caller.getUserId() + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        appointments.forEach(a -> a.setClientSession(null));
        return ApiGatewayUtils.ok(new PaginatedList<>(appointments, page, pageSize, null));
    }
}
