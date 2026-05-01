package com.therapy.handler.mapping;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.handler.BaseHandler;
import com.therapy.model.CallerContext;
import com.therapy.model.Mapping;
import com.therapy.model.PaginatedList;
import com.therapy.repository.MappingRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;
import java.util.Set;

public class ListMappingsHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final MappingRepository REPO = new MappingRepository(DDB);

    private static final Set<String> VALID_MAPPING_STATUSES     = Set.of("PENDING", "APPROVED", "REJECTED");
    private static final Set<String> VALID_JOURNAL_ACCESS_STATUSES = Set.of("NONE", "REQUESTED", "GRANTED", "REVOKED");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        // Validate optional filter parameters before querying
        String statusFilter = ApiGatewayUtils.getQueryParam(event, "mappingStatus");
        String jasFilter    = ApiGatewayUtils.getQueryParam(event, "journalAccessStatus");

        if (statusFilter != null && !VALID_MAPPING_STATUSES.contains(statusFilter)) {
            return ApiGatewayUtils.badRequest("Invalid 'mappingStatus' filter. Valid values: PENDING, APPROVED, REJECTED.");
        }
        if (jasFilter != null && !VALID_JOURNAL_ACCESS_STATUSES.contains(jasFilter)) {
            return ApiGatewayUtils.badRequest("Invalid 'journalAccessStatus' filter. Valid values: NONE, REQUESTED, GRANTED, REVOKED.");
        }

        int pageSize = Math.min(
                ApiGatewayUtils.getQueryParamInt(event, "pageSize", DEFAULT_PAGE_SIZE),
                MAX_PAGE_SIZE);
        int page = Math.max(ApiGatewayUtils.getQueryParamInt(event, "page", 1), 1);

        List<Mapping> mappings;
        try {
            if (caller.isClient()) {
                mappings = REPO.listByClient(caller.getUserId(), statusFilter, jasFilter, pageSize, null);
            } else {
                mappings = REPO.listByTherapist(caller.getUserId(), statusFilter, jasFilter, pageSize, null);
            }
        } catch (Exception e) {
            context.getLogger().log("ListMappings error [userId=" + caller.getUserId() + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        return ApiGatewayUtils.ok(new PaginatedList<>(mappings, page, pageSize, null));
    }
}
