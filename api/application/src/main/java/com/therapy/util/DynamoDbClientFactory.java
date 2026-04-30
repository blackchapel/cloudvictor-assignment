package com.therapy.util;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/**
 * Creates a DynamoDbClient that points at DynamoDB Local when the
 * DYNAMODB_ENDPOINT env var is set (e.g. http://localhost:8000),
 * or the real AWS DynamoDB otherwise.
 *
 * All handlers use this factory instead of building the client inline,
 * so local and prod require zero code changes.
 */
public class DynamoDbClientFactory {

    private static final String ENDPOINT_ENV = "DYNAMODB_ENDPOINT";

    public static DynamoDbClient create() {
        String endpoint = System.getenv(ENDPOINT_ENV);

        if (endpoint != null && !endpoint.isBlank()) {
            // Local mode — fixed dummy credentials satisfy the SDK requirement
            return DynamoDbClient.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("local", "local")))
                    .httpClient(UrlConnectionHttpClient.create())
                    .build();
        }

        return DynamoDbClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}
