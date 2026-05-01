package com.therapy.infrastructure.constructs;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.dynamodb.*;
import software.constructs.Construct;

import java.util.List;

/**
 * Creates all DynamoDB tables matching the schema in db_design/schema.txt.
 * Tables for the implemented APIs: SessionTable, MappingTable, MessageTable, RelationshipTable.
 */
public class DynamoDbTablesConstruct extends Construct {

    private final Table userTable;
    private final Table sessionTable;
    private final Table mappingTable;
    private final Table messageTable;
    private final Table relationshipTable;
    private final Table appointmentTable;

    public DynamoDbTablesConstruct(Construct scope, String id) {
        super(scope, id);

        // ── UserTable ─────────────────────────────────────────────────────────────
        // Single table for both CLIENT and THERAPIST. PK: userId (natural key, prefix-namespaced).
        userTable = Table.Builder.create(this, "UserTable")
                .tableName("TherapyUserTable")
                .partitionKey(Attribute.builder().name("userId").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // GSI_EmailIndex: email (HASH) + userType (RANGE)
        // Powers O(1) login lookup and email uniqueness checks. Projects passwordHash for login.
        userTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_EmailIndex")
                .partitionKey(Attribute.builder().name("email").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("userType").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.INCLUDE)
                .nonKeyAttributes(List.of(
                        "userId", "userType", "email", "passwordHash", "name",
                        "phoneNumber", "createdAt", "updatedAt",
                        "licenseNumber", "qualification", "bio", "specialization", "yearsOfExperience"))
                .build());

        // GSI_UserTypeIndex: userType (HASH) + yearsOfExperience (RANGE)
        // Powers therapist discovery with optional range filter on experience. Excludes passwordHash.
        userTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_UserTypeIndex")
                .partitionKey(Attribute.builder().name("userType").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("yearsOfExperience").type(AttributeType.NUMBER).build())
                .projectionType(ProjectionType.INCLUDE)
                .nonKeyAttributes(List.of(
                        "userId", "userType", "name", "email",
                        "phoneNumber", "createdAt", "updatedAt",
                        "licenseNumber", "qualification", "bio", "specialization"))
                .build());

        // ── SessionTable ─────────────────────────────────────────────────────────
        // Single item per session. PK: sessionId (natural key, no prefix, no SK).
        // privateNotes and sharedNotes live on the same item; INCLUDE projections
        // exclude note content from list GSIs to avoid unnecessary RCU cost.
        sessionTable = Table.Builder.create(this, "SessionTable")
                .tableName("TherapySessionTable")
                .partitionKey(Attribute.builder().name("sessionId").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // GSI_TherapistSessions: therapistId (HASH) + scheduledAt (RANGE)
        // INCLUDE projection: excludes privateNotes and sharedNotes from list reads.
        // confirmedAppointmentId and pendingCount included for UI booking state.
        sessionTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_TherapistSessions")
                .partitionKey(Attribute.builder().name("therapistId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("scheduledAt").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.INCLUDE)
                .nonKeyAttributes(List.of(
                        "sessionId", "therapistId", "title", "scheduledAt",
                        "durationMinutes", "isAvailable", "status",
                        "confirmedAppointmentId", "pendingCount",
                        "createdAt", "updatedAt"))
                .build());

        // GSI_StatusSchedule: status (HASH) + scheduledAt (RANGE)
        // Powers the client-facing session browser. Same INCLUDE projection rationale.
        sessionTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_StatusSchedule")
                .partitionKey(Attribute.builder().name("status").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("scheduledAt").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.INCLUDE)
                .nonKeyAttributes(List.of(
                        "sessionId", "therapistId", "title", "scheduledAt",
                        "durationMinutes", "isAvailable", "status",
                        "confirmedAppointmentId", "pendingCount",
                        "createdAt", "updatedAt"))
                .build());

        // ── MappingTable ─────────────────────────────────────────────────────────
        // PK: mappingId (single-entity table, no SK)
        mappingTable = Table.Builder.create(this, "MappingTable")
                .tableName("TherapyMappingTable")
                .partitionKey(Attribute.builder().name("mappingId").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // GSI_ClientMappings: clientId (HASH) + createdAt (RANGE)
        mappingTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_ClientMappings")
                .partitionKey(Attribute.builder().name("clientId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("createdAt").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        // GSI_TherapistMappings: therapistId (HASH) + createdAt (RANGE)
        mappingTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_TherapistMappings")
                .partitionKey(Attribute.builder().name("therapistId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("createdAt").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        // GSI_ClientTherapistLookup: clientId (HASH) + therapistId (RANGE)
        // KEYS_ONLY — used only for conflict detection (409 check), data never needed
        mappingTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_ClientTherapistLookup")
                .partitionKey(Attribute.builder().name("clientId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("therapistId").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.KEYS_ONLY)
                .build());

        // ── MessageTable ─────────────────────────────────────────────────────────
        // threadKey: THREAD#{clientId}#{therapistId} — composite, justified (no single natural key for a thread)
        // messageSk: sentAt#messageId — chronological order, no MSG# prefix (single entity type)
        messageTable = Table.Builder.create(this, "MessageTable")
                .tableName("TherapyMessageTable")
                .partitionKey(Attribute.builder().name("threadKey").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("messageSk").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // GSI_MessageById: messageId (HASH) + sentAt (RANGE)
        // O(1) single-message lookup for GET and DELETE by messageId
        messageTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_MessageById")
                .partitionKey(Attribute.builder().name("messageId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("sentAt").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        // ── RelationshipTable ────────────────────────────────────────────────────
        // PK: clientId, SK: therapistId — natural composite key for a client-therapist pair
        relationshipTable = Table.Builder.create(this, "RelationshipTable")
                .tableName("TherapyRelationshipTable")
                .partitionKey(Attribute.builder().name("clientId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("therapistId").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // GSI_TherapistRelationships: therapistId (HASH) + clientId (RANGE)
        relationshipTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_TherapistRelationships")
                .partitionKey(Attribute.builder().name("therapistId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("clientId").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        // ── AppointmentTable ─────────────────────────────────────────────────────
        // PK: appointmentId (single entity, no SK). Four GSIs cover all access patterns.
        appointmentTable = Table.Builder.create(this, "AppointmentTable")
                .tableName("TherapyAppointmentTable")
                .partitionKey(Attribute.builder().name("appointmentId").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // GSI_ClientAppointments: clientId (HASH) + requestedAt (RANGE)
        appointmentTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_ClientAppointments")
                .partitionKey(Attribute.builder().name("clientId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("requestedAt").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        // GSI_TherapistAppointments: therapistId (HASH) + requestedAt (RANGE)
        appointmentTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_TherapistAppointments")
                .partitionKey(Attribute.builder().name("therapistId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("requestedAt").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        // GSI_SessionAppointments: sessionId (HASH) + status (RANGE)
        // Groups appointments by session; status range enables pending-only queries for confirm flow.
        appointmentTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_SessionAppointments")
                .partitionKey(Attribute.builder().name("sessionId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("status").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        // GSI_ClientSessionDedup: clientSession (HASH) + status (RANGE)
        // KEYS_ONLY — used only for O(1) duplicate check in POST /appointments.
        appointmentTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI_ClientSessionDedup")
                .partitionKey(Attribute.builder().name("clientSession").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("status").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.KEYS_ONLY)
                .build());
    }

    public Table getUserTable()          { return userTable; }
    public Table getSessionTable()       { return sessionTable; }
    public Table getMappingTable()       { return mappingTable; }
    public Table getMessageTable()       { return messageTable; }
    public Table getRelationshipTable()  { return relationshipTable; }
    public Table getAppointmentTable()   { return appointmentTable; }
}
