# Therapy Journalling API

A serverless backend for mental health journalling and therapy management. Clients track emotions in a private journal, book sessions with therapists, and communicate securely. Therapists manage sessions, view authorised journals, and stay connected with their clients.

---

## Table of Contents

- [Business Problem](#business-problem)
- [What Is Implemented](#what-is-implemented)
- [Architecture](#architecture)
- [Repository Layout](#repository-layout)
- [Quick Start](#quick-start)
- [Resources](#resources)

---

## Business Problem

The pandemic increased social isolation and drove demand for remote therapy. This platform addresses three specific pain points:

1. **Clients** lose emotional context between sessions — this app lets them log emotions as they happen, with timestamps, intensity, and free-text notes.
2. **Clients** cannot easily switch or share records across therapists — journal access is granted per therapist and can be revoked at any time.
3. **Therapists** manage growing caseloads — structured sessions with private and shared notes, appointment tracking, and secure messaging keep everything organised.

---

## What Is Implemented

| Domain | Endpoints | Notes |
| --- | --- | --- |
| **Auth** | Register (client & therapist), Login | JWT HS256, BCrypt passwords |
| **Sessions** | CRUD + List | Therapist-owned; clients browse and book |
| **Mappings** | Full lifecycle + journal access control | Therapist–client relationship state machine |
| **Messages** | Send, List, Edit, Delete | Thread model; sender-only edits within 10 min |
| **Appointments** | Request, List, Get, Patch status, Delete | PENDING → CONFIRMED / REJECTED / CANCELLED / COMPLETED |

Unimplemented routes (Journals, Search, User profiles, etc.) are present in the OpenAPI spec and return `501 Not Implemented` via API Gateway mock integrations — so the spec-driven gateway deploys cleanly.

---

## Architecture

```text
Client / Therapist
       │
       ▼
  API Gateway  ──── OpenAPI spec (SpecRestApi)
  (REST, prod)       x-amazon-apigateway-integration injected at CDK synth time
       │
       ▼
  AWS Lambda          one function per API operation (23 total)
  (Java 11, 512 MB)   static DynamoDB client reused across warm invocations
       │
       ▼
  DynamoDB            6 tables, PAY_PER_REQUEST billing
                      GSIs/LSIs designed around access patterns
```

**Key design choices:**

- **One Lambda per operation** keeps cold-start surface small and each handler under 200 lines.
- **SpecRestApi** — the CDK stack reads the OpenAPI YAML, injects `x-amazon-apigateway-integration` blocks at synth time, and passes the result to API Gateway. No manual route wiring.
- **Repository pattern** — each table has a dedicated repository class; handlers never touch DynamoDB directly.
- **PAY_PER_REQUEST** billing — no capacity planning needed for demo/assignment scale.

---

## Repository Layout

```text
.
├── README.md                    ← you are here
├── api/                         ← Lambda application code + CDK infrastructure
│   ├── README.md                ← build, deploy, auth, and design decisions
│   ├── application/             ← handler, model, repository, util packages
│   └── infrastructure/          ← CDK stack and constructs
├── swagger/                     ← OpenAPI 3.0 specification + Swagger UI
│   ├── README.md                ← how to view the API docs
│   ├── therapy-api.yaml         ← single source of truth for all API contracts
│   └── index.html               ← local Swagger UI
├── db_design/                   ← DynamoDB schema documentation
│   ├── README.md                ← how to read the schema
│   └── schema.txt               ← tables, keys, GSIs/LSIs, access patterns
└── postman/
    └── therapy_journalling_api.postman_collection.json
```

---

## Quick Start

```bash
# 1. Build the Lambda fat JAR
cd api
mvn package -pl application -DskipTests

# 2. Bootstrap CDK (first deploy only)
cd infrastructure
cdk bootstrap

# 3. Deploy to AWS
cdk deploy
```

CDK outputs the base URL on completion:

```text
TherapyApiStack.TherapyRestApiEndpoint = https://<id>.execute-api.<region>.amazonaws.com/prod/
```

See [api/README.md](api/README.md) for detailed build, deploy, and authentication instructions.

---

## Resources

| Resource | Link |
| --- | --- |
| **Live API Base URL** | <https://mpcqc90o29.execute-api.ap-south-1.amazonaws.com/prod> |
| API Docs (live) | <https://therapy-api-swaggerui.vercel.app> |
| DB Schema (visual) | <https://therapy-api-db-design.vercel.app> |
| OpenAPI Spec | [swagger/therapy-api.yaml](swagger/therapy-api.yaml) |
| Postman Collection | [postman/](postman/) |
| Backend README | [api/README.md](api/README.md) |
| Schema README | [db_design/README.md](db_design/README.md) |
