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

## Production Transactional Email

회원가입 및 비밀번호 재설정 인증 메일은 Spring `JavaMailSender`를 통해 SMTP로 전송한다.
운영 환경에서는 개인 Gmail 대신 AWS SES, SendGrid, Mailgun 등 트랜잭션 메일 공급자의 SMTP 자격 증명을 사용한다.
`prod` 프로필은 메일 기능을 기본 활성화하고 SMTP 인증과 STARTTLS를 요구하므로 필수 설정이 빠지면 애플리케이션 시작이 실패한다.

필수 환경변수는 `.env.example`의 `MAIL_*` 항목을 따른다. 실제 SMTP 사용자명과 비밀번호는 저장소에 커밋하지 않는다.

```properties
MAIL_ENABLED=true
MAIL_HOST=email-smtp.ap-northeast-2.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=<provider-smtp-username>
MAIL_PASSWORD=<provider-smtp-password>
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLE=true
MAIL_STARTTLS_REQUIRED=true
MAIL_FROM_ADDRESS=no-reply@pmagent.co.kr
MAIL_FROM_NAME=PM Agent
MAIL_REPLY_TO=support@pmagent.co.kr
```

메일 공급자와 DNS에서 별도로 완료해야 하는 항목:

- `MAIL_FROM_ADDRESS` 도메인의 발신자 또는 도메인 소유권 인증
- 공급자가 제공하는 SPF 레코드 등록
- 공급자가 제공하는 DKIM 레코드 등록
- `_dmarc.pmagent.co.kr` DMARC 정책 등록
- 표시 From 도메인과 SPF/DKIM 인증 도메인 정렬
- AWS SES를 사용할 경우 프로덕션 액세스 승인 및 샌드박스 해제
- 반송 및 스팸 신고 모니터링 설정

로컬 Gmail SMTP 테스트에서는 `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`과 Google 앱 비밀번호를 사용할 수 있지만 운영 발송에는 사용하지 않는다.

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
