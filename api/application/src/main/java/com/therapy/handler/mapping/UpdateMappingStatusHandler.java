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
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Optional;
import java.util.Set;

/**
 * PATCH /mappings/{mappingId}/mapping-status
 *
 * Only the non-initiating party may approve or reject a PENDING mapping.
 * Allowed transitions:  PENDING → APPROVED  |  PENDING → REJECTED
 */
public class UpdateMappingStatusHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final MappingRepository REPO = new MappingRepository(DDB);
    private static final RelationshipRepository REL_REPO = new RelationshipRepository(DDB);

    private static final Set<String> ALLOWED_TARGETS = Set.of("APPROVED", "REJECTED");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        String mappingId = ApiGatewayUtils.getPathParam(event, "mappingId");
        if (isBlank(mappingId)) {
            return ApiGatewayUtils.badRequest("'mappingId' path parameter is required.");
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

        String newStatus = body.hasNonNull("mappingStatus") ? body.get("mappingStatus").asText("").trim() : null;
        if (isBlank(newStatus)) {
            return ApiGatewayUtils.badRequest("'mappingStatus' is required.");
        }
        if (!ALLOWED_TARGETS.contains(newStatus)) {
            return ApiGatewayUtils.unprocessable("Invalid transition. Only APPROVED or REJECTED are allowed.");
        }

        Optional<Mapping> opt;
        try {
            opt = REPO.findById(mappingId);
        } catch (Exception e) {
            context.getLogger().log("UpdateMappingStatus lookup error [mappingId=" + mappingId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        if (opt.isEmpty()) {
            return ApiGatewayUtils.notFound("Mapping not found.");
        }

        Mapping mapping = opt.get();
        boolean isParty = caller.getUserId().equals(mapping.getClientId())
                || caller.getUserId().equals(mapping.getTherapistId());
        if (!isParty) {
            return ApiGatewayUtils.forbidden("You are not a party to this mapping.");
        }

        // Only the non-initiating party may approve or reject
        if (caller.getUserType().equals(mapping.getInitiatedBy())) {
            return ApiGatewayUtils.forbidden("Only the non-initiating party may approve or reject a mapping.");
        }

        if (!"PENDING".equals(mapping.getMappingStatus())) {
            return ApiGatewayUtils.unprocessable(
                    "Only PENDING mappings can be approved or rejected. Current status: " + mapping.getMappingStatus() + ".");
        }

        try {
            REPO.updateMappingStatus(mappingId, newStatus);
            REL_REPO.syncMappingStatus(mapping.getClientId(), mapping.getTherapistId(), newStatus);
        } catch (ConditionalCheckFailedException e) {
            return ApiGatewayUtils.unprocessable("Mapping is no longer in PENDING state.");
        } catch (Exception e) {
            context.getLogger().log("UpdateMappingStatus write error [mappingId=" + mappingId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        Optional<Mapping> updated = REPO.findById(mappingId);
        if (updated.isEmpty()) return ApiGatewayUtils.internalError();
        return ApiGatewayUtils.ok(updated.get());
    }
}
