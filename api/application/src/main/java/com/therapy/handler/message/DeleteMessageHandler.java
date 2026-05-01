package com.therapy.handler.message;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.handler.BaseHandler;
import com.therapy.model.CallerContext;
import com.therapy.model.Message;
import com.therapy.repository.MessageRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Optional;

public class DeleteMessageHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final MessageRepository REPO = new MessageRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        String messageId = ApiGatewayUtils.getPathParam(event, "messageId");
        if (isBlank(messageId)) {
            return ApiGatewayUtils.badRequest("'messageId' path parameter is required.");
        }

        Optional<Message> opt;
        try {
            opt = REPO.findByMessageId(messageId);
        } catch (Exception e) {
            context.getLogger().log("DeleteMessage lookup error [messageId=" + messageId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        // Return 403 rather than 404 to avoid leaking message existence to non-parties
        if (opt.isEmpty()) {
            return ApiGatewayUtils.forbidden("You do not have access to this message.");
        }

        Message message = opt.get();

        // Only the sender may delete their own message
        if (!caller.getUserId().equals(message.getSenderId())) {
            return ApiGatewayUtils.forbidden("You do not have access to this message.");
        }

        try {
            REPO.delete(message.getClientId(), message.getTherapistId(),
                    message.getSentAt(), message.getMessageId());
        } catch (ConditionalCheckFailedException e) {
            // Message was already deleted by a concurrent request — treat as success
            return ApiGatewayUtils.noContent();
        } catch (Exception e) {
            context.getLogger().log("DeleteMessage error [messageId=" + messageId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        return ApiGatewayUtils.noContent();
    }
}
