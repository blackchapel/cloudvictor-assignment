package com.therapy.handler.mapping;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.handler.BaseHandler;
import com.therapy.model.CallerContext;
import com.therapy.model.Mapping;
import com.therapy.repository.MappingRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Optional;

public class GetMappingHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final MappingRepository REPO = new MappingRepository(DDB);

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
            opt = REPO.findById(mappingId);
        } catch (Exception e) {
            context.getLogger().log("GetMapping error [mappingId=" + mappingId + "]: " + e.getMessage());
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

        return ApiGatewayUtils.ok(mapping);
    }
}
