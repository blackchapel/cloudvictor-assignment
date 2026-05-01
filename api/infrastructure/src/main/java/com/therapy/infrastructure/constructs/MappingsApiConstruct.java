package com.therapy.infrastructure.constructs;

import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Function;
import software.constructs.Construct;

/**
 * Creates the six Mapping Lambda functions and grants DynamoDB permissions.
 * Route wiring is handled in TherapyApiStack via OpenApiSpecProcessor.
 *
 *   POST   /mappings                              → createMapping
 *   GET    /mappings                              → listMappings
 *   GET    /mappings/{mappingId}                  → getMapping
 *   DELETE /mappings/{mappingId}                  → deleteMapping
 *   PATCH  /mappings/{mappingId}/mapping-status   → updateMappingStatus
 *   PATCH  /mappings/{mappingId}/journal-access   → updateJournalAccess
 */
public class MappingsApiConstruct extends Construct {

    private final Function createMapping;
    private final Function listMappings;
    private final Function getMapping;
    private final Function deleteMapping;
    private final Function updateMappingStatus;
    private final Function updateJournalAccess;

    public MappingsApiConstruct(Construct scope, String id,
                                LambdaFactory factory,
                                Table mappingTable,
                                Table relationshipTable) {
        super(scope, id);

        createMapping       = factory.create("CreateMappingFunction",
                "com.therapy.handler.mapping.CreateMappingHandler");
        listMappings        = factory.create("ListMappingsFunction",
                "com.therapy.handler.mapping.ListMappingsHandler");
        getMapping          = factory.create("GetMappingFunction",
                "com.therapy.handler.mapping.GetMappingHandler");
        deleteMapping       = factory.create("DeleteMappingFunction",
                "com.therapy.handler.mapping.DeleteMappingHandler");
        updateMappingStatus = factory.create("UpdateMappingStatusFunction",
                "com.therapy.handler.mapping.UpdateMappingStatusHandler");
        updateJournalAccess = factory.create("UpdateJournalAccessFunction",
                "com.therapy.handler.mapping.UpdateJournalAccessHandler");

        mappingTable.grantReadWriteData(createMapping);
        mappingTable.grantReadData(listMappings);
        mappingTable.grantReadData(getMapping);
        mappingTable.grantReadWriteData(deleteMapping);
        mappingTable.grantReadWriteData(updateMappingStatus);
        mappingTable.grantReadWriteData(updateJournalAccess);

        // updateMappingStatus and updateJournalAccess sync denormalised state
        // to RelationshipTable; without this grant they receive AccessDenied.
        relationshipTable.grantReadWriteData(createMapping);
        relationshipTable.grantReadWriteData(deleteMapping);
        relationshipTable.grantReadWriteData(updateMappingStatus);
        relationshipTable.grantReadWriteData(updateJournalAccess);
    }

    public Function getCreateMapping()       { return createMapping; }
    public Function getListMappings()        { return listMappings; }
    public Function getGetMapping()          { return getMapping; }
    public Function getDeleteMapping()       { return deleteMapping; }
    public Function getUpdateMappingStatus() { return updateMappingStatus; }
    public Function getUpdateJournalAccess() { return updateJournalAccess; }
}
