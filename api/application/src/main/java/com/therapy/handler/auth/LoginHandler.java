package com.therapy.handler.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.therapy.handler.BaseHandler;
import com.therapy.model.User;
import com.therapy.repository.UserRepository;
import com.therapy.util.ApiGatewayUtils;
import com.therapy.util.JwtUtils;
import org.mindrot.jbcrypt.BCrypt;
import com.therapy.util.DynamoDbClientFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * POST /auth/login
 * No authentication required.
 * Returns a JWT on valid credentials. Error responses are deliberately vague
 * (same 401 message) to prevent user enumeration.
 */
public class LoginHandler extends BaseHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String INVALID_CREDENTIALS_MSG = "Invalid email or password.";
    private static final int TOKEN_EXPIRY_SECONDS = 3600;

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

        String email = body.hasNonNull("email") ? body.get("email").asText("").trim().toLowerCase() : null;
        String password = body.hasNonNull("password") ? body.get("password").asText("") : null;

        if (isBlank(email) && isBlank(password))
            return ApiGatewayUtils.badRequest("'email' and 'password' is required.");
        if (isBlank(email))
            return ApiGatewayUtils.badRequest("'email' is required.");
        if (isBlank(password))
            return ApiGatewayUtils.badRequest("'password' is required.");

        Optional<User> opt;
        try {
            opt = REPO.findByEmail(email);
        } catch (Exception e) {
            context.getLogger().log("Login lookup error: " + e.getMessage());
            return ApiGatewayUtils.internalError();
        }

        // Deliberately vague: same message for unknown email and wrong password
        if (opt.isEmpty()) {
            return ApiGatewayUtils.unauthorized(INVALID_CREDENTIALS_MSG);
        }

        User user = opt.get();
        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            return ApiGatewayUtils.unauthorized(INVALID_CREDENTIALS_MSG);
        }

        String token = JwtUtils.generateToken(user.getUserId(), user.getUserType());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("tokenType", "Bearer");
        response.put("expiresIn", TOKEN_EXPIRY_SECONDS);
        response.put("userId", user.getUserId());
        response.put("userType", user.getUserType());

        return ApiGatewayUtils.ok(response);
    }
}
