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

import java.util.Optional;

/**
 * PATCH /mappings/{mappingId}/journal-access
 *
 * Full state machine per OpenAPI spec:
 *
 *   Therapist (mapping must be PENDING or APPROVED):
 *     NONE      → REQUESTED
 *     REVOKED   → REQUESTED
 *
 *   Client (mapping may be PENDING or APPROVED):
 *     REQUESTED → GRANTED   (auto-approves mapping if still PENDING)
 *     NONE      → GRANTED   (auto-approves mapping if still PENDING)
 *     REQUESTED → REVOKED
 *     GRANTED   → REVOKED
 */
public class UpdateJournalAccessHandler  extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final MappingRepository REPO = new MappingRepository(DDB);
    private static final RelationshipRepository REL_REPO = new RelationshipRepository(DDB);

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

        String newJas = body.hasNonNull("journalAccessStatus")
                ? body.get("journalAccessStatus").asText("").trim() : null;
        if (isBlank(newJas)) {
            return ApiGatewayUtils.badRequest("'journalAccessStatus' is required.");
        }

        Optional<Mapping> opt;
        try {
            opt = REPO.findById(mappingId);
        } catch (Exception e) {
            context.getLogger().log("UpdateJournalAccess lookup error [mappingId=" + mappingId + "]: " + e.getMessage());
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

        String currentJas     = mapping.getJournalAccessStatus();
        String mappingStatus  = mapping.getMappingStatus();

        APIGatewayProxyResponseEvent result;
        if (caller.isTherapist()) {
            result = applyTherapistTransition(mappingId, newJas, currentJas, mappingStatus, mapping.getInitiatedBy(), context);
        } else {
            result = applyClientTransition(mappingId, newJas, currentJas, mappingStatus, context);
        }

        if (result != null) return result;

        // Return updated mapping
        Optional<Mapping> updated = REPO.findById(mappingId);
        if (updated.isEmpty()) return ApiGatewayUtils.internalError();

        // Sync denormalized state to RelationshipTable
        try {
            REL_REPO.syncMappingAndJournalAccess(
                    mapping.getClientId(), mapping.getTherapistId(),
                    updated.get().getMappingStatus(), updated.get().getJournalAccessStatus());
        } catch (Exception e) {
            context.getLogger().log("UpdateJournalAccess rel-sync error [mappingId=" + mappingId + "]: " + e.getMessage());
        }

        return ApiGatewayUtils.ok(updated.get());
    }

    /**
     * Therapist allowed transitions:
     *   - If therapist initiated the mapping: PENDING or APPROVED allowed.
     *   - If client initiated the mapping: therapist must approve the mapping first (APPROVED only).
     *   - REJECTED is always blocked.
     * Returns a non-null error response if the transition is invalid, null on success.
     */
    private APIGatewayProxyResponseEvent applyTherapistTransition(
            String mappingId, String newJas, String currentJas, String mappingStatus, String initiatedBy, Context context) {

        if ("REJECTED".equals(mappingStatus)) {
            return ApiGatewayUtils.unprocessable("Cannot request journal access on a REJECTED mapping.");
        }
        if ("PENDING".equals(mappingStatus) && "CLIENT".equals(initiatedBy)) {
            return ApiGatewayUtils.unprocessable(
                    "You must approve the client's mapping request before requesting journal access.");
        }
        if (!"REQUESTED".equals(newJas)) {
            return ApiGatewayUtils.forbidden("Therapists may only set journalAccessStatus to REQUESTED.");
        }
        if (!"NONE".equals(currentJas) && !"REVOKED".equals(currentJas)) {
            return ApiGatewayUtils.unprocessable(
                    "Cannot transition from " + currentJas + " to REQUESTED.");
        }

        try {
            REPO.updateJournalAccess(mappingId, "REQUESTED");
        } catch (Exception e) {
            context.getLogger().log("UpdateJournalAccess (therapist) write error [mappingId=" + mappingId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }
        return null;
    }

    /**
     * Client allowed transitions — mapping may be PENDING or APPROVED.
     * GRANTED also auto-approves the mapping if it is still PENDING.
     * Returns a non-null error response if the transition is invalid, null on success.
     */
    private APIGatewayProxyResponseEvent applyClientTransition(
            String mappingId, String newJas, String currentJas, String mappingStatus, Context context) {

        if ("GRANTED".equals(newJas)) {
            if (!"REQUESTED".equals(currentJas) && !"NONE".equals(currentJas)) {
                return ApiGatewayUtils.unprocessable(
                        "Cannot grant access from current state: " + currentJas + ".");
            }
            if ("REJECTED".equals(mappingStatus)) {
                return ApiGatewayUtils.unprocessable("Cannot grant journal access on a REJECTED mapping.");
            }
            try {
                if ("PENDING".equals(mappingStatus)) {
                    // Auto-approve: granting access implies consent to the formal relationship
                    REPO.grantJournalAccessAndApproveMappingAtomically(mappingId);
                } else {
                    REPO.updateJournalAccess(mappingId, "GRANTED");
                }
            } catch (Exception e) {
                context.getLogger().log("UpdateJournalAccess (grant) write error [mappingId=" + mappingId + "]: " + e.getMessage());
                return ApiGatewayUtils.internalError();
            }

        } else if ("REVOKED".equals(newJas)) {
            if (!"REQUESTED".equals(currentJas) && !"GRANTED".equals(currentJas)) {
                return ApiGatewayUtils.unprocessable(
                        "Cannot revoke access from current state: " + currentJas + ".");
            }
            try {
                REPO.updateJournalAccess(mappingId, "REVOKED");
            } catch (Exception e) {
                context.getLogger().log("UpdateJournalAccess (revoke) write error [mappingId=" + mappingId + "]: " + e.getMessage());
                return ApiGatewayUtils.internalError();
            }

        } else {
            return ApiGatewayUtils.forbidden(
                    "Clients may only set journalAccessStatus to GRANTED or REVOKED.");
        }

        return null;
    }
}
