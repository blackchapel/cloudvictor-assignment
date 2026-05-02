package com.therapy.infrastructure;

import com.therapy.infrastructure.constructs.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Permission;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.Map;

public class TherapyApiStack extends Stack {

    public TherapyApiStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // ── DynamoDB tables ─────────────────────────────────────────────────────
        DynamoDbTablesConstruct tables = new DynamoDbTablesConstruct(this, "DynamoDbTables");

        // ── Common environment variables shared by all Lambda functions ─────────
        Map<String, String> commonEnv = new HashMap<>();
        commonEnv.put("USER_TABLE_NAME",         tables.getUserTable().getTableName());
        commonEnv.put("SESSION_TABLE_NAME",      tables.getSessionTable().getTableName());
        commonEnv.put("MAPPING_TABLE_NAME",      tables.getMappingTable().getTableName());
        commonEnv.put("MESSAGE_TABLE_NAME",      tables.getMessageTable().getTableName());
        commonEnv.put("RELATIONSHIP_TABLE_NAME", tables.getRelationshipTable().getTableName());
        commonEnv.put("APPOINTMENT_TABLE_NAME",  tables.getAppointmentTable().getTableName());
        commonEnv.put("JWT_SECRET", "therapy-api-jwt-secret-change-in-prod");

        LambdaFactory lambdaFactory = new LambdaFactory(this, commonEnv);

        // ── Lambda constructs (IAM grants wired here; routes wired via spec) ────
        AuthApiConstruct auth = new AuthApiConstruct(this, "AuthApi",
                lambdaFactory, tables.getUserTable());

        SessionsApiConstruct sessions = new SessionsApiConstruct(this, "SessionsApi",
                lambdaFactory, tables.getSessionTable(), tables.getAppointmentTable());

        MappingsApiConstruct mappings = new MappingsApiConstruct(this, "MappingsApi",
                lambdaFactory, tables.getMappingTable(), tables.getRelationshipTable());

        MessagesApiConstruct messages = new MessagesApiConstruct(this, "MessagesApi",
                lambdaFactory, tables.getMessageTable(), tables.getRelationshipTable());

        AppointmentsApiConstruct appointments = new AppointmentsApiConstruct(this, "AppointmentsApi",
                lambdaFactory, tables.getAppointmentTable(),
                tables.getSessionTable(), tables.getMappingTable());

        // ── OpenAPI spec: inject Lambda integrations for all 18 implemented routes
        OpenApiSpecProcessor processor;
        try {
            processor = new OpenApiSpecProcessor("../../swagger/therapy-api.yaml");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load OpenAPI spec: " + e.getMessage(), e);
        }

        String region = getRegion();

        // Auth
        processor.addLambdaIntegration("/auth/clients/register",    "post", auth.getRegisterClient().getFunctionArn(),    region);
        processor.addLambdaIntegration("/auth/therapists/register", "post", auth.getRegisterTherapist().getFunctionArn(), region);
        processor.addLambdaIntegration("/auth/login",               "post", auth.getLogin().getFunctionArn(),             region);

        // Sessions
        processor.addLambdaIntegration("/sessions",                       "post",   sessions.getCreateSession().getFunctionArn(), region);
        processor.addLambdaIntegration("/sessions",                       "get",    sessions.getListSessions().getFunctionArn(),  region);
        processor.addLambdaIntegration("/sessions/{sessionId}",           "get",    sessions.getGetSession().getFunctionArn(),    region);
        processor.addLambdaIntegration("/sessions/{sessionId}",           "put",    sessions.getUpdateSession().getFunctionArn(), region);
        processor.addLambdaIntegration("/sessions/{sessionId}",           "delete", sessions.getDeleteSession().getFunctionArn(), region);
        processor.addLambdaIntegration("/sessions/{sessionId}/start",     "post",   sessions.getStartSession().getFunctionArn(),  region);
        processor.addLambdaIntegration("/sessions/{sessionId}/end",       "post",   sessions.getEndSession().getFunctionArn(),    region);

        // Mappings
        processor.addLambdaIntegration("/mappings",                              "post",   mappings.getCreateMapping().getFunctionArn(),       region);
        processor.addLambdaIntegration("/mappings",                              "get",    mappings.getListMappings().getFunctionArn(),         region);
        processor.addLambdaIntegration("/mappings/{mappingId}",                  "get",    mappings.getGetMapping().getFunctionArn(),           region);
        processor.addLambdaIntegration("/mappings/{mappingId}",                  "delete", mappings.getDeleteMapping().getFunctionArn(),        region);
        processor.addLambdaIntegration("/mappings/{mappingId}/mapping-status",   "patch",  mappings.getUpdateMappingStatus().getFunctionArn(),  region);
        processor.addLambdaIntegration("/mappings/{mappingId}/journal-access",   "patch",  mappings.getUpdateJournalAccess().getFunctionArn(),  region);

        // Messages
        processor.addLambdaIntegration("/messages",              "post",   messages.getSendMessage().getFunctionArn(),   region);
        processor.addLambdaIntegration("/messages",              "get",    messages.getListMessages().getFunctionArn(),  region);
        processor.addLambdaIntegration("/messages/{messageId}",  "put",    messages.getUpdateMessage().getFunctionArn(), region);
        processor.addLambdaIntegration("/messages/{messageId}",  "delete", messages.getDeleteMessage().getFunctionArn(), region);

        // Appointments
        processor.addLambdaIntegration("/appointments",                       "post",   appointments.getCreateAppointment().getFunctionArn(), region);
        processor.addLambdaIntegration("/appointments",                       "get",    appointments.getListAppointments().getFunctionArn(),  region);
        processor.addLambdaIntegration("/appointments/{appointmentId}",       "get",    appointments.getGetAppointment().getFunctionArn(),    region);
        processor.addLambdaIntegration("/appointments/{appointmentId}",       "patch",  appointments.getPatchAppointment().getFunctionArn(),  region);
        processor.addLambdaIntegration("/appointments/{appointmentId}",       "delete", appointments.getDeleteAppointment().getFunctionArn(), region);

        // Fill every other path in the spec with a 501 mock (required by API Gateway)
        processor.addMockIntegrationsForUnimplementedRoutes();

        // ── API Gateway — spec-driven ───────────────────────────────────────────
        LogGroup accessLogs = LogGroup.Builder.create(this, "ApiAccessLogs")
                .logGroupName("/therapy-api/access-logs")
                .build();

        SpecRestApi api = SpecRestApi.Builder.create(this, "TherapyRestApi")
                .restApiName("therapy-api")
                .apiDefinition(ApiDefinition.fromInline(processor.getSpec()))
                .deployOptions(StageOptions.builder()
                        .stageName("prod")
                        .accessLogDestination(new LogGroupLogDestination(accessLogs))
                        .accessLogFormat(AccessLogFormat.jsonWithStandardFields())
                        .build())
                .build();

        // ── Lambda invoke permissions ───────────────────────────────────────────
        // SpecRestApi does not wire permissions automatically (unlike LambdaIntegration).
        // Grant API Gateway execution role permission to invoke each Lambda.
        String executeApiArn = String.format("arn:aws:execute-api:%s:%s:%s/*/*",
                getRegion(), getAccount(), api.getRestApiId());

        Function[] allFunctions = {
                auth.getRegisterClient(), auth.getRegisterTherapist(), auth.getLogin(),
                sessions.getCreateSession(), sessions.getListSessions(), sessions.getGetSession(),
                sessions.getUpdateSession(), sessions.getDeleteSession(),
                sessions.getStartSession(), sessions.getEndSession(),
                mappings.getCreateMapping(), mappings.getListMappings(), mappings.getGetMapping(),
                mappings.getDeleteMapping(), mappings.getUpdateMappingStatus(), mappings.getUpdateJournalAccess(),
                messages.getSendMessage(), messages.getListMessages(),
                messages.getUpdateMessage(), messages.getDeleteMessage(),
                appointments.getCreateAppointment(), appointments.getListAppointments(),
                appointments.getGetAppointment(), appointments.getPatchAppointment(),
                appointments.getDeleteAppointment()
        };
        for (int i = 0; i < allFunctions.length; i++) {
            allFunctions[i].addPermission("ApiGwInvoke" + i, Permission.builder()
                    .principal(new ServicePrincipal("apigateway.amazonaws.com"))
                    .action("lambda:InvokeFunction")
                    .sourceArn(executeApiArn)
                    .build());
        }
    }
}
