package com.therapy.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.therapy.model.CallerContext;

public class JwtUtils {

    private static final String JWT_SECRET_ENV = "JWT_SECRET";

    public static String generateToken(String userId, String userType) {
        String secret = System.getenv(JWT_SECRET_ENV);
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withSubject(userId)
                .withClaim("userType", userType)
                .withIssuedAt(new java.util.Date())
                .withExpiresAt(new java.util.Date(System.currentTimeMillis() + 3600 * 1000L))
                .sign(algorithm);
    }

    public static CallerContext verify(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new SecurityException("Missing or invalid Authorization header");
        }
        String token = authorizationHeader.substring(7);
        String secret = System.getenv(JWT_SECRET_ENV);
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            DecodedJWT decoded = verifier.verify(token);
            String userId = decoded.getSubject();
            String userType = decoded.getClaim("userType").asString();
            return new CallerContext(userId, userType);
        } catch (JWTVerificationException e) {
            throw new SecurityException("Invalid or expired token: " + e.getMessage());
        }
    }
}
