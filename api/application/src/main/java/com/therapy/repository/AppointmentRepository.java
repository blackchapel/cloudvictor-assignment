package com.therapy.repository;

import com.therapy.model.Appointment;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class AppointmentRepository {

    private static final String TABLE = System.getenv("APPOINTMENT_TABLE_NAME");
    private final DynamoDbClient ddb;

    public AppointmentRepository(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    public void create(Appointment appt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("appointmentId", s(appt.getAppointmentId()));
        item.put("sessionId",     s(appt.getSessionId()));
        item.put("clientId",      s(appt.getClientId()));
        item.put("therapistId",   s(appt.getTherapistId()));
        item.put("status",        s(appt.getStatus()));
        item.put("clientSession", s(appt.getClientSession()));
        item.put("requestedAt",   s(appt.getRequestedAt()));
        item.put("updatedAt",     s(appt.getUpdatedAt()));

        ddb.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(item)
                .conditionExpression("attribute_not_exists(appointmentId)")
                .build());
    }

    public Optional<Appointment> findById(String appointmentId) {
        GetItemResponse resp = ddb.getItem(GetItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("appointmentId", s(appointmentId)))
                .build());
        if (!resp.hasItem() || resp.item().isEmpty()) return Optional.empty();
        return Optional.of(itemToAppointment(resp.item()));
    }

    public List<Appointment> listByClient(String clientId, String sessionIdFilter,
                                          String statusFilter, int limit,
                                          Map<String, AttributeValue> lastKey) {
        Map<String, AttributeValue> vals = new HashMap<>();
        vals.put(":cid", s(clientId));

        QueryRequest.Builder qb = QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_ClientAppointments")
                .keyConditionExpression("clientId = :cid")
                .expressionAttributeValues(vals)
                .limit(limit)
                .scanIndexForward(false);

        List<String> filters = new ArrayList<>();
        if (sessionIdFilter != null) {
            filters.add("sessionId = :sid");
            vals.put(":sid", s(sessionIdFilter));
        }
        if (statusFilter != null) {
            filters.add("#st = :status");
            vals.put(":status", s(statusFilter));
            qb.expressionAttributeNames(Map.of("#st", "status"));
        }
        if (!filters.isEmpty()) qb.filterExpression(String.join(" AND ", filters));
        if (lastKey != null) qb.exclusiveStartKey(lastKey);

        QueryResponse resp = ddb.query(qb.build());
        List<Appointment> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) result.add(itemToAppointment(item));
        return result;
    }

    public List<Appointment> listByTherapist(String therapistId, String sessionIdFilter,
                                             String statusFilter, int limit,
                                             Map<String, AttributeValue> lastKey) {
        Map<String, AttributeValue> vals = new HashMap<>();
        vals.put(":tid", s(therapistId));

        QueryRequest.Builder qb = QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_TherapistAppointments")
                .keyConditionExpression("therapistId = :tid")
                .expressionAttributeValues(vals)
                .limit(limit)
                .scanIndexForward(false);

        List<String> filters = new ArrayList<>();
        Map<String, String> names = new HashMap<>();
        if (sessionIdFilter != null) {
            filters.add("sessionId = :sid");
            vals.put(":sid", s(sessionIdFilter));
        }
        if (statusFilter != null) {
            filters.add("#st = :status");
            vals.put(":status", s(statusFilter));
            names.put("#st", "status");
        }
        if (!filters.isEmpty()) qb.filterExpression(String.join(" AND ", filters));
        if (!names.isEmpty()) qb.expressionAttributeNames(names);
        if (lastKey != null) qb.exclusiveStartKey(lastKey);

        QueryResponse resp = ddb.query(qb.build());
        List<Appointment> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) result.add(itemToAppointment(item));
        return result;
    }

    public void updateStatus(String appointmentId, String newStatus, String updatedAt) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("appointmentId", s(appointmentId)))
                .updateExpression("SET #st = :status, updatedAt = :updatedAt")
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(Map.of(
                        ":status",    s(newStatus),
                        ":updatedAt", s(updatedAt)))
                .conditionExpression("attribute_exists(appointmentId)")
                .build());
    }

    public void delete(String appointmentId) {
        ddb.deleteItem(DeleteItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("appointmentId", s(appointmentId)))
                .conditionExpression("attribute_exists(appointmentId)")
                .build());
    }

    /**
     * Returns true if the client has any non-cancelled appointment for the given session.
     * Used by GetSessionHandler to decide if a client may view a fully-booked session.
     */
    public boolean hasActiveAppointment(String clientId, String sessionId) {
        QueryResponse resp = ddb.query(QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_ClientSessionDedup")
                .keyConditionExpression("clientSession = :cs")
                .filterExpression("#st <> :cancelled")
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(Map.of(
                        ":cs",        s(clientId + "#" + sessionId),
                        ":cancelled", s("CANCELLED")))
                .limit(1)
                .build());
        return resp.count() > 0;
    }

    /**
     * Returns true if any appointment exists for this client+session (any status).
     * Used by CreateAppointmentHandler for 409 duplicate check.
     */
    public boolean existsByClientAndSession(String clientId, String sessionId) {
        QueryResponse resp = ddb.query(QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_ClientSessionDedup")
                .keyConditionExpression("clientSession = :cs")
                .expressionAttributeValues(Map.of(":cs", s(clientId + "#" + sessionId)))
                .limit(1)
                .build());
        return resp.count() > 0;
    }

    /**
     * Lists all appointments for a session (used by therapist list view).
     * GSI_SessionAppointments: sessionId (HASH), status (RANGE).
     */
    public List<Appointment> listBySession(String sessionId, int limit,
                                           Map<String, AttributeValue> lastKey) {
        QueryRequest.Builder qb = QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_SessionAppointments")
                .keyConditionExpression("sessionId = :sid")
                .expressionAttributeValues(Map.of(":sid", s(sessionId)))
                .limit(limit)
                .scanIndexForward(true);
        if (lastKey != null) qb.exclusiveStartKey(lastKey);

        QueryResponse resp = ddb.query(qb.build());
        List<Appointment> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) result.add(itemToAppointment(item));
        return result;
    }

    /**
     * Lists all PENDING appointments for a session.
     * Used by PatchAppointmentHandler to batch-reject when confirming one.
     */
    public List<Appointment> listPendingBySession(String sessionId) {
        QueryResponse resp = ddb.query(QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_SessionAppointments")
                .keyConditionExpression("sessionId = :sid AND #st = :pending")
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(Map.of(
                        ":sid",     s(sessionId),
                        ":pending", s("PENDING")))
                .build());
        List<Appointment> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) result.add(itemToAppointment(item));
        return result;
    }

    private Appointment itemToAppointment(Map<String, AttributeValue> item) {
        Appointment a = new Appointment();
        a.setAppointmentId(str(item, "appointmentId"));
        a.setSessionId(str(item, "sessionId"));
        a.setClientId(str(item, "clientId"));
        a.setTherapistId(str(item, "therapistId"));
        a.setStatus(str(item, "status"));
        a.setClientSession(str(item, "clientSession"));
        a.setRequestedAt(str(item, "requestedAt"));
        a.setUpdatedAt(str(item, "updatedAt"));
        return a;
    }

    private static AttributeValue s(String v) {
        return AttributeValue.builder().s(v).build();
    }

    private static String str(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return (v != null && v.s() != null) ? v.s() : null;
    }
}
