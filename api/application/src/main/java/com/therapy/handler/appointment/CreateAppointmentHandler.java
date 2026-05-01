package com.therapy.handler.appointment;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.therapy.handler.BaseHandler;
import com.therapy.model.Appointment;
import com.therapy.model.CallerContext;
import com.therapy.model.Session;
import com.therapy.repository.AppointmentRepository;
import com.therapy.repository.MappingRepository;
import com.therapy.repository.SessionRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import com.therapy.util.IdGenerator;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.util.Optional;

public class CreateAppointmentHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final AppointmentRepository APPT_REPO    = new AppointmentRepository(DDB);
    private static final SessionRepository     SESSION_REPO  = new SessionRepository(DDB);
    private static final MappingRepository     MAPPING_REPO  = new MappingRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        if (caller.isTherapist()) {
            return ApiGatewayUtils.forbidden("Only clients can create appointments.");
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

        String sessionId = body.hasNonNull("sessionId") ? body.get("sessionId").asText("").trim() : null;
        if (isBlank(sessionId)) {
            return ApiGatewayUtils.badRequest("'sessionId' is required.");
        }

        // Verify session exists and is available
        Optional<Session> sessionOpt;
        try {
            sessionOpt = SESSION_REPO.findById(sessionId);
        } catch (Exception e) {
            context.getLogger().log("CreateAppointment session lookup error: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }
        if (sessionOpt.isEmpty()) {
            return ApiGatewayUtils.notFound("Session not found.");
        }
        Session session = sessionOpt.get();
        if (!Boolean.TRUE.equals(session.getIsAvailable())) {
            return ApiGatewayUtils.unprocessable("Session is not available for booking.");
        }

        String clientId    = caller.getUserId();
        String therapistId = session.getTherapistId();

        // Prevent duplicate appointment for this client+session
        try {
            if (APPT_REPO.existsByClientAndSession(clientId, sessionId)) {
                return ApiGatewayUtils.conflict("You already have an appointment for this session.");
            }
        } catch (Exception e) {
            context.getLogger().log("CreateAppointment dedup check error: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        String now = Instant.now().toString();
        Appointment appt = new Appointment();
        appt.setAppointmentId(IdGenerator.appointmentId());
        appt.setSessionId(sessionId);
        appt.setClientId(clientId);
        appt.setTherapistId(therapistId);
        appt.setStatus("PENDING");
        appt.setClientSession(clientId + "#" + sessionId);
        appt.setRequestedAt(now);
        appt.setUpdatedAt(now);

        try {
            APPT_REPO.create(appt);
            SESSION_REPO.incrementPendingCount(sessionId);
        } catch (Exception e) {
            context.getLogger().log("CreateAppointment write error [clientId=" + clientId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        appt.setClientSession(null);
        return ApiGatewayUtils.created(appt);
    }
}
