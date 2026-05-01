package com.therapy.infrastructure.constructs;

import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Function;
import software.constructs.Construct;

/**
 * Creates the three Auth Lambda functions and grants DynamoDB permissions.
 * Route wiring is handled in TherapyApiStack via OpenApiSpecProcessor.
 *
 *   POST /auth/clients/register   → registerClient
 *   POST /auth/therapists/register → registerTherapist
 *   POST /auth/login               → login
 */
public class AuthApiConstruct extends Construct {

    private final Function registerClient;
    private final Function registerTherapist;
    private final Function login;

    public AuthApiConstruct(Construct scope, String id,
                            LambdaFactory factory,
                            Table userTable) {
        super(scope, id);

        registerClient    = factory.create("RegisterClientFunction",
                "com.therapy.handler.auth.RegisterClientHandler");
        registerTherapist = factory.create("RegisterTherapistFunction",
                "com.therapy.handler.auth.RegisterTherapistHandler");
        login             = factory.create("LoginFunction",
                "com.therapy.handler.auth.LoginHandler");

        userTable.grantReadWriteData(registerClient);
        userTable.grantReadWriteData(registerTherapist);
        userTable.grantReadData(login);
    }

    public Function getRegisterClient()    { return registerClient; }
    public Function getRegisterTherapist() { return registerTherapist; }
    public Function getLogin()             { return login; }
}
