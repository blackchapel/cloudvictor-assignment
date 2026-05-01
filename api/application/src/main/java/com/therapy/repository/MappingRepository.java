package com.therapy.repository;

import com.therapy.model.Mapping;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class MappingRepository {

    private static final String TABLE = System.getenv("MAPPING_TABLE_NAME");
    private final DynamoDbClient ddb;

    public MappingRepository(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    public void create(Mapping mapping) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("mappingId", s(mapping.getMappingId()));
        item.put("clientId", s(mapping.getClientId()));
        item.put("therapistId", s(mapping.getTherapistId()));
        item.put("mappingStatus", s(mapping.getMappingStatus()));
        item.put("journalAccessStatus", s(mapping.getJournalAccessStatus()));
        item.put("initiatedBy", s(mapping.getInitiatedBy()));
        item.put("createdAt", s(mapping.getCreatedAt()));
        item.put("updatedAt", s(mapping.getUpdatedAt()));

        ddb.putItem(PutItemRequest.builder()
                .tableName(TABLE)
                .item(item)
                .conditionExpression("attribute_not_exists(mappingId)")
                .build());
    }

    public Optional<Mapping> findById(String mappingId) {
        GetItemResponse resp = ddb.getItem(GetItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("mappingId", s(mappingId)))
                .build());
        if (!resp.hasItem() || resp.item().isEmpty()) return Optional.empty();
        return Optional.of(itemToMapping(resp.item()));
    }

    /** Returns true if an APPROVED mapping exists between the client and therapist. */
    public boolean hasApprovedMapping(String clientId, String therapistId) {
        QueryResponse resp = ddb.query(QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_ClientMappings")
                .keyConditionExpression("clientId = :cid")
                .filterExpression("therapistId = :tid AND mappingStatus = :approved")
                .expressionAttributeValues(Map.of(
                        ":cid",      s(clientId),
                        ":tid",      s(therapistId),
                        ":approved", s("APPROVED")))
                .limit(1)
                .build());
        return resp.count() > 0;
    }

    /** Check for any existing mapping between clientId and therapistId. */
    public boolean existsByClientAndTherapist(String clientId, String therapistId) {
        QueryResponse resp = ddb.query(QueryRequest.builder()
                .tableName(TABLE)
                .indexName("GSI_ClientTherapistLookup")
                .keyConditionExpression("clientId = :cid AND therapistId = :tid")
                .expressionAttributeValues(Map.of(
                        ":cid", s(clientId),
                        ":tid", s(therapistId)))
                .limit(1)
                .build());
        return resp.count() > 0;
    }

    public List<Mapping> listByClient(String clientId, String statusFilter, String jasFilter, int limit, Map<String, AttributeValue> lastKey) {
        return listByIndex("GSI_ClientMappings", "clientId", clientId, statusFilter, jasFilter, limit, lastKey);
    }

    public List<Mapping> listByTherapist(String therapistId, String statusFilter, String jasFilter, int limit, Map<String, AttributeValue> lastKey) {
        return listByIndex("GSI_TherapistMappings", "therapistId", therapistId, statusFilter, jasFilter, limit, lastKey);
    }

    private List<Mapping> listByIndex(String index, String pkAttr, String pkVal,
                                      String statusFilter, String jasFilter, int limit,
                                      Map<String, AttributeValue> lastKey) {
        Map<String, AttributeValue> vals = new HashMap<>();
        vals.put(":pk", s(pkVal));

        StringBuilder filter = new StringBuilder();
        if (statusFilter != null) {
            filter.append("mappingStatus = :ms");
            vals.put(":ms", s(statusFilter));
        }
        if (jasFilter != null) {
            if (filter.length() > 0) filter.append(" AND ");
            filter.append("journalAccessStatus = :jas");
            vals.put(":jas", s(jasFilter));
        }

        QueryRequest.Builder qb = QueryRequest.builder()
                .tableName(TABLE)
                .indexName(index)
                .keyConditionExpression(pkAttr + " = :pk")
                .expressionAttributeValues(vals)
                .limit(limit)
                .scanIndexForward(false);

        if (filter.length() > 0) qb.filterExpression(filter.toString());
        if (lastKey != null) qb.exclusiveStartKey(lastKey);

        QueryResponse resp = ddb.query(qb.build());
        List<Mapping> result = new ArrayList<>();
        for (Map<String, AttributeValue> item : resp.items()) result.add(itemToMapping(item));
        return result;
    }

    public void updateMappingStatus(String mappingId, String newStatus) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("mappingId", s(mappingId)))
                .updateExpression("SET mappingStatus = :ms, updatedAt = :now")
                .expressionAttributeValues(Map.of(
                        ":ms", s(newStatus),
                        ":now", s(now()),
                        ":pending", s("PENDING")))
                .conditionExpression("mappingStatus = :pending")
                .build());
    }

    public void updateJournalAccess(String mappingId, String newJas) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("mappingId", s(mappingId)))
                .updateExpression("SET journalAccessStatus = :jas, updatedAt = :now")
                .expressionAttributeValues(Map.of(
                        ":jas", s(newJas),
                        ":now", s(now())))
                .build());
    }

    /** Auto-approve: atomically set journalAccessStatus=GRANTED and mappingStatus=APPROVED.
     *  Condition guards against a concurrent REJECT racing between the handler read and this write. */
    public void grantJournalAccessAndApproveMappingAtomically(String mappingId) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("mappingId", s(mappingId)))
                .updateExpression("SET journalAccessStatus = :jas, mappingStatus = :ms, updatedAt = :now")
                .conditionExpression("mappingStatus <> :rejected")
                .expressionAttributeValues(Map.of(
                        ":jas", s("GRANTED"),
                        ":ms", s("APPROVED"),
                        ":now", s(now()),
                        ":rejected", s("REJECTED")))
                .build());
    }

    public void delete(String mappingId) {
        ddb.deleteItem(DeleteItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("mappingId", s(mappingId)))
                .conditionExpression("attribute_exists(mappingId)")
                .build());
    }

    private Mapping itemToMapping(Map<String, AttributeValue> item) {
        Mapping m = new Mapping();
        m.setMappingId(str(item, "mappingId"));
        m.setClientId(str(item, "clientId"));
        m.setTherapistId(str(item, "therapistId"));
        m.setMappingStatus(str(item, "mappingStatus"));
        m.setJournalAccessStatus(str(item, "journalAccessStatus"));
        m.setInitiatedBy(str(item, "initiatedBy"));
        m.setCreatedAt(str(item, "createdAt"));
        m.setUpdatedAt(str(item, "updatedAt"));
        return m;
    }

    private static AttributeValue s(String v) {
        return AttributeValue.builder().s(v).build();
    }

    private static String str(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return (v != null && v.s() != null) ? v.s() : null;
    }

    private static String now() {
        return java.time.Instant.now().toString();
    }
}
