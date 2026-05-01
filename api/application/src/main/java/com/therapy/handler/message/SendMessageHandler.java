package com.therapy.handler.message;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.therapy.handler.BaseHandler;
import com.therapy.model.CallerContext;
import com.therapy.model.Message;
import com.therapy.repository.MessageRepository;
import com.therapy.repository.RelationshipRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.IdGenerator;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;

public class SendMessageHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final MessageRepository MSG_REPO = new MessageRepository(DDB);
    private static final RelationshipRepository REL_REPO = new RelationshipRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        if (!ApiGatewayUtils.hasBody(event)) {
            return ApiGatewayUtils.badRequest("Request body is required.");
        }

        JsonNode body;
        try {
            body = ApiGatewayUtils.MAPPER.readTree(event.getBody());
        } catch (Exception e) {
            return ApiGatewayUtils.badRequest("Request body is not valid JSON.");
        }

        String clientId    = body.hasNonNull("clientId")    ? body.get("clientId").asText("").trim()    : null;
        String therapistId = body.hasNonNull("therapistId") ? body.get("therapistId").asText("").trim() : null;
        String content     = body.hasNonNull("content")     ? body.get("content").asText("").trim()     : null;

        if (isBlank(clientId))    return ApiGatewayUtils.badRequest("'clientId' is required.");
        if (isBlank(therapistId)) return ApiGatewayUtils.badRequest("'therapistId' is required.");
        if (isBlank(content))     return ApiGatewayUtils.badRequest("'content' is required and must not be blank.");
        if (content.length() > 2000) {
            return ApiGatewayUtils.badRequest("'content' must not exceed 2000 characters.");
        }

        // Caller must be one of the two named participants
        boolean isParty = caller.getUserId().equals(clientId) || caller.getUserId().equals(therapistId);
        if (!isParty) {
            return ApiGatewayUtils.forbidden("You are not a participant in this conversation.");
        }

        // senderType and senderId are derived from the JWT — never trusted from the request body
        Message message = new Message();
        message.setMessageId(IdGenerator.messageId());
        message.setClientId(clientId);
        message.setTherapistId(therapistId);
        message.setSenderType(caller.getUserType());
        message.setSenderId(caller.getUserId());
        message.setContent(content);
        message.setSentAt(Instant.now().toString());

        try {
            MSG_REPO.save(message);
            REL_REPO.addRelationshipType(clientId, therapistId, "MESSAGE");
        } catch (Exception e) {
            context.getLogger().log("SendMessage error: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        return ApiGatewayUtils.created(message);
    }
}
