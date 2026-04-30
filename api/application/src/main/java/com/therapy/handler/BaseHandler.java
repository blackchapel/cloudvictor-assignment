package com.therapy.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.therapy.model.CallerContext;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.JwtUtils;

/**
 * Shared auth helper for all Lambda handlers.
 * Returns null on success (caller populated) or a pre-built 401 response on failure.
 */
public abstract class BaseHandler {

    /**
     * Extracts and verifies the JWT from the Authorization header.
     *
     * @return the verified CallerContext, or null if auth failed (response is written to out[0])
     */
    protected CallerContext authenticate(APIGatewayProxyRequestEvent event,
                                         APIGatewayProxyResponseEvent[] out) {
        try {
            String header = ApiGatewayUtils.getHeader(event, "Authorization");
            CallerContext ctx = JwtUtils.verify(header);
            out[0] = null;
            return ctx;
        } catch (SecurityException e) {
            out[0] = ApiGatewayUtils.unauthorized("Authentication token is missing or has expired.");
            return null;
        }
    }

    /** Convenience: validates that a path/query parameter is non-null and non-blank. */
    protected static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
