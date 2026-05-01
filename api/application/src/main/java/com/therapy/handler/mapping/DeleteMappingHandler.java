package com.therapy.handler.mapping;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
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

public class DeleteMappingHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final MappingRepository MAPPING_REPO = new MappingRepository(DDB);
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

        Optional<Mapping> opt;
        try {
            opt = MAPPING_REPO.findById(mappingId);
        } catch (Exception e) {
            context.getLogger().log("DeleteMapping lookup error [mappingId=" + mappingId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        // Per spec: return 403 (not 404) to non-parties to avoid leaking mapping existence
        if (opt.isEmpty()) {
            return ApiGatewayUtils.forbidden("You do not have access to this mapping.");
        }

        Mapping mapping = opt.get();
        boolean isParty = caller.getUserId().equals(mapping.getClientId())
                || caller.getUserId().equals(mapping.getTherapistId());
        if (!isParty) {
            return ApiGatewayUtils.forbidden("You do not have access to this mapping.");
        }

        try {
            MAPPING_REPO.delete(mappingId);
            REL_REPO.removeMappingType(mapping.getClientId(), mapping.getTherapistId());
        } catch (ConditionalCheckFailedException e) {
            // Mapping was already deleted by a concurrent request — treat as success
            return ApiGatewayUtils.noContent();
        } catch (Exception e) {
            context.getLogger().log("DeleteMapping error [mappingId=" + mappingId + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        return ApiGatewayUtils.noContent();
    }
}
