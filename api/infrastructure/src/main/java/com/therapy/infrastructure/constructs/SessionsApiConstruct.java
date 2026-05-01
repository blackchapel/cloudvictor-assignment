package com.therapy.infrastructure.constructs;

import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Function;
import software.constructs.Construct;

/**
 * Creates the five Session Lambda functions and grants DynamoDB permissions.
 * Route wiring is handled in TherapyApiStack via OpenApiSpecProcessor.
 *
 *   POST   /sessions              → createSession
 *   GET    /sessions              → listSessions
 *   GET    /sessions/{sessionId}  → getSession
 *   PUT    /sessions/{sessionId}  → updateSession
 *   DELETE /sessions/{sessionId}  → deleteSession
 */
public class SessionsApiConstruct extends Construct {

    private final Function createSession;
    private final Function listSessions;
    private final Function getSession;
    private final Function updateSession;
    private final Function deleteSession;

    public SessionsApiConstruct(Construct scope, String id,
                                LambdaFactory factory,
                                Table sessionTable,
                                Table appointmentTable) {
        super(scope, id);

        createSession = factory.create("CreateSessionFunction",
                "com.therapy.handler.session.CreateSessionHandler");
        listSessions  = factory.create("ListSessionsFunction",
                "com.therapy.handler.session.ListSessionsHandler");
        getSession    = factory.create("GetSessionFunction",
                "com.therapy.handler.session.GetSessionHandler");
        updateSession = factory.create("UpdateSessionFunction",
                "com.therapy.handler.session.UpdateSessionHandler");
        deleteSession = factory.create("DeleteSessionFunction",
                "com.therapy.handler.session.DeleteSessionHandler");

        sessionTable.grantReadWriteData(createSession);
        sessionTable.grantReadData(listSessions);
        sessionTable.grantReadData(getSession);
        sessionTable.grantReadWriteData(updateSession);
        sessionTable.grantReadWriteData(deleteSession);

        // getSession checks appointment table for client access to unavailable sessions
        appointmentTable.grantReadData(getSession);
    }

    public Function getCreateSession() { return createSession; }
    public Function getListSessions()  { return listSessions; }
    public Function getGetSession()    { return getSession; }
    public Function getUpdateSession() { return updateSession; }
    public Function getDeleteSession() { return deleteSession; }
}
