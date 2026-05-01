package com.therapy.repository;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class RelationshipRepository {

    private static final String TABLE = System.getenv("RELATIONSHIP_TABLE_NAME");
    private final DynamoDbClient ddb;

    public RelationshipRepository(DynamoDbClient ddb) {
        this.ddb = ddb;
    }

    /** Add a relationship type (MAPPING, APPOINTMENT, MESSAGE) to the StringSet. Idempotent via ADD. */
    public void addRelationshipType(String clientId, String therapistId, String type) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of(
                        "clientId", s(clientId),
                        "therapistId", s(therapistId)))
                .updateExpression("ADD relationshipTypes :type SET updatedAt = :now")
                .conditionExpression("attribute_exists(clientId) OR attribute_not_exists(clientId)")
                .expressionAttributeValues(Map.of(
                        ":type", AttributeValue.builder().ss(type).build(),
                        ":now", s(java.time.Instant.now().toString())))
                .build());
    }

    /** Upsert a relationship row with MAPPING type. Called when mapping is created. */
    public void upsertWithMapping(String clientId, String therapistId, String mappingId,
                                   String mappingStatus, String journalAccessStatus) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of(
                        "clientId", s(clientId),
                        "therapistId", s(therapistId)))
                .updateExpression(
                        "ADD relationshipTypes :type " +
                        "SET mappingId = :mid, mappingStatus = :ms, journalAccessStatus = :jas, " +
                        "updatedAt = :now, createdAt = if_not_exists(createdAt, :now)")
                .expressionAttributeValues(Map.of(
                        ":type", AttributeValue.builder().ss("MAPPING").build(),
                        ":mid", s(mappingId),
                        ":ms", s(mappingStatus),
                        ":jas", s(journalAccessStatus),
                        ":now", s(java.time.Instant.now().toString())))
                .build());
    }

    /** Remove MAPPING from types. If set becomes empty delete the item. */
    public void removeMappingType(String clientId, String therapistId) {
        try {
            ddb.updateItem(UpdateItemRequest.builder()
                    .tableName(TABLE)
                    .key(Map.of(
                            "clientId", s(clientId),
                            "therapistId", s(therapistId)))
                    .updateExpression("DELETE relationshipTypes :type REMOVE mappingId, mappingStatus, journalAccessStatus SET updatedAt = :now")
                    .expressionAttributeValues(Map.of(
                            ":type", AttributeValue.builder().ss("MAPPING").build(),
                            ":now", s(java.time.Instant.now().toString())))
                    .build());
        } catch (Exception ignored) {
            // item may not exist if no other relationship type remains
        }
    }

    /** Sync mappingStatus on the relationship row after a mapping status transition. */
    public void syncMappingStatus(String clientId, String therapistId, String mappingStatus) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("clientId", s(clientId), "therapistId", s(therapistId)))
                .updateExpression("SET mappingStatus = :ms, updatedAt = :now")
                .expressionAttributeValues(Map.of(
                        ":ms", s(mappingStatus),
                        ":now", s(java.time.Instant.now().toString())))
                .build());
    }

    /** Sync both mappingStatus and journalAccessStatus after an atomic approve+grant. */
    public void syncMappingAndJournalAccess(String clientId, String therapistId,
                                            String mappingStatus, String journalAccessStatus) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("clientId", s(clientId), "therapistId", s(therapistId)))
                .updateExpression("SET mappingStatus = :ms, journalAccessStatus = :jas, updatedAt = :now")
                .expressionAttributeValues(Map.of(
                        ":ms", s(mappingStatus),
                        ":jas", s(journalAccessStatus),
                        ":now", s(java.time.Instant.now().toString())))
                .build());
    }

    /** Sync journalAccessStatus on the relationship row after a journal access transition. */
    public void syncJournalAccessStatus(String clientId, String therapistId, String journalAccessStatus) {
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(TABLE)
                .key(Map.of("clientId", s(clientId), "therapistId", s(therapistId)))
                .updateExpression("SET journalAccessStatus = :jas, updatedAt = :now")
                .expressionAttributeValues(Map.of(
                        ":jas", s(journalAccessStatus),
                        ":now", s(java.time.Instant.now().toString())))
                .build());
    }

    private static AttributeValue s(String v) {
        return AttributeValue.builder().s(v).build();
    }
}
