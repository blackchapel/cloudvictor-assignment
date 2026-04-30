package com.therapy.util;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.HashMap;
import java.util.Map;

public class ApiGatewayUtils {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Map<String, String> CORS_HEADERS = new HashMap<>();

    static {
        CORS_HEADERS.put("Content-Type", "application/json");
        CORS_HEADERS.put("Access-Control-Allow-Origin", "*");
        CORS_HEADERS.put("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
        CORS_HEADERS.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
    }

    // ── Response builders ────────────────────────────────────────────────────

    public static APIGatewayProxyResponseEvent ok(Object body) {
        return response(200, body);
    }

    public static APIGatewayProxyResponseEvent created(Object body) {
        return response(201, body);
    }

    public static APIGatewayProxyResponseEvent noContent() {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(204)
                .withHeaders(CORS_HEADERS);
    }

    public static APIGatewayProxyResponseEvent badRequest(String message) {
        return error(400, "BAD_REQUEST", message);
    }

    public static APIGatewayProxyResponseEvent unauthorized(String message) {
        return error(401, "UNAUTHORIZED", message);
    }

    public static APIGatewayProxyResponseEvent forbidden(String message) {
        return error(403, "FORBIDDEN", message);
    }

    public static APIGatewayProxyResponseEvent notFound(String message) {
        return error(404, "NOT_FOUND", message);
    }

    public static APIGatewayProxyResponseEvent conflict(String message) {
        return error(409, "CONFLICT", message);
    }

    public static APIGatewayProxyResponseEvent unprocessable(String message) {
        return error(422, "UNPROCESSABLE_ENTITY", message);
    }

    public static APIGatewayProxyResponseEvent internalError() {
        return error(500, "INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please try again later.");
    }

    // ── Request helpers ──────────────────────────────────────────────────────

    /**
     * Null-safe header lookup. Header names in API Gateway Lambda proxy are
     * case-insensitive in HTTP/1.1 but the map keys may vary; we normalise to
     * lowercase for robustness.
     */
    public static String getHeader(APIGatewayProxyRequestEvent event, String name) {
        if (event.getHeaders() == null) return null;
        // Try exact key first, then lowercase
        String val = event.getHeaders().get(name);
        if (val == null) val = event.getHeaders().get(name.toLowerCase());
        if (val == null) val = event.getHeaders().get(name.toLowerCase().replace("-", ""));
        return val;
    }

    public static String getPathParam(APIGatewayProxyRequestEvent event, String name) {
        if (event.getPathParameters() == null) return null;
        return event.getPathParameters().get(name);
    }

    public static String getQueryParam(APIGatewayProxyRequestEvent event, String name) {
        if (event.getQueryStringParameters() == null) return null;
        return event.getQueryStringParameters().get(name);
    }

    public static int getQueryParamInt(APIGatewayProxyRequestEvent event, String name, int defaultValue) {
        String val = getQueryParam(event, name);
        if (val == null) return defaultValue;
        try {
            int parsed = Integer.parseInt(val);
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Returns true when the body is present and non-blank. */
    public static boolean hasBody(APIGatewayProxyRequestEvent event) {
        return event.getBody() != null && !event.getBody().isBlank();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static APIGatewayProxyResponseEvent response(int statusCode, Object body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(CORS_HEADERS)
                    .withBody(json);
        } catch (Exception e) {
            return internalError();
        }
    }

    private static APIGatewayProxyResponseEvent error(int statusCode, String code, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(CORS_HEADERS)
                    .withBody(MAPPER.writeValueAsString(body));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(CORS_HEADERS)
                    .withBody("{\"code\":\"" + code + "\",\"message\":\"" + message.replace("\"", "'") + "\"}");
        }
    }
}
