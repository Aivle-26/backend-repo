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
`prod` 프로필은 서울 리전 AWS SES SMTP(`email-smtp.ap-northeast-2.amazonaws.com:587`)를 기본 전송 경로로 사용한다.
개인 Gmail 계정은 운영 기본값으로 사용되지 않는다. SMTP 인증과 STARTTLS가 기본 활성화되며, 자격증명이나 발신 주소가 빠지면 애플리케이션 시작 시 누락된 환경변수 이름이 로그에 표시된다.

필수 환경변수는 `.env.example`의 `MAIL_*` 항목을 따른다. `MAIL_USERNAME`과 `MAIL_PASSWORD`에는 AWS 액세스 키가 아니라 SES에서 별도로 발급한 SMTP 자격증명을 넣는다. 실제 자격증명과 비밀번호는 저장소에 커밋하지 않는다.

```properties
MAIL_ENABLED=true
MAIL_HOST=email-smtp.ap-northeast-2.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=<SES_SMTP_USERNAME>
MAIL_PASSWORD=<SES_SMTP_PASSWORD>
MAIL_SMTP_AUTH=true
MAIL_STARTTLS_ENABLE=true
MAIL_STARTTLS_REQUIRED=true
MAIL_FROM_ADDRESS=no-reply@your-service-domain.example
MAIL_FROM_NAME=BidWorks AI
MAIL_REPLY_TO=support@your-service-domain.example
MAIL_SUBJECT_PREFIX=[BidWorks AI]
```

AWS SES와 서비스 도메인 DNS에서 별도로 완료해야 하는 항목:

- `MAIL_FROM_ADDRESS`에 사용할 발신 주소 또는 발신 도메인 검증
- SES SMTP 자격증명 발급
- SES 샌드박스에서는 검증된 수신자에게만 발송 가능
- 운영 사용 전 SES 프로덕션 액세스 요청
- SES가 안내하는 SPF 레코드 등록
- SES가 안내하는 DKIM 레코드 등록
- 서비스 도메인의 DMARC 정책 등록
- 표시 From 도메인과 SPF/DKIM 인증 도메인 정렬
- 반송 및 스팸 신고 모니터링 설정

`local` 프로필은 메일 기능이 기본 비활성화되어 있다. 로컬 Gmail SMTP 테스트가 필요할 때만 `MAIL_ENABLED=true`, Gmail 주소인 `MAIL_USERNAME`/`MAIL_FROM_ADDRESS`, Google 앱 비밀번호인 `MAIL_PASSWORD`를 설정한다. `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, SMTP 인증과 STARTTLS는 로컬 프로필 기본값으로 제공되며 운영 발송에는 사용하지 않는다.

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
