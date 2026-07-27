# AI PM Backend

## Development Environment

- Java 21
- Spring Boot 3.5.16
- Gradle 8.x
- MySQL 8.0
- Docker

## EC2 Operations

Deployment and operations guidance lives in:

- `docs/EC2_DEPLOYMENT.md`
- `docs/OPERATIONS.md`

Use `.env.example` as the shape for environment variables, but keep real secrets only on the server.

## Document Relay Flow

This repository now includes a document relay path for testing the upload flow:

```text
React frontend
-> Spring Boot backend
-> FastAPI AI Server
-> Spring Boot backend
-> React frontend
```

The relay endpoint is:

```http
POST /api/projects/{projectId}/documents/extract
Content-Type: multipart/form-data
```

Request field:

```text
files
```

### Configuration

```yaml
ai-server:
  base-url: ${AI_SERVER_BASE_URL:http://localhost:8000}
  document-extract-path: /api/v1/planning/documents/extract
  connect-timeout-seconds: 5
  response-timeout-seconds: 120
```

For the actual new-project creation flow, the backend uses the planning-agent configuration below, not `ai-server.*`:

```yaml
agent:
  planning:
    base-url: ${PLANNING_AGENT_BASE_URL:http://localhost:8000}
    extract-path: ${PLANNING_AGENT_EXTRACT_PATH:/api/v1/planning/documents/extract}
```

If you want to call the deployed document-extract API, set `PLANNING_AGENT_BASE_URL` to that server's base URL and keep the path as `/api/v1/planning/documents/extract`.

### Manual Test Steps

1. Start the FastAPI AI server.

```powershell
cd ..\ai-server
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload
```

2. Confirm the AI server health endpoint.

```powershell
curl.exe http://localhost:8000/health
```

3. Start the Spring Boot backend.

```powershell
set AI_SERVER_BASE_URL=http://localhost:8000
.\gradlew.bat bootRun
```

4. Relay one file through the backend.

```powershell
curl.exe -X POST "http://localhost:8080/api/projects/1/documents/extract" `
  -H "Authorization: Bearer <access-token>" `
  -F "files=@C:\test-files\project-rfp.pdf"
```

5. Relay multiple files through the backend.

```powershell
curl.exe -X POST "http://localhost:8080/api/projects/1/documents/extract" `
  -H "Authorization: Bearer <access-token>" `
  -F "files=@C:\test-files\project-rfp.pdf" `
  -F "files=@C:\test-files\project-proposal.docx"
```

### Frontend Check

Run the Vite dev server:

```powershell
npm run dev
```

Open the PM upload screen, choose files, click `Upload`, and inspect the browser Network tab.

### Automated Tests

```powershell
.\gradlew.bat test
```

### Test File Layout Example

```text
test-files/
├── project-rfp.pdf
├── project-proposal.docx
├── sample.txt
└── unsupported.exe
```
