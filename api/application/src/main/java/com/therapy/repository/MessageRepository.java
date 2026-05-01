package com.therapy.repository;

import com.therapy.model.Message;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class MessageRepository {

    private static final String TABLE = System.getenv("MESSAGE_TABLE_NAME");
    private final DynamoDbClient ddb;

    public MessageRepository(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    private static String threadKey(String clientId, String therapistId) {
        return "THREAD#" + clientId + "#" + therapistId;
    }

    private static String messageSk(String sentAt, String messageId) {
        return sentAt + "#" + messageId;
    }

    public void save(Message message) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("threadKey", s(threadKey(message.getClientId(), message.getTherapistId())));
        item.put("messageSk", s(messageSk(message.getSentAt(), message.getMessageId())));
        item.put("messageId", s(message.getMessageId()));
        item.put("clientId", s(message.getClientId()));
        item.put("therapistId", s(message.getTherapistId()));
        item.put("senderType", s(message.getSenderType()));
        item.put("senderId", s(message.getSenderId()));
        item.put("content", s(message.getContent()));
        item.put("sentAt", s(message.getSentAt()));

        ddb.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(item)
                .build());
    }

    public List<Message> listThread(String clientId, String therapistId, int limit,
                                    Map<String, AttributeValue> lastKey) {
        QueryRequest.Builder qb = QueryRequest.builder()
                .tableName(TABLE)
                .keyConditionExpression("threadKey = :tk")
                .expressionAttributeValues(Map.of(":tk", s(threadKey(clientId, therapistId))))
                .limit(limit)
                .scanIndexForward(true);

        if (lastKey != null) qb.exclusiveStartKey(lastKey);

        QueryResponse resp = ddb.query(qb.build());
        List<Message> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) result.add(itemToMessage(item));
        return result;
    }

    public Optional<Message> findByMessageId(String messageId) {
        QueryResponse resp = ddb.query(QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_MessageById")
                .keyConditionExpression("messageId = :mid")
                .expressionAttributeValues(Map.of(":mid", s(messageId)))
                .limit(1)
                .build());
        if (resp.count() == 0) return Optional.empty();
        return Optional.of(itemToMessage(resp.items().get(0)));
    }

    public void updateContent(String clientId, String therapistId,
                              String sentAt, String messageId,
                              String content, String editedAt) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of(
                        "threadKey", s(threadKey(clientId, therapistId)),
                        "messageSk", s(messageSk(sentAt, messageId))))
                .updateExpression("SET content = :c, editedAt = :ea")
                .conditionExpression("attribute_exists(threadKey)")
                .expressionAttributeValues(Map.of(
                        ":c", s(content),
                        ":ea", s(editedAt)))
                .build());
    }

    public void delete(String clientId, String therapistId, String sentAt, String messageId) {
        ddb.deleteItem(DeleteItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of(
                        "threadKey", s(threadKey(clientId, therapistId)),
                        "messageSk", s(messageSk(sentAt, messageId))))
                .conditionExpression("attribute_exists(threadKey)")
                .build());
    }

    private Message itemToMessage(Map<String, AttributeValue> item) {
        Message m = new Message();
        m.setMessageId(str(item, "messageId"));
        m.setClientId(str(item, "clientId"));
        m.setTherapistId(str(item, "therapistId"));
        m.setSenderType(str(item, "senderType"));
        m.setSenderId(str(item, "senderId"));
        m.setContent(str(item, "content"));
        m.setSentAt(str(item, "sentAt"));
        m.setEditedAt(str(item, "editedAt"));
        return m;
    }

    private static AttributeValue s(String v) {
        return AttributeValue.builder().s(v).build();
    }

    private static String str(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return (v != null && v.s() != null) ? v.s() : null;
    }
}
