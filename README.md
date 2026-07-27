# AI PM 백엔드

## 개발 환경

- Java 21
- Spring Boot 3.5.16
- Gradle 8.x
- MySQL 8.0
- Docker

## 로컬 테스트 환경

`local` 프로필로 백엔드를 실행하면 메모리 H2 DB와 로컬 JWT 시크릿을 사용해 빠르게 테스트할 수 있습니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

애플리케이션 실행 후 아래 경로로 접속할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- H2 콘솔: `http://localhost:8080/h2-console`
- 인증 테스트 페이지: `http://localhost:8080/test-auth.html`

권장 로컬 검증 순서는 아래와 같습니다.

1. `test-auth.html`에서 로컬 PM 또는 STAFF 계정으로 로그인해 액세스 토큰을 발급받습니다.
2. Swagger UI에서 `Authorize`를 클릭합니다.
3. 테스트 페이지에서 복사한 JWT 값만 입력합니다. `Bearer` 접두사는 Swagger가 자동으로 추가합니다.
4. Swagger에서 프로젝트 및 WBS API를 호출합니다.

WBS 생성까지 확인하려면 AI 서버도 로컬에서 함께 실행해야 합니다.

```powershell
cd ..\ai-server
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload
```

그다음 백엔드를 실행합니다.

```powershell
cd ..\backend-repo
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

WBS 관련 Swagger 테스트 순서는 아래와 같습니다.

1. `POST /api/projects/{projectId}/wbs/generate`
2. `GET /api/projects/{projectId}/wbs`
3. `PUT /api/projects/{projectId}/wbs/final`
4. `GET /api/projects/{projectId}/wbs`

## EC2 운영

배포 및 운영 방법은 다음 문서에서 확인할 수 있습니다.

- `docs/EC2_DEPLOYMENT.md`
- `docs/OPERATIONS.md`

환경 변수 형식은 `.env.example`을 참고하되, 실제 비밀값은 서버에만 보관해야 합니다.
