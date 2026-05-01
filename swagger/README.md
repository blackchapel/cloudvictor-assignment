# Therapy Journalling API — OpenAPI Spec and Swagger UI

Interactive documentation for the Therapy Journalling backend, defined with OpenAPI 3.0 and served via Swagger UI.

---

## What Is in This Directory

| File | Purpose |
| --- | --- |
| `therapy-api.yaml` | OpenAPI 3.0 spec — single source of truth for all API contracts |
| `index.html` | Swagger UI entry point (works locally and on Vercel) |
| `README.md` | This file |

The YAML spec also drives the CDK infrastructure: `OpenApiSpecProcessor` reads it at synth time and injects `x-amazon-apigateway-integration` blocks for every implemented route before passing it to API Gateway. Any path in the spec that does not have a Lambda integration gets a 501 mock integration automatically, so the gateway always deploys cleanly.

---

## How to View the API Docs

### Option 1 — Live deployment (recommended)

The spec is publicly hosted on Vercel. No setup required.

**<https://therapy-api-swaggerui.vercel.app>**

Open the link in any browser. The Swagger UI loads instantly with the latest version of the spec.

---

### Option 2 — Open locally in a browser

No prerequisites needed — just a browser.

1. Clone or download this repository.
2. Navigate to the `swagger/` folder.
3. Open `index.html` directly in your browser.

Both `index.html` and `therapy-api.yaml` must be in the same directory, as the HTML file loads the spec from the relative path `./therapy-api.yaml`.

---

### Option 3 — Swagger Editor online

No installation required. Paste the spec directly into the official editor.

1. Open <https://editor.swagger.io> in your browser.
2. Delete the default content in the left panel.
3. Open `therapy-api.yaml`, copy its entire contents, and paste into the left panel.
4. The rendered Swagger UI appears instantly on the right.

The Swagger Editor also validates the spec in real time and highlights syntax or schema errors in the left panel.

---

### Option 4 — Docker

Serves the Swagger UI via a local web server on port `8080`.

**Prerequisite:** [Docker](https://docs.docker.com/get-docker/) installed and running.

**macOS / Linux:**

```bash
docker run -p 8080:8080 \
  -e SWAGGER_JSON=/tmp/therapy-api.yaml \
  -v $(pwd)/swagger/therapy-api.yaml:/tmp/therapy-api.yaml \
  swaggerapi/swagger-ui
```

**Windows (PowerShell):**

```powershell
docker run -p 8080:8080 `
  -e SWAGGER_JSON=/tmp/therapy-api.yaml `
  -v ${PWD}/swagger/therapy-api.yaml:/tmp/therapy-api.yaml `
  swaggerapi/swagger-ui
```

Then open <http://localhost:8080> in your browser. Press `Ctrl + C` to stop the container.

---

## API Overview

The spec covers the following resource groups:

| Tag | Routes | Status |
| --- | --- | --- |
| Auth | POST /auth/clients/register, POST /auth/therapists/register, POST /auth/login | Implemented |
| Sessions | CRUD + List `/sessions` | Implemented |
| Mappings | CRUD + mapping-status + journal-access `/mappings` | Implemented |
| Messages | Send, List, Update, Delete `/messages` | Implemented |
| Appointments | Create, List, Get, Patch, Delete `/appointments` | Implemented |
| Clients | CRUD `/clients` | Spec only (501) |
| Therapists | CRUD `/therapists` | Spec only (501) |
| Journal | CRUD `/clients/{id}/journal/entries` | Spec only (501) |
| Search | GET `/search` | Spec only (501) |

Spec-only routes return `501 Not Implemented` via API Gateway mock integrations and are fully documented with request/response schemas.

---

## Resources

- [OpenAPI 3.0 Specification](https://swagger.io/specification/)
- [Swagger UI Documentation](https://swagger.io/tools/swagger-ui/)
- [Emotion-Feelings Vocabulary Reference](https://tomdrummond.com/wp-content/uploads/2019/11/Emotion-Feelings.pdf)
