# Database Design

DynamoDB schema for the Therapy Journalling API. There are two ways to view it:

## Option 1 — Read the schema file

Open [schema.txt](schema.txt) directly. It is a plain-text file that documents every table, its primary key, all attributes, GSIs/LSIs, and the access patterns each index serves.

## Option 2 — Visual UI (recommended)

A hosted page renders the schema as a structured, easy-to-read UI:

**[https://therapy-api-db-design.vercel.app](https://therapy-api-db-design.vercel.app)**

It reads `schema.txt` and displays each table with its keys, indexes, projections, and access patterns laid out visually — much easier to navigate than the raw text file.

## Tables at a glance

| Table | Purpose |
| --- | --- |
| `TherapyUserTable` | Clients and therapists (single table, prefixed IDs) |
| `TherapySessionTable` | Therapy sessions created by therapists |
| `TherapyMappingTable` | Therapist–client mapping requests and journal access state |
| `TherapyMessageTable` | Messages between a client–therapist pair (thread model) |
| `TherapyRelationshipTable` | Denormalised relationship view for quick party lookups |
| `TherapyJournalTable` | Client emotion journal entries |
| `TherapyAppointmentTable` | Appointment bookings against sessions |