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
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;
import java.util.Optional;

/**
 * PUT /messages/{messageId}
 * Only the sender may edit their message within 10 minutes of sending.
 */
public class UpdateMessageHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final MessageRepository REPO = new MessageRepository(DDB);

    private static final int EDIT_WINDOW_SECONDS = 600;
    private static final int CONTENT_MAX_CHARS = 2000;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        String messageId = ApiGatewayUtils.getPathParam(event, "messageId");
        if (isBlank(messageId)) {
            return ApiGatewayUtils.badRequest("'messageId' path parameter is required.");
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

        String content = body.hasNonNull("content") ? body.get("content").asText("").trim() : null;
        if (isBlank(content)) {
            return ApiGatewayUtils.badRequest("'content' is required.");
        }
        if (content.length() > CONTENT_MAX_CHARS) {
            return ApiGatewayUtils.badRequest("'content' must not exceed " + CONTENT_MAX_CHARS + " characters.");
        }

        Optional<Message> opt;
        try {
            opt = REPO.findByMessageId(messageId);
        } catch (Exception e) {
            context.getLogger().log("UpdateMessage lookup error [messageId=" + messageId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        // 403 rather than 404 to avoid leaking message existence to non-parties
        if (opt.isEmpty()) {
            return ApiGatewayUtils.forbidden("You do not have access to this message.");
        }

        Message message = opt.get();

        if (!caller.getUserId().equals(message.getSenderId())) {
            return ApiGatewayUtils.forbidden("Only the sender may edit this message.");
        }

        Instant sentAt;
        try {
            sentAt = Instant.parse(message.getSentAt());
        } catch (Exception e) {
            return ApiGatewayUtils.internalError();
        }

        if (Instant.now().getEpochSecond() - sentAt.getEpochSecond() > EDIT_WINDOW_SECONDS) {
            return ApiGatewayUtils.unprocessable("Messages can only be edited within 10 minutes of sending.");
        }

        String editedAt = Instant.now().toString();
        try {
            REPO.updateContent(message.getClientId(), message.getTherapistId(),
                    message.getSentAt(), messageId, content, editedAt);
        } catch (Exception e) {
            context.getLogger().log("UpdateMessage write error [messageId=" + messageId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        message.setContent(content);
        message.setEditedAt(editedAt);
        return ApiGatewayUtils.ok(message);
    }
}
