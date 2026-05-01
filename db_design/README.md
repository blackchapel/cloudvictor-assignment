# Database Design — DynamoDB Schema

DynamoDB schema for the Therapy Journalling API. All tables use PAY_PER_REQUEST billing. Primary keys are natural identifiers — no artificial prefixes on single-entity tables.

---

## How to View the Schema

### Option 1 — Read the schema file directly

Open [schema.txt](schema.txt). It documents every table, its primary key, all attributes, GSIs/LSIs, and the access patterns each index serves. Each table block follows this format:

```text
TABLE <name>
DESCRIPTION  purpose and design rationale
ATTRIBUTES   name | type | role | notes
KEYS         HASH + RANGE definition
GSI / LSI    index name, keys, projection, description
ACCESS_PATTERNS  one block per API endpoint that uses this table
```

### Option 2 — Visual UI (recommended)

A hosted page renders the schema as a structured, easy-to-read UI:

**<https://therapy-api-db-design.vercel.app>**

It reads `schema.txt` and displays each table with its keys, indexes, projections, and access patterns laid out visually — much easier to navigate than the raw text file.

---

## Tables at a Glance

| Table | PK | SK | Purpose |
| --- | --- | --- | --- |
| `TherapyUserTable` | `userId` | — | Clients and therapists in a single table. Prefixed IDs (`cli_`, `thr_`) distinguish types. |
| `TherapySessionTable` | `sessionId` | — | Therapy sessions created by therapists. Includes scheduling, availability, and notes. |
| `TherapyMappingTable` | `mappingId` | — | Therapist–client relationship requests and journal access state machine. |
| `TherapyMessageTable` | `threadKey` | `messageSk` | Messages within a client–therapist thread. Co-located by `THREAD#{clientId}#{therapistId}`. |
| `TherapyRelationshipTable` | `clientId` | `therapistId` | Denormalised read model. Powers "who is connected to whom" queries without cross-table joins. |
| `TherapyJournalTable` | `clientId` | `journalEntryId` | Client emotion entries. Metadata item (`journalEntryId=JOURNAL`) co-located with entry items on the same partition. |
| `TherapyAppointmentTable` | `appointmentId` | — | Appointment bookings against session slots. Four GSIs cover all list and dedup access patterns. |

---

## Index Summary

### TherapyUserTable

| Index | Hash | Range | Purpose |
| --- | --- | --- | --- |
| GSI_EmailIndex | `email` | `userType` | O(1) login lookup; enforces email uniqueness at write time |
| GSI_UserTypeIndex | `userType` | `yearsOfExperience` | Therapist discovery with optional experience range filter |

### TherapySessionTable

| Index | Hash | Range | Purpose |
| --- | --- | --- | --- |
| GSI_TherapistSessions | `therapistId` | `scheduledAt` | Paginated list of a therapist's own sessions, sorted chronologically |
| GSI_StatusSchedule | `status` | `scheduledAt` | Client-facing session browser — filters to `SCHEDULED` + `isAvailable=true` |

### TherapyMappingTable

| Index | Hash | Range | Purpose |
| --- | --- | --- | --- |
| GSI_ClientMappings | `clientId` | `createdAt` | All mappings for a client, newest first |
| GSI_TherapistMappings | `therapistId` | `createdAt` | All mappings for a therapist, newest first |
| GSI_ClientTherapistLookup | `clientId` | `therapistId` | KEYS_ONLY — O(1) duplicate check on mapping creation |

### TherapyMessageTable

| Index | Hash | Range | Purpose |
| --- | --- | --- | --- |
| GSI_MessageById | `messageId` | `sentAt` | O(1) single-message lookup for PUT and DELETE by messageId |

### TherapyJournalTable

| Index | Hash | Range | Purpose |
| --- | --- | --- | --- |
| LSI_EmotionTime | `clientId` | `emotionTimestamp` | Time-ordered entry listing per client |
| LSI_IntensityTime | `clientId` | `intensityTime` | Intensity + date range queries (`intensityTime = intensity#emotionTimestamp`) |
| GSI_EmotionEntries | `clientEmotion` | `emotionTimestamp` | Emotion-category filter; `clientEmotion = clientId#emotion` scopes partition to one client+emotion |

### TherapyAppointmentTable

| Index | Hash | Range | Purpose |
| --- | --- | --- | --- |
| GSI_ClientAppointments | `clientId` | `requestedAt` | Client's own appointments, newest first |
| GSI_TherapistAppointments | `therapistId` | `requestedAt` | All appointments across a therapist's sessions |
| GSI_SessionAppointments | `sessionId` | `status` | All appointments for a session; enables PENDING-only query for confirm flow |
| GSI_ClientSessionDedup | `clientSession` | `status` | KEYS_ONLY — O(1) duplicate prevention; `clientSession = clientId#sessionId` |

---

## Design Principles

- **Access-pattern-first** — every index exists to serve a specific API query. No index was added speculatively.
- **Natural primary keys** — `userId`, `sessionId`, `mappingId`, `appointmentId` are natural identifiers. Composite or prefixed keys are used only where semantically justified (e.g. `THREAD#{clientId}#{therapistId}`, `clientId#emotion`).
- **Sort keys only where needed** — range queries (`messageSk`), multi-item-type partitions (`journalEntryId`), and meaningful composite keys (`therapistId` in `RelationshipTable`). Constant SKs like `METADATA` have been avoided.
- **Sparse GSIs** — therapist-only attributes (`yearsOfExperience`, `licenseNumber`) are absent on CLIENT items, keeping the `GSI_UserTypeIndex` partition lean.
- **KEYS_ONLY projections** on dedup indexes (`GSI_ClientTherapistLookup`, `GSI_ClientSessionDedup`) — only existence matters, minimising RCU cost.
- **Atomic counters** — `totalEntries` (JournalTable) and `pendingCount` (SessionTable) are maintained with DynamoDB `ADD` expressions, never read-modify-write.
