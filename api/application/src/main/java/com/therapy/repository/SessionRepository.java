package com.therapy.repository;

import com.therapy.model.Session;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class SessionRepository {

    private static final String TABLE = System.getenv("SESSION_TABLE_NAME");
    private final DynamoDbClient ddb;

    public SessionRepository(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    private static Map<String, AttributeValue> key(String sessionId) {
        return Map.of("sessionId", s(sessionId));
    }

    public void create(Session session) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("sessionId", s(session.getSessionId()));
        item.put("therapistId", s(session.getTherapistId()));
        item.put("title", s(session.getTitle()));
        item.put("scheduledAt", s(session.getScheduledAt()));
        item.put("durationMinutes", n(session.getDurationMinutes()));
        item.put("status", s(session.getStatus()));
        item.put("isAvailable", bool(session.getIsAvailable()));
        item.put("pendingCount", n(0));
        item.put("createdAt", s(session.getCreatedAt()));
        item.put("updatedAt", s(session.getUpdatedAt()));
        if (session.getDescription() != null) item.put("description", s(session.getDescription()));

        ddb.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(item)
                .conditionExpression("attribute_not_exists(sessionId)")
                .build());
    }

    public Optional<Session> findById(String sessionId) {
        GetItemResponse resp = ddb.getItem(GetItemRequest.builder()
                .tableName(TABLE)
                .key(key(sessionId))
                .build());
        if (!resp.hasItem() || resp.item().isEmpty()) return Optional.empty();
        return Optional.of(itemToSession(resp.item()));
    }

    public List<Session> listByTherapist(String therapistId, String fromDate, String toDate,
                                         Boolean isAvailable, int limit, Map<String, AttributeValue> lastKey) {
        Map<String, AttributeValue> vals = new HashMap<>();
        vals.put(":tid", s(therapistId));

        QueryRequest.Builder qb = QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_TherapistSessions")
                .keyConditionExpression("therapistId = :tid")
                .expressionAttributeValues(vals)
                .limit(limit)
                .scanIndexForward(true);

        if (fromDate != null && toDate != null) {
            qb.keyConditionExpression("therapistId = :tid AND scheduledAt BETWEEN :from AND :to");
            vals.put(":from", s(fromDate));
            vals.put(":to", s(toDate));
        }
        if (isAvailable != null) {
            qb.filterExpression("isAvailable = :avail");
            vals.put(":avail", AttributeValue.builder().bool(isAvailable).build());
        }
        if (lastKey != null) qb.exclusiveStartKey(lastKey);

        QueryResponse resp = ddb.query(qb.build());
        List<Session> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) result.add(itemToSession(item));
        return result;
    }

    public List<Session> listForClient(String fromDate, String toDate, String therapistIdFilter,
                                       int limit, Map<String, AttributeValue> lastKey) {
        Map<String, AttributeValue> vals = new HashMap<>();
        vals.put(":status", s("SCHEDULED"));
        vals.put(":avail", AttributeValue.builder().bool(true).build());

        QueryRequest.Builder qb = QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_StatusSchedule")
                .keyConditionExpression("#st = :status")
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(vals)
                .limit(limit)
                .scanIndexForward(true);

        if (fromDate != null && toDate != null) {
            qb.keyConditionExpression("#st = :status AND scheduledAt BETWEEN :from AND :to");
            vals.put(":from", s(fromDate));
            vals.put(":to", s(toDate));
        }

        if (therapistIdFilter != null) {
            qb.filterExpression("isAvailable = :avail AND therapistId = :therapistId");
            vals.put(":therapistId", s(therapistIdFilter));
        } else {
            qb.filterExpression("isAvailable = :avail");
        }

        if (lastKey != null) qb.exclusiveStartKey(lastKey);

        QueryResponse resp = ddb.query(qb.build());
        List<Session> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) result.add(itemToSession(item));
        return result;
    }

    public void updateWithOwnerCheck(String sessionId, String therapistId, Map<String, Object> fields) {
        StringBuilder expr = new StringBuilder("SET updatedAt = :updatedAt");
        Map<String, AttributeValue> vals = new HashMap<>();
        vals.put(":updatedAt", s(now()));
        vals.put(":therapistId", s(therapistId));
        Map<String, String> names = new HashMap<>();

        for (Map.Entry<String, Object> e : fields.entrySet()) {
            String attr = e.getKey();
            Object val = e.getValue();
            String placeholder = ":" + attr;
            String namePlaceholder = "#" + attr;
            expr.append(", ").append(namePlaceholder).append(" = ").append(placeholder);
            names.put(namePlaceholder, attr);
            if (val instanceof Boolean) {
                vals.put(placeholder, AttributeValue.builder().bool((Boolean) val).build());
            } else if (val instanceof Integer) {
                vals.put(placeholder, n((Integer) val));
            } else {
                vals.put(placeholder, s(val.toString()));
            }
        }

        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(key(sessionId))
                .updateExpression(expr.toString())
                .expressionAttributeValues(vals)
                .expressionAttributeNames(names.isEmpty() ? null : names)
                .conditionExpression("therapistId = :therapistId")
                .build());
    }

    /** Marks the session as booked: sets isAvailable=false and confirmedAppointmentId. */
    public void markBooked(String sessionId, String appointmentId) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(key(sessionId))
                .updateExpression("SET isAvailable = :false, confirmedAppointmentId = :apptId, updatedAt = :now")
                .expressionAttributeValues(Map.of(
                        ":false",  AttributeValue.builder().bool(false).build(),
                        ":apptId", s(appointmentId),
                        ":now",    s(now())))
                .conditionExpression("attribute_exists(sessionId)")
                .build());
    }

    /** Cancels a confirmed session when therapist cancels the confirmed appointment. */
    public void markCancelledByTherapist(String sessionId) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(key(sessionId))
                .updateExpression("SET #st = :cancelled, isAvailable = :false, updatedAt = :now REMOVE confirmedAppointmentId")
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(Map.of(
                        ":cancelled", s("CANCELLED"),
                        ":false",     AttributeValue.builder().bool(false).build(),
                        ":now",       s(now())))
                .conditionExpression("attribute_exists(sessionId)")
                .build());
    }

    /** Re-opens a confirmed session when client cancels their confirmed appointment. */
    public void markScheduledAndAvailable(String sessionId) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(key(sessionId))
                .updateExpression("SET #st = :scheduled, isAvailable = :true, updatedAt = :now REMOVE confirmedAppointmentId")
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(Map.of(
                        ":scheduled", s("SCHEDULED"),
                        ":true",      AttributeValue.builder().bool(true).build(),
                        ":now",       s(now())))
                .conditionExpression("attribute_exists(sessionId)")
                .build());
    }

    /** Transitions session SCHEDULED → IN_PROGRESS on start. */
    public void markStarted(String sessionId, String startedAt) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(key(sessionId))
                .updateExpression("SET #st = :inprogress, startedAt = :startedAt, isAvailable = :false, updatedAt = :now")
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(Map.of(
                        ":inprogress", s("IN_PROGRESS"),
                        ":startedAt",  s(startedAt),
                        ":false",      AttributeValue.builder().bool(false).build(),
                        ":now",        s(now()),
                        ":scheduled",  s("SCHEDULED")))
                .conditionExpression("attribute_exists(sessionId) AND #st = :scheduled")
                .build());
    }

    /** Transitions session IN_PROGRESS → COMPLETED on end. */
    public void markCompleted(String sessionId, String endedAt) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(key(sessionId))
                .updateExpression("SET #st = :completed, endedAt = :endedAt, updatedAt = :now")
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(Map.of(
                        ":completed",  s("COMPLETED"),
                        ":endedAt",    s(endedAt),
                        ":now",        s(now()),
                        ":inprogress", s("IN_PROGRESS")))
                .conditionExpression("attribute_exists(sessionId) AND #st = :inprogress")
                .build());
    }

    /** Atomically increments pendingCount. */
    public void incrementPendingCount(String sessionId) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(key(sessionId))
                .updateExpression("ADD pendingCount :one")
                .expressionAttributeValues(Map.of(":one", n(1)))
                .conditionExpression("attribute_exists(sessionId)")
                .build());
    }

    /** Atomically decrements pendingCount (floor at 0). */
    public void decrementPendingCount(String sessionId) {
        try {
            ddb.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE)
                    .key(key(sessionId))
                    .updateExpression("ADD pendingCount :neg")
                    .expressionAttributeValues(Map.of(
                            ":neg",  n(-1),
                            ":zero", n(0)))
                    .conditionExpression("attribute_exists(sessionId) AND pendingCount > :zero")
                    .build());
        } catch (ConditionalCheckFailedException ignored) {
            // already at 0 — no-op
        }
    }

    public void delete(String sessionId) {
        ddb.deleteItem(DeleteItemRequest.builder()
                .tableName(TABLE)
                .key(key(sessionId))
                .conditionExpression("attribute_exists(sessionId)")
                .build());
    }

    private Session itemToSession(Map<String, AttributeValue> item) {
        Session s = new Session();
        s.setSessionId(str(item, "sessionId"));
        s.setTherapistId(str(item, "therapistId"));
        s.setTitle(str(item, "title"));
        s.setDescription(str(item, "description"));
        s.setScheduledAt(str(item, "scheduledAt"));
        if (item.containsKey("durationMinutes"))
            s.setDurationMinutes(Integer.parseInt(item.get("durationMinutes").n()));
        if (item.containsKey("isAvailable"))
            s.setIsAvailable(item.get("isAvailable").bool());
        s.setStatus(str(item, "status"));
        s.setStartedAt(str(item, "startedAt"));
        s.setEndedAt(str(item, "endedAt"));
        s.setConfirmedAppointmentId(str(item, "confirmedAppointmentId"));
        if (item.containsKey("pendingCount"))
            s.setPendingCount(Integer.parseInt(item.get("pendingCount").n()));
        s.setPrivateNotes(str(item, "privateNotes"));
        s.setSharedNotes(str(item, "sharedNotes"));
        s.setCreatedAt(str(item, "createdAt"));
        s.setUpdatedAt(str(item, "updatedAt"));
        return s;
    }

    private static AttributeValue s(String v) {
        return AttributeValue.builder().s(v).build();
    }

    private static AttributeValue n(int v) {
        return AttributeValue.builder().n(String.valueOf(v)).build();
    }

    private static AttributeValue bool(boolean v) {
        return AttributeValue.builder().bool(v).build();
    }

    private static String str(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return (v != null && v.s() != null) ? v.s() : null;
    }

    private static String now() {
        return java.time.Instant.now().toString();
    }
}
