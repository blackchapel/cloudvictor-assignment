# Therapy API — Backend Implementation

Java + AWS Lambda + API Gateway + DynamoDB, deployed via AWS CDK.

## Implemented APIs

| Domain | Method | Path |
| --- | --- | --- |
| **Auth** | POST | `/auth/clients/register` |
| | POST | `/auth/therapists/register` |
| | POST | `/auth/login` |
| **Sessions** | POST | `/sessions` |
| | GET | `/sessions` |
| | GET | `/sessions/{sessionId}` |
| | PUT | `/sessions/{sessionId}` |
| | DELETE | `/sessions/{sessionId}` |
| **Mappings** | POST | `/mappings` |
| | GET | `/mappings` |
| | GET | `/mappings/{mappingId}` |
| | DELETE | `/mappings/{mappingId}` |
| | PATCH | `/mappings/{mappingId}/mapping-status` |
| | PATCH | `/mappings/{mappingId}/journal-access` |
| **Messages** | POST | `/messages` |
| | GET | `/messages` |
| | PUT | `/messages/{messageId}` |
| | DELETE | `/messages/{messageId}` |
| **Appointments** | POST | `/appointments` |
| | GET | `/appointments` |
| | GET | `/appointments/{appointmentId}` |
| | PATCH | `/appointments/{appointmentId}` |
| | DELETE | `/appointments/{appointmentId}` |

## Project Structure

```
api/
├── pom.xml                          # Parent POM (multi-module)
├── application/                     # Lambda handler code
│   ├── pom.xml
│   └── src/main/java/com/therapy/
│       ├── handler/
│       │   ├── BaseHandler.java         # Shared JWT auth helper
│       │   ├── auth/                    # 3 auth handlers
│       │   ├── session/                 # 5 session handlers
│       │   ├── mapping/                 # 6 mapping handlers
│       │   ├── message/                 # 4 message handlers
│       │   └── appointment/             # 5 appointment handlers
│       ├── model/                   # Session, Mapping, Message, Appointment, User,
│       │                            #   ClientResponse, TherapistResponse, PaginatedList
│       ├── repository/              # DynamoDB interaction layer
│       │                            #   UserRepository, SessionRepository,
│       │                            #   MappingRepository, MessageRepository,
│       │                            #   RelationshipRepository, AppointmentRepository
│       └── util/                    # JwtUtils, ApiGatewayUtils, IdGenerator
└── infrastructure/                  # AWS CDK stack
    ├── cdk.json
    ├── pom.xml
    └── src/main/java/com/therapy/infrastructure/
        ├── TherapyApiApp.java        # CDK entry point
        ├── TherapyApiStack.java      # Main stack
        └── constructs/
            ├── DynamoDbTablesConstruct.java  # All 6 DDB tables + GSIs/LSIs
            ├── LambdaFactory.java            # Shared Lambda factory (DRY)
            ├── AuthApiConstruct.java         # Auth Lambdas + routes
            ├── SessionsApiConstruct.java     # Session Lambdas + routes
            ├── MappingsApiConstruct.java     # Mapping Lambdas + routes
            ├── MessagesApiConstruct.java     # Message Lambdas + routes
            └── AppointmentsApiConstruct.java # Appointment Lambdas + routes
```

## Prerequisites

- Java 11+
- Maven 3.8+
- Node.js 20+ (for CDK CLI)
- AWS CLI configured (`aws configure`)
- AWS CDK CLI: `npm install -g aws-cdk`

## Build & Deploy

### 1. Build the Lambda JAR

```bash
cd api
mvn package -pl application -DskipTests
```

This produces `application/target/therapy-api-application.jar` (~17 MB fat JAR).

### 2. Bootstrap CDK (first time only)

```bash
cd infrastructure
cdk bootstrap
```

### 3. Deploy

```bash
cd infrastructure
cdk deploy
```

CDK will output the API Gateway base URL after deployment:
```
Outputs:
TherapyApiStack.TherapyRestApiEndpoint = https://<id>.execute-api.<region>.amazonaws.com/prod/
```

## Authentication

The auth endpoints (`/auth/**`) require no token. All other endpoints require a JWT Bearer token in the `Authorization` header.

**Register and login to get a token:**

```
POST /auth/clients/register     { "email", "password", "name" }
POST /auth/therapists/register  { "email", "password", "name", "licenseNumber", "yearsOfExperience" }
POST /auth/login                { "email", "password" }
```

Login returns:
```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": "cli_...",
  "userType": "CLIENT"
}
```

Use the token as `Authorization: Bearer <token>` on all subsequent requests.

**Token internals:** JWT signed with HS256. Payload encodes `sub` (userId) and `userType`. Secret is in the `JWT_SECRET` Lambda environment variable (set in `TherapyApiStack.java`). Rotate to AWS Secrets Manager in production.

## DynamoDB Tables

| Table | PK | SK | Key GSIs |
| --- | --- | --- | --- |
| `TherapyUserTable` | `userId` | — | GSI_EmailIndex, GSI_UserTypeIndex |
| `TherapySessionTable` | `sessionId` | — | GSI_TherapistSessions, GSI_StatusSchedule |
| `TherapyMappingTable` | `mappingId` | — | GSI_ClientMappings, GSI_TherapistMappings, GSI_ClientTherapistLookup |
| `TherapyMessageTable` | `threadKey` (`THREAD#{cid}#{tid}`) | `messageSk` (`sentAt#messageId`) | GSI_MessageById |
| `TherapyRelationshipTable` | `clientId` | `therapistId` | GSI_TherapistRelationships |
| `TherapyAppointmentTable` | `appointmentId` | — | GSI_ClientAppointments, GSI_TherapistAppointments, GSI_SessionAppointments, GSI_ClientSessionDedup |

See [db_design/](../db_design/) for the full schema, index projections, and access patterns.

## Design Decisions & Assumptions

1. **One Lambda per API** — 23 separate Lambda functions (3 auth + 5 sessions + 6 mappings + 4 messages + 5 appointments); consistent with the assignment requirement and the reference starting point.
2. **JWT secret in env var** — acceptable for assignment scope; swap for AWS Secrets Manager in production.
3. **DynamoDB PAY_PER_REQUEST** — no capacity planning required for a demo/assignment workload.
4. **Pagination** — DynamoDB cursor-based pagination is simplified to `page`/`pageSize` query parameters for the assignment. Passing `null` as `exclusiveStartKey` always returns the first page; true cursor pagination would require exposing a `nextToken` derived from DynamoDB's `LastEvaluatedKey`.
5. **Password hashing** — BCrypt via `org.mindrot:jbcrypt`. The stored attribute is named `passwordHash` and is never included in any API response.
6. **Email uniqueness** — enforced at registration time via `GSI_EmailIndex` query before the `PutItem`. The same 409 response is returned whether the conflicting account is a client or therapist.
7. **`yearsOfExperience` mandatory for therapists** — it is the sort key on `GSI_UserTypeIndex` which powers the therapist discovery endpoint. Without it a therapist would be invisible to clients browsing therapists.
8. **RelationshipTable sync** — the `RelationshipTable` is a denormalised read model. It is kept in sync whenever mapping status or journal access changes (`UpdateMappingStatusHandler`, `UpdateJournalAccessHandler`). Sync failures are logged but do not fail the primary request.
9. **No mapping required to send messages** — any authenticated user who names themselves as `clientId` or `therapistId` in the request can send a message. A relationship row is upserted on first message.
10. **privateNotes access** — `GetSessionHandler` strips `privateNotes` from the response for CLIENT callers at the application layer. `UpdateSessionHandler` rejects a `privateNotes` field from CLIENT callers with 403 before the DynamoDB write.
11. **Appointment confirmation** — Confirming an appointment (PENDING→CONFIRMED) batch-rejects all other PENDING appointments for the same session and marks the session `isAvailable=false`. These writes are sequential (not transactional) for simplicity; a production system would use DynamoDB `TransactWriteItems`. The `pendingCount` counter on SessionTable is updated atomically with `ADD` expressions.
12. **Therapist list appointments** — Therapists must supply a `sessionId` query parameter and may only list appointments for sessions they own. This scopes the query to `GSI_SessionAppointments` which is more efficient than scanning all therapist appointments.
