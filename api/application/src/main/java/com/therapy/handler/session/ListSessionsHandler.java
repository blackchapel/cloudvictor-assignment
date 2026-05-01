package com.therapy.handler.session;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.handler.BaseHandler;
import com.therapy.model.CallerContext;
import com.therapy.model.PaginatedList;
import com.therapy.model.Session;
import com.therapy.repository.SessionRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.List;

public class ListSessionsHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final SessionRepository REPO = new SessionRepository(DDB);

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        APIGatewayProxyResponseEvent[] authOut = new APIGatewayProxyResponseEvent[1];
        CallerContext caller = authenticate(event, authOut);
        if (caller == null) return authOut[0];

        int pageSize = Math.min(
                ApiGatewayUtils.getQueryParamInt(event, "pageSize", DEFAULT_PAGE_SIZE),
                MAX_PAGE_SIZE);
        int page = Math.max(ApiGatewayUtils.getQueryParamInt(event, "page", 1), 1);
        String fromDate = ApiGatewayUtils.getQueryParam(event, "fromDate");
        String toDate   = ApiGatewayUtils.getQueryParam(event, "toDate");

        List<Session> sessions;
        try {
            if (caller.isTherapist()) {
                // Therapist sees only their own sessions; optional isAvailable filter
                String isAvailableParam = ApiGatewayUtils.getQueryParam(event, "isAvailable");
                Boolean isAvailable = isAvailableParam != null ? Boolean.parseBoolean(isAvailableParam) : null;
                sessions = REPO.listByTherapist(caller.getUserId(), fromDate, toDate, isAvailable, pageSize, null);
            } else {
                // Client browses platform sessions filtered by therapistId; always isAvailable=true
                String therapistFilter = ApiGatewayUtils.getQueryParam(event, "therapistId");
                sessions = REPO.listForClient(fromDate, toDate, therapistFilter, pageSize, null);
            }
        } catch (Exception e) {
            context.getLogger().log("ListSessions error [userId=" + caller.getUserId() + "]: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        sessions.forEach(s -> {
            s.setPrivateNotes(null);
            s.setSharedNotes(null);
        });

        return ApiGatewayUtils.ok(new PaginatedList<>(sessions, page, pageSize, null));
    }
}
