# Therapy Journalling API — Swagger UI

Interactive API documentation for the Therapy Journalling backend, built with OpenAPI 3.0.

---

## Accessing the API Docs

There are three ways to view and interact with the Swagger UI.

---

### 1. Live Deployment *(Recommended)*

The API docs are publicly hosted on Vercel. No setup required.

🔗 **https://therapy-api-swaggerui.vercel.app**

Open the link in any browser. The Swagger UI loads instantly with the latest version of the spec.

---

### 2. Local - Open in Browser

For offline access or local development.

**Prerequisites:** None. Just a browser.

**Steps:**

1. Clone or download this repository
2. Navigate to the project folder
3. Open `index.html` directly in your browser

> **Note:** When running locally via the file system, the YAML path is resolved
> relatively (`./therapy-api.yaml`). Both `index.html` and `therapy-api.yaml`
> must be in the same directory.

---

### 3. Docker

Serves the Swagger UI via a local web server on port `8080`.

**Prerequisites:** [Docker](https://docs.docker.com/get-docker/) installed and running.

**Steps:**

1. Clone or download this repository
2. Run the following command from the project root, replacing the path with the
   absolute path to your `therapy-api.yaml` file:

```bash
docker run -p 8080:8080 \
  -e SWAGGER_JSON=/tmp/therapy-api.yaml \
  -v /absolute/path/to/therapy-api.yaml:/tmp/therapy-api.yaml \
  swaggerapi/swagger-ui
```

3. Open your browser and navigate to:

```
http://localhost:8080
```

**Example on macOS/Linux** (if the file is in your current directory):

```bash
docker run -p 8080:8080 \
  -e SWAGGER_JSON=/tmp/therapy-api.yaml \
  -v $(pwd)/swagger/therapy-api.yaml:/tmp/therapy-api.yaml \
  swaggerapi/swagger-ui
```

**Example on Windows (PowerShell)**:

```powershell
docker run -p 8080:8080 `
  -e SWAGGER_JSON=/tmp/therapy-api.yaml `
  -v ${PWD}/swagger/therapy-api.yaml:/tmp/therapy-api.yaml `
  swaggerapi/swagger-ui
```

> To stop the container, press `Ctrl + C` in the terminal.

---

## Project Structure

```
.
├── index.html          # Swagger UI entry point
├── therapy-api.yaml    # OpenAPI 3.0 specification
└── README.md           # This file
```

---

## Resources

- [OpenAPI 3.0 Specification](https://swagger.io/specification/)
- [Swagger UI Documentation](https://swagger.io/tools/swagger-ui/)
- [Emotion-Feelings Vocabulary Reference](https://tomdrummond.com/wp-content/uploads/2019/11/Emotion-Feelings.pdf)