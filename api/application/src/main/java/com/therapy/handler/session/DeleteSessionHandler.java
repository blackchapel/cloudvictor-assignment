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

import java.util.Optional;

public class DeleteSessionHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final SessionRepository REPO = new SessionRepository(DDB);

    // TODO: session can be deleted only if they do not have cofirmed or completed appointments
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        if (!caller.isTherapist()) {
            return ApiGatewayUtils.forbidden("Only therapists can delete sessions.");
        }

        String sessionId = ApiGatewayUtils.getPathParam(event, "sessionId");
        if (isBlank(sessionId)) {
            return ApiGatewayUtils.badRequest("'sessionId' path parameter is required.");
        }

        Optional<Session> opt;
        try {
            opt = REPO.findById(sessionId);
        } catch (Exception e) {
            context.getLogger().log("DeleteSession lookup error [sessionId=" + sessionId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        if (opt.isEmpty()) {
            return ApiGatewayUtils.notFound("Session not found.");
        }
        if (!caller.getUserId().equals(opt.get().getTherapistId())) {
            return ApiGatewayUtils.forbidden("You do not have permission to delete this session.");
        }

        try {
            REPO.delete(sessionId);
        } catch (ConditionalCheckFailedException e) {
            // Concurrent delete — treat as success
            return ApiGatewayUtils.noContent();
        } catch (Exception e) {
            context.getLogger().log("DeleteSession error [sessionId=" + sessionId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        return ApiGatewayUtils.noContent();
    }
}
