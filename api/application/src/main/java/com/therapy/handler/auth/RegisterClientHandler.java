package com.therapy.handler.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.therapy.handler.BaseHandler;
import com.therapy.model.ClientResponse;
import com.therapy.model.User;
import com.therapy.repository.UserRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.IdGenerator;
import org.mindrot.jbcrypt.BCrypt;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Instant;

/**
 * POST /auth/clients/register
 * No authentication required.
 */
public class RegisterClientHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DynamoDbClient DDB = DynamoDbClientFactory.create();
    private static final UserRepository REPO = new UserRepository(DDB);

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        if (!ApiGatewayUtils.hasBody(event)) {
            return ApiGatewayUtils.badRequest("Request body is required.");
        }

        JsonNode body;
        try {
            body = ApiGatewayUtils.MAPPER.readTree(event.getBody());
        } catch (Exception e) {
            return ApiGatewayUtils.badRequest("Request body is not valid JSON.");
        }

        String email    = body.hasNonNull("email")    ? body.get("email").asText("").trim().toLowerCase()    : null;
        String password = body.hasNonNull("password") ? body.get("password").asText("")                       : null;
        String name     = body.hasNonNull("name")     ? body.get("name").asText("").trim()                   : null;

        if (isBlank(email))    return ApiGatewayUtils.badRequest("'email' is required.");
        if (isBlank(password)) return ApiGatewayUtils.badRequest("'password' is required.");
        if (isBlank(name))     return ApiGatewayUtils.badRequest("'name' is required.");

        if (!email.contains("@")) return ApiGatewayUtils.badRequest("'email' must be a valid email address.");
        if (!isValidPassword(password))
            return ApiGatewayUtils.badRequest("'password' must be at least 8 characters and contain at least one letter and one digit.");

        try {
            if (REPO.existsByEmail(email)) {
                return ApiGatewayUtils.conflict("An account with this email address already exists.");
            }
        } catch (Exception e) {
            context.getLogger().log("RegisterClient email-check error: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        String now = Instant.now().toString();
        User user = new User();
        user.setUserId(IdGenerator.clientId());
        user.setUserType("CLIENT");
        user.setEmail(email);
        user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        user.setName(name);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            REPO.create(user);
        } catch (Exception e) {
            context.getLogger().log("RegisterClient write error: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        return ApiGatewayUtils.created(ClientResponse.from(user));
    }

    private static boolean isValidPassword(String password) {
        if (password.length() < 8) return false;
        boolean hasLetter = false, hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }
        return hasLetter && hasDigit;
    }
}
