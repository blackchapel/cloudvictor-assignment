package com.therapy.infrastructure.constructs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the OpenAPI YAML spec and injects x-amazon-apigateway-integration
 * extensions before passing the spec to SpecRestApi.
 *
 * Call addLambdaIntegration() for each implemented route, then call
 * addMockIntegrationsForUnimplementedRoutes() to fill remaining paths with
 * 501 mock responses (API Gateway rejects specs that have paths without integrations).
 */
public class OpenApiSpecProcessor {

    private final Map<String, Object> spec;

    @SuppressWarnings("unchecked")
    public OpenApiSpecProcessor(String specPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        spec = mapper.readValue(new File(specPath), Map.class);
    }

    public void addLambdaIntegration(String path, String method, String functionArn, String region) {
        Map<String, Object> integration = new HashMap<>();
        integration.put("type", "aws_proxy");
        integration.put("httpMethod", "POST"); // always POST for Lambda proxy regardless of API method
        integration.put("uri", "arn:aws:apigateway:" + region + ":lambda:path/2015-03-31/functions/" + functionArn + "/invocations");
        integration.put("passthroughBehavior", "when_no_match");
        integration.put("contentHandling", "CONVERT_TO_TEXT");
        injectIntegration(path, method.toLowerCase(), integration);
    }

    /** Fills any path+method that has no integration yet with a 501 mock. */
    @SuppressWarnings("unchecked")
    public void addMockIntegrationsForUnimplementedRoutes() {
        Map<String, Object> paths = getPaths();
        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            Map<String, Object> pathItem = (Map<String, Object>) pathEntry.getValue();
            for (Map.Entry<String, Object> methodEntry : pathItem.entrySet()) {
                String method = methodEntry.getKey();
                if ("parameters".equals(method) || "summary".equals(method)) continue;
                Map<String, Object> operation = (Map<String, Object>) methodEntry.getValue();
                if (!operation.containsKey("x-amazon-apigateway-integration")) {
                    injectIntegration(pathEntry.getKey(), method, buildMockIntegration());
                }
            }
        }
    }

    public Map<String, Object> getSpec() {
        return spec;
    }

    @SuppressWarnings("unchecked")
    private void injectIntegration(String path, String method, Map<String, Object> integration) {
        Map<String, Object> pathItem = (Map<String, Object>) getPaths().get(path);
        if (pathItem == null) return;
        Map<String, Object> operation = (Map<String, Object>) pathItem.get(method);
        if (operation == null) return;
        operation.put("x-amazon-apigateway-integration", integration);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPaths() {
        return (Map<String, Object>) spec.get("paths");
    }

    private Map<String, Object> buildMockIntegration() {
        Map<String, Object> requestTemplates = new HashMap<>();
        requestTemplates.put("application/json", "{\"statusCode\": 501}");

        Map<String, Object> responseTemplates = new HashMap<>();
        responseTemplates.put("application/json", "{\"message\": \"Not implemented\"}");

        Map<String, Object> defaultResponse = new HashMap<>();
        defaultResponse.put("statusCode", "501");
        defaultResponse.put("responseTemplates", responseTemplates);

        Map<String, Object> responses = new HashMap<>();
        responses.put("default", defaultResponse);

        Map<String, Object> integration = new HashMap<>();
        integration.put("type", "mock");
        integration.put("requestTemplates", requestTemplates);
        integration.put("responses", responses);
        return integration;
    }
}
