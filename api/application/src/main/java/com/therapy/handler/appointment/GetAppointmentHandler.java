package com.therapy.handler.appointment;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.handler.BaseHandler;
import com.therapy.model.Appointment;
import com.therapy.model.CallerContext;
import com.therapy.repository.AppointmentRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Optional;

public class GetAppointmentHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final AppointmentRepository REPO = new AppointmentRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        String appointmentId = ApiGatewayUtils.getPathParam(event, "appointmentId");
        if (isBlank(appointmentId)) {
            return ApiGatewayUtils.badRequest("'appointmentId' path parameter is required.");
        }

        Optional<Appointment> opt;
        try {
            opt = REPO.findById(appointmentId);
        } catch (Exception e) {
            context.getLogger().log("GetAppointment error [id=" + appointmentId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        if (opt.isEmpty()) {
            return ApiGatewayUtils.notFound("Appointment not found.");
        }

        Appointment appt = opt.get();

        // Only the client who requested it or the therapist who received it may view
        boolean isParty = caller.isTherapist()
                ? caller.getUserId().equals(appt.getTherapistId())
                : caller.getUserId().equals(appt.getClientId());

        if (!isParty) {
            return ApiGatewayUtils.forbidden("You do not have permission to access this appointment.");
        }

        appt.setClientSession(null);
        return ApiGatewayUtils.ok(appt);
    }
}
