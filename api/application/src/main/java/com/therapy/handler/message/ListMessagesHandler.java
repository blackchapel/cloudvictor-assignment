package com.therapy.handler.message;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.handler.BaseHandler;
import com.therapy.model.CallerContext;
import com.therapy.model.Message;
import com.therapy.model.PaginatedList;
import com.therapy.repository.MessageRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;

public class ListMessagesHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final MessageRepository REPO = new MessageRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        String clientId    = ApiGatewayUtils.getQueryParam(event, "clientId");
        String therapistId = ApiGatewayUtils.getQueryParam(event, "therapistId");

        if (isBlank(clientId))    return ApiGatewayUtils.badRequest("'clientId' query parameter is required.");
        if (isBlank(therapistId)) return ApiGatewayUtils.badRequest("'therapistId' query parameter is required.");

        boolean isParty = caller.getUserId().equals(clientId) || caller.getUserId().equals(therapistId);
        if (!isParty) {
            return ApiGatewayUtils.forbidden("You are not a participant in this conversation.");
        }

        int pageSize = Math.min(ApiGatewayUtils.getQueryParamInt(event, "pageSize", 20), 100);
        int page = Math.max(ApiGatewayUtils.getQueryParamInt(event, "page", 1), 1);

        List<Message> messages;
        try {
            messages = REPO.listThread(clientId, therapistId, pageSize, null);
        } catch (Exception e) {
            context.getLogger().log("ListMessages error [clientId=" + clientId + ", therapistId=" + therapistId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        return ApiGatewayUtils.ok(new PaginatedList<>(messages, page, pageSize, null));
    }
}
