
# 🧠 Therapy Journalling API

Welcome to the Therapy Journalling API — a modern, serverless backend for mental health journalling and therapy management. This project enables clients to track their emotions, therapists to manage sessions and notes, and both to communicate securely, all via robust APIs.

---

## 🚀 Project Overview

**Features:**
- Secure registration and login for clients and therapists
- Emotion journalling for clients
- Therapist–client mapping and journal access control
- Session scheduling, booking, and management
- Secure messaging between clients and therapists
- DynamoDB-backed, serverless, and scalable
- Fully documented with Swagger (OpenAPI 3.0)

**Tech Stack:**
- Java 11+, AWS Lambda, API Gateway, DynamoDB, AWS CDK

---

## 📝 How to Use

1. **Explore the API:**
	 - Use the [Swagger UI](swagger/index.html) or [hosted docs](https://therapy-api-swaggerui.vercel.app) for live, interactive API exploration.
2. **Review the Database Schema:**
	 - See [db_design/schema.txt](db_design/schema.txt) or the [visual schema explorer](https://therapy-api-db-design.vercel.app).
3. **Run the Backend:**
	 - See [api/README.md](api/README.md) for build, deploy, and authentication instructions.
4. **Test with Postman:**
	 - Import [therapy_journalling_api.postman_collection.json](postman/therapy_journalling_api.postman_collection.json) into Postman for ready-to-use API calls.