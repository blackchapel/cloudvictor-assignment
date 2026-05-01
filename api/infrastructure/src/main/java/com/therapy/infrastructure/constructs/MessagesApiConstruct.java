package com.therapy.infrastructure.constructs;

import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Function;
import software.constructs.Construct;

/**
 * Creates the four Message Lambda functions and grants DynamoDB permissions.
 * Route wiring is handled in TherapyApiStack via OpenApiSpecProcessor.
 *
 *   POST   /messages              → sendMessage
 *   GET    /messages              → listMessages
 *   PUT    /messages/{messageId}  → updateMessage
 *   DELETE /messages/{messageId}  → deleteMessage
 */
public class MessagesApiConstruct extends Construct {

    private final Function sendMessage;
    private final Function listMessages;
    private final Function updateMessage;
    private final Function deleteMessage;

    public MessagesApiConstruct(Construct scope, String id,
                                LambdaFactory factory,
                                Table messageTable,
                                Table relationshipTable) {
        super(scope, id);

        sendMessage   = factory.create("SendMessageFunction",
                "com.therapy.handler.message.SendMessageHandler");
        listMessages  = factory.create("ListMessagesFunction",
                "com.therapy.handler.message.ListMessagesHandler");
        updateMessage = factory.create("UpdateMessageFunction",
                "com.therapy.handler.message.UpdateMessageHandler");
        deleteMessage = factory.create("DeleteMessageFunction",
                "com.therapy.handler.message.DeleteMessageHandler");

        messageTable.grantReadWriteData(sendMessage);
        messageTable.grantReadData(listMessages);
        messageTable.grantReadWriteData(updateMessage);
        messageTable.grantReadWriteData(deleteMessage);

        relationshipTable.grantReadWriteData(sendMessage);
    }

    public Function getSendMessage()   { return sendMessage; }
    public Function getListMessages()  { return listMessages; }
    public Function getUpdateMessage() { return updateMessage; }
    public Function getDeleteMessage() { return deleteMessage; }
}
