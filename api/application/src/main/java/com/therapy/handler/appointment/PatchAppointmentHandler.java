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
import com.therapy.repository.SessionRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class PatchAppointmentHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final AppointmentRepository REPO         = new AppointmentRepository(DDB);
    private static final SessionRepository     SESSION_REPO = new SessionRepository(DDB);

    private static final Set<String> VALID_STATUSES = Set.of(
            "CONFIRMED", "CANCELLED", "COMPLETED", "REJECTED");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        String appointmentId = ApiGatewayUtils.getPathParam(event, "appointmentId");
        if (isBlank(appointmentId)) {
            return ApiGatewayUtils.badRequest("'appointmentId' path parameter is required.");
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

        String newStatus = body.hasNonNull("status") ? body.get("status").asText("").trim().toUpperCase() : null;
        if (isBlank(newStatus) || !VALID_STATUSES.contains(newStatus)) {
            return ApiGatewayUtils.badRequest("'status' must be one of: CONFIRMED, CANCELLED, COMPLETED, REJECTED.");
        }

        Optional<Appointment> opt;
        try {
            opt = REPO.findById(appointmentId);
        } catch (Exception e) {
            context.getLogger().log("PatchAppointment lookup error [id=" + appointmentId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        if (opt.isEmpty()) {
            return ApiGatewayUtils.notFound("Appointment not found.");
        }

        Appointment appt = opt.get();
        String currentStatus = appt.getStatus();

        // Party check
        boolean isParty = caller.isTherapist()
                ? caller.getUserId().equals(appt.getTherapistId())
                : caller.getUserId().equals(appt.getClientId());

        if (!isParty) {
            return ApiGatewayUtils.forbidden("You do not have permission to update this appointment.");
        }

        // Validate transition
        if (!isValidTransition(caller.isTherapist(), currentStatus, newStatus)) {
            return ApiGatewayUtils.unprocessable(
                    "Transition " + currentStatus + " → " + newStatus + " is not allowed.");
        }

        String now = Instant.now().toString();
        try {
            if ("CONFIRMED".equals(newStatus)) {
                // Confirm: update appointment, reject all other pending, mark session booked
                REPO.updateStatus(appointmentId, "CONFIRMED", now);
                List<Appointment> pending = REPO.listPendingBySession(appt.getSessionId());
                for (Appointment other : pending) {
                    if (!other.getAppointmentId().equals(appointmentId)) {
                        REPO.updateStatus(other.getAppointmentId(), "REJECTED", now);
                        SESSION_REPO.decrementPendingCount(appt.getSessionId());
                    }
                }
                SESSION_REPO.decrementPendingCount(appt.getSessionId());
                SESSION_REPO.markBooked(appt.getSessionId(), appointmentId);
            } else if ("CANCELLED".equals(newStatus) && "CONFIRMED".equals(currentStatus)) {
                Optional<Session> sessionOpt = SESSION_REPO.findById(appt.getSessionId());
                if (sessionOpt.isEmpty()) {
                    return ApiGatewayUtils.notFound("Session not found.");
                }
                Session session = sessionOpt.get();
                if (hasSessionStarted(session)) {
                    return ApiGatewayUtils.unprocessable(
                            "Confirmed appointment can only be cancelled before therapist starts the session.");
                }

                REPO.updateStatus(appointmentId, "CANCELLED", now);
                if (caller.isTherapist()) {
                    SESSION_REPO.markCancelledByTherapist(appt.getSessionId());
                } else {
                    SESSION_REPO.markScheduledAndAvailable(appt.getSessionId());
                }
            } else if ("CANCELLED".equals(newStatus) && "PENDING".equals(currentStatus)) {
                REPO.updateStatus(appointmentId, "CANCELLED", now);
                SESSION_REPO.decrementPendingCount(appt.getSessionId());
            } else {
                REPO.updateStatus(appointmentId, newStatus, now);
            }
        } catch (Exception e) {
            context.getLogger().log("PatchAppointment update error [id=" + appointmentId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        appt.setStatus(newStatus);
        appt.setUpdatedAt(now);
        appt.setClientSession(null);
        return ApiGatewayUtils.ok(appt);
    }

    private boolean isValidTransition(boolean isTherapist, String from, String to) {
        if (isTherapist) {
            return ("PENDING".equals(from) && ("CONFIRMED".equals(to) || "REJECTED".equals(to)))
                    || ("CONFIRMED".equals(from) && ("CANCELLED".equals(to) || "COMPLETED".equals(to)));
        } else {
            return ("PENDING".equals(from) || "CONFIRMED".equals(from)) && "CANCELLED".equals(to);
        }
    }

    private boolean hasSessionStarted(Session session) {
        String status = session.getStatus();
        return (session.getStartedAt() != null && !session.getStartedAt().trim().isEmpty())
                || "IN_PROGRESS".equals(status)
                || "COMPLETED".equals(status);
    }
}
