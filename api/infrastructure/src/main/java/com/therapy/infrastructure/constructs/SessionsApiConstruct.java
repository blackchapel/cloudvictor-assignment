package com.therapy.infrastructure.constructs;

import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Function;
import software.constructs.Construct;

/**
 * Creates the seven Session Lambda functions and grants DynamoDB permissions.
 * Route wiring is handled in TherapyApiStack via OpenApiSpecProcessor.
 *
 *   POST   /sessions                      → createSession
 *   GET    /sessions                      → listSessions
 *   GET    /sessions/{sessionId}          → getSession
 *   PUT    /sessions/{sessionId}          → updateSession
 *   DELETE /sessions/{sessionId}          → deleteSession
 *   POST   /sessions/{sessionId}/start    → startSession
 *   POST   /sessions/{sessionId}/end      → endSession
 */
public class SessionsApiConstruct extends Construct {

    private final Function createSession;
    private final Function listSessions;
    private final Function getSession;
    private final Function updateSession;
    private final Function deleteSession;
    private final Function startSession;
    private final Function endSession;

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
        startSession  = factory.create("StartSessionFunction",
                "com.therapy.handler.session.StartSessionHandler");
        endSession    = factory.create("EndSessionFunction",
                "com.therapy.handler.session.EndSessionHandler");

        sessionTable.grantReadWriteData(createSession);
        sessionTable.grantReadData(listSessions);
        sessionTable.grantReadData(getSession);
        sessionTable.grantReadWriteData(updateSession);
        sessionTable.grantReadWriteData(deleteSession);
        sessionTable.grantReadWriteData(startSession);
        sessionTable.grantReadWriteData(endSession);

        // getSession checks appointment table for client access to unavailable sessions
        appointmentTable.grantReadData(getSession);

        // endSession completes the confirmed appointment
        appointmentTable.grantReadWriteData(endSession);
    }

    public Function getCreateSession() { return createSession; }
    public Function getListSessions()  { return listSessions; }
    public Function getGetSession()    { return getSession; }
    public Function getUpdateSession() { return updateSession; }
    public Function getDeleteSession() { return deleteSession; }
    public Function getStartSession()  { return startSession; }
    public Function getEndSession()    { return endSession; }
}
