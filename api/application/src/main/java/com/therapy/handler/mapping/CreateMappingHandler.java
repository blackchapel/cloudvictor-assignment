package com.therapy.handler.mapping;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.therapy.handler.BaseHandler;
import com.therapy.model.CallerContext;
import com.therapy.model.Mapping;
import com.therapy.repository.MappingRepository;
import com.therapy.repository.RelationshipRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.IdGenerator;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;

public class CreateMappingHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final MappingRepository MAPPING_REPO = new MappingRepository(DDB);
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

        if (isBlank(clientId))    return ApiGatewayUtils.badRequest("'clientId' is required.");
        if (isBlank(therapistId)) return ApiGatewayUtils.badRequest("'therapistId' is required.");
        if (clientId.equals(therapistId)) {
            return ApiGatewayUtils.badRequest("A user cannot create a mapping with themselves.");
        }

        // Caller identity must match their role — prevents impersonation
        if (caller.isClient() && !caller.getUserId().equals(clientId)) {
            return ApiGatewayUtils.forbidden("Clients may only create mappings using their own clientId.");
        }
        if (caller.isTherapist() && !caller.getUserId().equals(therapistId)) {
            return ApiGatewayUtils.forbidden("Therapists may only create mappings using their own therapistId.");
        }

        boolean journalAccess = body.hasNonNull("journalAccess") && body.get("journalAccess").asBoolean(false);

        // Conflict check — reject if any mapping already exists between this pair
        try {
            if (MAPPING_REPO.existsByClientAndTherapist(clientId, therapistId)) {
                return ApiGatewayUtils.conflict("A mapping between this client and therapist already exists.");
            }
        } catch (Exception e) {
            context.getLogger().log("CreateMapping conflict-check error: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        // journalAccessStatus depends on who initiates (per OpenAPI spec table)
        String journalAccessStatus;
        if (!journalAccess) {
            journalAccessStatus = "NONE";
        } else if (caller.isClient()) {
            journalAccessStatus = "GRANTED";    // client grants immediately
        } else {
            journalAccessStatus = "REQUESTED";  // therapist must still await client approval
        }

        String now = Instant.now().toString();
        Mapping mapping = new Mapping();
        mapping.setMappingId(IdGenerator.mappingId());
        mapping.setClientId(clientId);
        mapping.setTherapistId(therapistId);
        mapping.setMappingStatus("PENDING");
        mapping.setJournalAccessStatus(journalAccessStatus);
        mapping.setInitiatedBy(caller.getUserType());
        mapping.setCreatedAt(now);
        mapping.setUpdatedAt(now);

        try {
            MAPPING_REPO.create(mapping);
            REL_REPO.upsertWithMapping(clientId, therapistId,
                    mapping.getMappingId(), "PENDING", journalAccessStatus);
        } catch (Exception e) {
            context.getLogger().log("CreateMapping write error: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        return ApiGatewayUtils.created(mapping);
    }
}
