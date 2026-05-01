# Therapy Journalling API — Backend

Java + AWS Lambda + API Gateway + DynamoDB, deployed via AWS CDK. This is the backend implementation for the Therapy Journalling platform — it powers secure journalling, therapist–client mapping, session management, appointment booking, and messaging.

---

## Table of Contents

- [Implemented APIs](#implemented-apis)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Build and Deploy](#build-and-deploy)
- [Authentication](#authentication)
- [DynamoDB Tables](#dynamodb-tables)
- [Design Decisions and Assumptions](#design-decisions-and-assumptions)

---

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

---

## Project Structure

```text
api/
├── pom.xml                          # Parent POM (multi-module)
├── application/                     # Lambda handler code
│   ├── pom.xml
│   └── src/main/java/com/therapy/
│       ├── handler/
│       │   ├── BaseHandler.java         # Shared JWT auth + convenience helpers
│       │   ├── auth/                    # 3 handlers  — register client/therapist, login
│       │   ├── session/                 # 5 handlers  — CRUD + list
│       │   ├── mapping/                 # 6 handlers  — CRUD + mapping-status + journal-access
│       │   ├── message/                 # 4 handlers  — send, list, update, delete
│       │   └── appointment/             # 5 handlers  — create, list, get, patch, delete
│       ├── model/
│       │   ├── Session.java, Mapping.java, Message.java, Appointment.java
│       │   ├── User.java, ClientResponse.java, TherapistResponse.java
│       │   └── PaginatedList.java, CallerContext.java
│       ├── repository/                  # DynamoDB interaction layer (one class per table)
│       │   ├── UserRepository.java
│       │   ├── SessionRepository.java
│       │   ├── MappingRepository.java
│       │   ├── MessageRepository.java
│       │   ├── RelationshipRepository.java
│       │   └── AppointmentRepository.java
│       └── util/
│           ├── JwtUtils.java            # JWT sign + verify (HS256)
│           ├── ApiGatewayUtils.java     # Response builders + request helpers
│           ├── IdGenerator.java         # Prefixed UUID helpers (cli_, ses_, apt_, …)
│           └── DynamoDbClientFactory.java
└── infrastructure/                  # AWS CDK stack
    ├── cdk.json
    ├── pom.xml
    └── src/main/java/com/therapy/infrastructure/
        ├── TherapyApiApp.java            # CDK entry point
        ├── TherapyApiStack.java          # Main stack — wires tables, Lambdas, API Gateway
        └── constructs/
            ├── DynamoDbTablesConstruct.java   # All 6 tables + GSIs/LSIs
            ├── LambdaFactory.java             # Shared factory — DRY config (runtime, memory, timeout, env)
            ├── OpenApiSpecProcessor.java      # Reads YAML, injects Lambda integrations at synth time
            ├── AuthApiConstruct.java          # 3 auth Lambdas + IAM grants
            ├── SessionsApiConstruct.java      # 5 session Lambdas + IAM grants
            ├── MappingsApiConstruct.java      # 6 mapping Lambdas + IAM grants
            ├── MessagesApiConstruct.java      # 4 message Lambdas + IAM grants
            └── AppointmentsApiConstruct.java  # 5 appointment Lambdas + IAM grants
```

---

## Prerequisites

- Java 11+
- Maven 3.8+
- Node.js 20+ (for CDK CLI)
- AWS CLI configured (`aws configure`)
- AWS CDK CLI: `npm install -g aws-cdk`

---

## Build and Deploy

### 1. Build the Lambda JAR

```bash
cd api
mvn package -pl application -DskipTests
```

Produces `application/target/therapy-api-application.jar` — a fat JAR (~17 MB) containing all dependencies.

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

CDK outputs the API Gateway base URL on completion:

```text
Outputs:
TherapyApiStack.TherapyRestApiEndpoint = https://<id>.execute-api.<region>.amazonaws.com/prod/
```

**Deployed API base URL:**

```text
https://mpcqc90o29.execute-api.ap-south-1.amazonaws.com/prod
```

---

## Authentication

The auth endpoints (`/auth/**`) require no token. All other endpoints require a JWT Bearer token in the `Authorization` header.

### Register and log in

```text
POST /auth/clients/register
Body: { "email": "...", "password": "...", "name": "..." }

POST /auth/therapists/register
Body: { "email": "...", "password": "...", "name": "...", "licenseNumber": "...", "yearsOfExperience": 5 }

POST /auth/login
Body: { "email": "...", "password": "..." }
```

Login response:

```json
{
  "token": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": "cli_...",
  "userType": "CLIENT"
}
```

Use the token on all subsequent requests:

```text
Authorization: Bearer <token>
```

**Token internals:** JWT signed with HS256. Payload encodes `sub` (userId) and `userType`. The secret is in the `JWT_SECRET` Lambda environment variable (set in `TherapyApiStack.java`). Rotate to AWS Secrets Manager in production.

---

## DynamoDB Tables

| Table | PK | SK | Key GSIs |
| --- | --- | --- | --- |
| `TherapyUserTable` | `userId` | — | GSI_EmailIndex, GSI_UserTypeIndex |
| `TherapySessionTable` | `sessionId` | — | GSI_TherapistSessions, GSI_StatusSchedule |
| `TherapyMappingTable` | `mappingId` | — | GSI_ClientMappings, GSI_TherapistMappings, GSI_ClientTherapistLookup |
| `TherapyMessageTable` | `threadKey` | `messageSk` | GSI_MessageById |
| `TherapyRelationshipTable` | `clientId` | `therapistId` | GSI_TherapistRelationships |
| `TherapyAppointmentTable` | `appointmentId` | — | GSI_ClientAppointments, GSI_TherapistAppointments, GSI_SessionAppointments, GSI_ClientSessionDedup |

See [db_design/](../db_design/) for the full schema, index projections, and access patterns.

---

## Design Decisions and Assumptions

1. **One Lambda per API** — 23 separate Lambda functions (3 auth + 5 sessions + 6 mappings + 4 messages + 5 appointments). Each handler class is kept under ~200 lines and has a single responsibility.

2. **SpecRestApi + OpenApiSpecProcessor** — `OpenApiSpecProcessor` reads the OpenAPI YAML at CDK synth time, injects `x-amazon-apigateway-integration` blocks for each implemented route, and fills unimplemented routes with 501 mock integrations. The resulting map is passed to `SpecRestApi.ApiDefinition.fromInline()`, making the spec the authoritative source of truth for API Gateway.

3. **JWT secret in env var** — acceptable for assignment scope. In production, store in AWS Secrets Manager and fetch on cold start.

4. **DynamoDB PAY_PER_REQUEST** — no capacity planning required for a demo workload.

5. **Pagination** — simplified to `page`/`pageSize` query parameters. The current implementation always queries from the start (`exclusiveStartKey = null`). True cursor-based pagination would expose a `nextToken` derived from DynamoDB's `LastEvaluatedKey`.

6. **Password hashing** — BCrypt via `org.mindrot:jbcrypt`. The `passwordHash` attribute is never included in any API response.

7. **Email uniqueness** — enforced at registration via a `GSI_EmailIndex` query before the `PutItem`. Both client and therapist conflicts return the same 409 response to avoid leaking account types.

8. **RelationshipTable sync** — a denormalised read model kept in sync whenever mapping status or journal access changes. Sync failures are logged but do not fail the primary request.

9. **Messages — no mapping required** — any authenticated user can send a message to another user. A `RelationshipTable` row is upserted on first message to power the relationship view.

10. **Session notes access** — `privateNotes` is always stripped from client responses. `sharedNotes` is only returned to clients once the session status is `IN_PROGRESS` or `COMPLETED`.

11. **Session list filters** — clients always see only `isAvailable=true` sessions (hardcoded); therapists see only their own sessions with an optional `isAvailable` filter.

12. **Session visibility for clients** — `GET /sessions/{sessionId}` allows a client to view a session if `isAvailable=true` OR if the client has a non-cancelled appointment for that session (checked via `GSI_ClientSessionDedup`).

13. **Appointment confirmation** — confirming an appointment (PENDING → CONFIRMED) batch-rejects all other PENDING appointments for the same session and marks the session `isAvailable=false`. These writes are sequential rather than transactional for assignment simplicity; a production system would use `TransactWriteItems`. The `pendingCount` counter on `SessionTable` is updated atomically with DynamoDB `ADD` expressions.

14. **Therapist list appointments** — therapists must supply a `sessionId` query parameter and may only list appointments for sessions they own. This scopes the query to `GSI_SessionAppointments`, which is more efficient than scanning all therapist appointments.

15. **Delete guardrails** — appointment deletion is restricted to `PENDING` and `REJECTED` states; session deletion is restricted to `SCHEDULED` state.

---

## API Testing

Import [../postman/therapy_journalling_api.postman_collection.json](../postman/therapy_journalling_api.postman_collection.json) into Postman for ready-to-use requests covering all implemented endpoints and authentication flows.

---

## Further Reading

- [../swagger/README.md](../swagger/README.md) — API documentation and OpenAPI spec
- [../db_design/README.md](../db_design/README.md) — DynamoDB schema and access patterns
- [../README.md](../README.md) — root project overview
