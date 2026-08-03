# Postman API 테스트 가이드

현재 백엔드 구현을 실제 사용 흐름 순서로 정리한 문서다. 예시의 ID와 토큰은 고정값이 아니라 앞 요청의 응답값을 사용한다.

## 0. 실행 및 공통 설정

로컬 서버:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

Postman 변수:

| 변수 | 초기값 | 설명 |
|---|---:|---|
| `baseUrl` | `http://localhost:8080` | 백엔드 주소 |
| `accessToken` | 로그인 응답값 | Bearer 토큰 |
| `refreshToken` | 로그인 응답값 | 토큰 갱신/로그아웃 |
| `projectId` | 생성 응답값 | 테스트 프로젝트 |
| `documentId` | 문서 목록 응답값 | 업로드 문서 |
| `analysisResultId` | 분석 결과 저장 응답값 | 문서 분석 결과 |
| `requirementId` | 요구사항 목록 응답값 | 요구사항 |
| `wbsTaskId` | WBS 조회 응답값 | 일정의 WBS FK |
| `channelId` | Slack 채널 ID | 예: `C0123456789` |

인증이 필요한 요청의 공통 헤더:

```http
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

로컬 시드 계정:

| 역할 | 이메일 | 비밀번호 |
|---|---|---|
| PM | `pm@local.test` | `local1234!` |
| STAFF | `staff@local.test` | `local1234!` |

PM만 가능한 API는 프로젝트 생성·삭제, 문서 처리, 요구사항, WBS, 일정, 산출물 상태 API다. 프로젝트 목록은 PM과 STAFF 모두 가능하다.

## 1. 인증

### 1-1. 로그인

```http
POST {{baseUrl}}/api/users/login
```

입력:

```json
{
  "email": "pm@local.test",
  "password": "local1234!",
  "role": "PM"
}
```

출력 `200 OK`:

```json
{
  "success": true,
  "message": "login success",
  "employeeNumber": "PM-0001",
  "name": "로컬 PM",
  "role": "PM",
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "nZ5j...생략",
  "accessTokenExpiresAt": 1785391200000,
  "absoluteExpiresAt": 1785418200000,
  "serverTime": 1785389400000
}
```

Postman Tests:

```javascript
const body = pm.response.json();
pm.collectionVariables.set("accessToken", body.accessToken);
pm.collectionVariables.set("refreshToken", body.refreshToken);
```

### 1-2. 현재 세션/내 정보

```http
GET {{baseUrl}}/api/users/session
GET {{baseUrl}}/api/users/me
```

입력 body 없음. 두 API의 응답 스키마는 같다.

```json
{
  "authenticated": true,
  "employeeNumber": "PM-0001",
  "name": "로컬 PM",
  "role": "PM",
  "accessToken": null,
  "refreshToken": null,
  "accessTokenExpiresAt": 1785391200000,
  "absoluteExpiresAt": 1785418200000,
  "serverTime": 1785389400000
}
```

### 1-3. Access Token 갱신

```http
POST {{baseUrl}}/api/users/refresh
```

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

출력은 `AuthSessionResponse`이며, 새 `accessToken`과 `refreshToken`을 다시 저장한다.

### 1-4. 로그아웃

```http
POST {{baseUrl}}/api/users/logout
```

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

```json
{
  "message": "logged out"
}
```

### 1-5. 회원가입 이메일 인증 흐름

로컬 기본 설정은 `MAIL_ENABLED=false`라 실제 실행 시 `503 MAIL_NOT_CONFIGURED`가 정상이다. SMTP 설정 후 테스트한다.

회원가입 시작:

```http
POST {{baseUrl}}/api/users/signup
```

```json
{
  "employeeNumber": "PM-0100",
  "name": "김프로",
  "email": "pm0100@example.com",
  "password": "test1234!",
  "role": "PM"
}
```

```json
{
  "success": true,
  "verificationRequired": true,
  "message": "verification code sent",
  "expiresIn": 300
}
```

회원가입 인증:

```http
POST {{baseUrl}}/api/users/signup/verify
```

```json
{
  "email": "pm0100@example.com",
  "verificationCode": "123456"
}
```

```json
{
  "employeeNumber": "PM-0100",
  "name": "김프로",
  "email": "pm0100@example.com",
  "role": "PM",
  "status": "ACTIVE"
}
```

로그인 인증번호 재전송과 검증:

```http
POST {{baseUrl}}/api/users/login/resend
```

```json
{
  "email": "pm0100@example.com"
}
```

```http
POST {{baseUrl}}/api/users/login/verify
```

```json
{
  "email": "pm0100@example.com",
  "verificationCode": "123456"
}
```

검증 성공 출력은 로그인 출력과 동일하다.

### 1-6. 비밀번호 재설정

인증번호 발송:

```http
POST {{baseUrl}}/api/users/password/email-send
```

```json
{
  "employeeNumber": "PM-0001",
  "email": "pm@local.test"
}
```

```json
{
  "message": "verification code sent"
}
```

인증번호 확인:

```http
POST {{baseUrl}}/api/users/password/email-check
```

```json
{
  "employeeNumber": "PM-0001",
  "email": "pm@local.test",
  "code": "123456"
}
```

```json
{
  "resetToken": "reset-token-value",
  "message": "email verification success"
}
```

비밀번호 변경:

```http
PATCH {{baseUrl}}/api/users/password
```

```json
{
  "employeeNumber": "PM-0001",
  "resetToken": "reset-token-value",
  "newPassword": "changed1234!"
}
```

```json
{
  "message": "password changed"
}
```

## 2. 프로젝트 생성과 문서

### 2-1. 프로젝트 목록

```http
GET {{baseUrl}}/api/projects
```

```json
[
  {
    "projectId": 1,
    "name": "로컬 테스트 프로젝트",
    "description": "Slack 커뮤니케이션 리스크 연동 확인용",
    "pmEmployeeNumber": "PM-0001",
    "status": "ACTIVE",
    "plannedStartDate": "2026-06-30",
    "plannedEndDate": "2026-08-29",
    "createdAt": "2026-07-30T15:00:00",
    "updatedAt": "2026-07-30T15:00:00"
  }
]
```

### 2-2. 빈 프로젝트 초안 생성

```http
POST {{baseUrl}}/api/projects/drafts
```

```json
{
  "name": "쇼핑몰 고도화 프로젝트",
  "description": "주문·결제·배송 기능을 개선한다.",
  "pmEmployeeNumber": "PM-0001",
  "plannedStartDate": "2026-08-03",
  "plannedEndDate": "2026-10-30"
}
```

출력 `201 Created`:

```json
{
  "projectId": 2,
  "name": "쇼핑몰 고도화 프로젝트",
  "pmEmployeeNumber": "PM-0001",
  "status": "DRAFT",
  "plannedStartDate": "2026-08-03",
  "plannedEndDate": "2026-10-30"
}
```

`projectId`를 컬렉션 변수로 저장한다.

### 2-3. 프로젝트 문서 업로드

```http
POST {{baseUrl}}/api/projects/{{projectId}}/documents/upload
Content-Type: multipart/form-data
```

Postman Body → `form-data`:

| KEY | TYPE | VALUE |
|---|---|---|
| `files` | File | `requirements.txt` |
| `files` | File | `project-rfp.pdf` |

허용 확장자는 `pdf`, `docx`, `xlsx`, `pptx`, `txt`이며, 파일당 최대 10MB, 요청 전체 최대 50MB다.

출력 `201 Created`:

```json
{
  "projectId": 2,
  "documents": [
    {
      "documentId": 10,
      "originalFileName": "requirements.txt",
      "status": "UPLOADED",
      "fileSize": 2048
    },
    {
      "documentId": 11,
      "originalFileName": "project-rfp.pdf",
      "status": "UPLOADED",
      "fileSize": 48120
    }
  ]
}
```

### 2-4. 프로젝트 문서 목록

```http
GET {{baseUrl}}/api/projects/{{projectId}}/documents
```

출력은 업로드 응답과 같은 구조다. 첫 문서의 `documentId`를 이후 요청에 사용한다.

### 2-5. 문서 기반 프로젝트 생성

빈 초안 생성과 문서 업로드·AI 추출을 한 요청으로 수행하는 별도 진입점이다.

```http
POST {{baseUrl}}/api/projects/drafts/from-documents?enableLlm=true
Content-Type: multipart/form-data
```

Body → `form-data`에 `files`를 1개 이상 추가한다. `pmUserId`는 인증 사용자의 사번을 사용하므로 보통 생략한다.

```json
{
  "projectId": 3,
  "projectName": "문서 기반 쇼핑몰 프로젝트",
  "status": "DRAFT",
  "llmStatus": "SUCCEEDED",
  "requirementCount": 8,
  "requiredArtifactCount": 3,
  "documentCount": 2,
  "message": "project draft created"
}
```

이 API는 Planning Agent가 실행 중이어야 한다.

### 2-6. 저장 문서 AI 추출

```http
POST {{baseUrl}}/api/projects/{{projectId}}/documents/extract
```

body는 없다. 업로드된 저장 파일을 Planning Agent에 전송한다. AI 원본 응답을 그대로 반환하므로 snake_case일 수 있다.

```json
{
  "llm_status": "SUCCEEDED",
  "project_info": {
    "project_name": "쇼핑몰 고도화 프로젝트",
    "project_goal": "주문 전환율 개선"
  },
  "requirement_candidates": [
    {
      "requirement_id": 1,
      "function_name": "주문 생성",
      "requirement_text": "사용자는 장바구니 상품을 주문할 수 있다.",
      "category": "FUNCTIONAL",
      "priority": "HIGH"
    }
  ]
}
```

### 2-7. AI 분석 결과 콜백 저장

외부 AI 서버 없이 이후 흐름을 테스트하려면 이 API에 예시 데이터를 직접 넣는다.

```http
POST {{baseUrl}}/api/projects/{{projectId}}/documents/analysis-results
```

```json
{
  "agentExecutionId": "analysis-postman-001",
  "agentVersion": "document-agent-v1",
  "projectGoal": "주문 전환율과 운영 효율 개선",
  "scope": "주문, 결제, 배송 상태 조회",
  "requirements": [
    {
      "externalReferenceId": 1,
      "type": "FUNCTIONAL",
      "title": "주문 생성",
      "description": "사용자는 장바구니 상품으로 주문을 생성할 수 있다.",
      "priority": "HIGH",
      "sourceDocumentId": {{documentId}}
    },
    {
      "externalReferenceId": 2,
      "type": "SECURITY",
      "title": "결제 정보 보호",
      "description": "결제 관련 민감정보를 로그에 남기지 않는다.",
      "priority": "HIGH",
      "sourceDocumentId": {{documentId}}
    }
  ],
  "deliverables": [
    "요구사항 정의서",
    "WBS"
  ],
  "milestones": [
    {
      "name": "MVP",
      "dueDate": "2026-09-15"
    }
  ],
  "technologyStacks": [
    "Spring Boot",
    "React",
    "MySQL"
  ],
  "constraints": [
    "기존 결제 PG 연동 유지"
  ],
  "risks": [
    "배송사 API 일정 지연"
  ]
}
```

출력 `201 Created`:

```json
{
  "analysisResultId": 20,
  "projectId": 2,
  "agentExecutionId": "analysis-postman-001",
  "requirementsCount": 2
}
```

### 2-8. 저장된 분석 결과 조회

```http
GET {{baseUrl}}/api/projects/{{projectId}}/documents/analysis-results
```

주요 출력:

```json
{
  "project": {
    "projectId": 2,
    "name": "쇼핑몰 고도화 프로젝트",
    "description": "주문·결제·배송 기능을 개선한다.",
    "clientOrganization": null,
    "pmEmployeeNumber": "PM-0001",
    "status": "DRAFT",
    "plannedStartDate": "2026-08-03",
    "plannedEndDate": "2026-10-30",
    "acceptanceConditionsJson": null,
    "budgetContractConditionsJson": null,
    "securityPrivacyConditionsJson": null,
    "createdAt": "2026-07-30T15:10:00",
    "updatedAt": "2026-07-30T15:12:00"
  },
  "documents": [
    {
      "documentId": 10,
      "status": "ANALYZED",
      "originalFileName": "requirements.txt",
      "storedFileName": "uuid.txt",
      "storagePath": "uploads/documents/uuid.txt",
      "extension": "txt",
      "contentType": "text/plain",
      "fileSize": 2048,
      "characterCount": null,
      "fileType": null,
      "processingMode": null,
      "createdAt": "2026-07-30T15:11:00"
    }
  ],
  "analysisResult": {
    "analysisResultId": 20,
    "agentExecutionId": "analysis-postman-001",
    "agentVersion": "document-agent-v1",
    "projectGoal": "주문 전환율과 운영 효율 개선",
    "scope": "주문, 결제, 배송 상태 조회",
    "deliverablesJson": "[\"요구사항 정의서\",\"WBS\"]",
    "milestonesJson": "[{\"name\":\"MVP\",\"dueDate\":\"2026-09-15\"}]",
    "technologyStacksJson": "[\"Spring Boot\",\"React\",\"MySQL\"]",
    "constraintsJson": "[\"기존 결제 PG 연동 유지\"]",
    "risksJson": "[\"배송사 API 일정 지연\"]",
    "createdAt": "2026-07-30T15:12:00"
  },
  "requirements": [
    {
      "requirementId": 30,
      "analysisResultId": 20,
      "sourceDocumentId": 10,
      "externalReferenceId": 1,
      "type": "FUNCTIONAL",
      "title": "주문 생성",
      "description": "사용자는 장바구니 상품으로 주문을 생성할 수 있다.",
      "acceptanceCriteria": null,
      "dueDate": null,
      "deliverableName": null,
      "securityCondition": null,
      "sourceDocumentName": null,
      "sourceExcerpt": null,
      "priority": "HIGH",
      "status": "UNCONFIRMED",
      "confirmed": false,
      "createdAt": "2026-07-30T15:12:00",
      "updatedAt": "2026-07-30T15:12:00"
    }
  ],
  "requiredArtifacts": [],
  "keyFeatures": [],
  "planningExtraction": null
}
```

## 3. 요구사항 검토와 확정

허용 enum:

- `type`: `FUNCTIONAL`, `NON_FUNCTIONAL`, `SECURITY`, `DATA`, `INTERFACE`, `OPERATION`, `PROJECT_MANAGEMENT`, `UNSPECIFIED`
- `priority`: `HIGH`, `MEDIUM`, `LOW`, `UNSPECIFIED`
- `status`: `UNCONFIRMED`, `CONFIRMED`, `REJECTED`

### 3-1. 선택 문서 요구사항 AI 분석

```http
POST {{baseUrl}}/api/projects/{{projectId}}/requirements/analyze
```

```json
{
  "documentIds": [
    {{documentId}}
  ]
}
```

응답은 아래 요구사항 목록 구조다. Planning Agent 실행이 필요하다.

### 3-2. 요구사항 목록

```http
GET {{baseUrl}}/api/projects/{{projectId}}/requirements
GET {{baseUrl}}/api/projects/{{projectId}}/requirements?type=FUNCTIONAL&priority=HIGH&status=UNCONFIRMED&confirmed=false
```

```json
{
  "projectId": 2,
  "aiSuggestions": [
    {
      "requirementId": 30,
      "analysisResultId": 20,
      "sourceDocumentId": 10,
      "externalReferenceId": 1,
      "type": "FUNCTIONAL",
      "title": "주문 생성",
      "description": "사용자는 장바구니 상품으로 주문을 생성할 수 있다.",
      "acceptanceCriteria": null,
      "dueDate": null,
      "deliverableName": null,
      "securityCondition": null,
      "sourceDocumentName": null,
      "sourceExcerpt": null,
      "priority": "HIGH",
      "status": "UNCONFIRMED",
      "confirmed": false,
      "createdAt": "2026-07-30T15:12:00",
      "updatedAt": "2026-07-30T15:12:00"
    }
  ],
  "finalRequirements": []
}
```

### 3-3. 요구사항 단건 생성

```http
POST {{baseUrl}}/api/projects/{{projectId}}/requirements
```

```json
{
  "analysisResultId": {{analysisResultId}},
  "sourceDocumentId": {{documentId}},
  "externalReferenceId": 100,
  "type": "NON_FUNCTIONAL",
  "title": "응답 시간",
  "description": "주문 조회 API의 95%가 2초 이내 응답해야 한다.",
  "acceptanceCriteria": "부하 테스트 결과 p95 <= 2초",
  "dueDate": "2026-09-30",
  "deliverableName": "성능 테스트 결과서",
  "securityCondition": null,
  "sourceDocumentName": "requirements.txt",
  "sourceExcerpt": "응답시간은 2초 이내로 한다.",
  "priority": "MEDIUM"
}
```

출력 `201 Created`는 요구사항 상세 객체이며 최초 상태는 `UNCONFIRMED`, `confirmed=false`다.

### 3-4. 요구사항 단건 조회

```http
GET {{baseUrl}}/api/projects/{{projectId}}/requirements/{{requirementId}}
```

출력은 요구사항 상세 객체다.

### 3-5. 요구사항 부분 수정

```http
PATCH {{baseUrl}}/api/projects/{{projectId}}/requirements/{{requirementId}}
```

보낸 필드만 수정한다.

```json
{
  "title": "주문 생성 및 검증",
  "acceptanceCriteria": "정상 주문 생성 시 주문번호를 반환한다.",
  "dueDate": "2026-09-10",
  "priority": "HIGH"
}
```

출력은 수정된 요구사항 상세 객체다.

### 3-6. 확정/확정 취소/반려

body 없음:

```http
PATCH {{baseUrl}}/api/projects/{{projectId}}/requirements/{{requirementId}}/confirm
PATCH {{baseUrl}}/api/projects/{{projectId}}/requirements/{{requirementId}}/unconfirm
PATCH {{baseUrl}}/api/projects/{{projectId}}/requirements/{{requirementId}}/reject
```

확정 출력의 핵심:

```json
{
  "requirementId": 30,
  "status": "CONFIRMED",
  "confirmed": true
}
```

전체 확정:

```http
PATCH {{baseUrl}}/api/projects/{{projectId}}/requirements/confirm
```

출력은 확정된 요구사항 상세 객체 배열이다.

### 3-7. 최종 요구사항 전체 저장

화면에서 편집한 전체 목록으로 교체 저장한다.

```http
PUT {{baseUrl}}/api/projects/{{projectId}}/requirements/final
```

```json
{
  "requirements": [
    {
      "requirementId": {{requirementId}},
      "analysisResultId": {{analysisResultId}},
      "sourceDocumentId": {{documentId}},
      "externalReferenceId": 1,
      "type": "FUNCTIONAL",
      "title": "주문 생성 및 검증",
      "description": "사용자는 장바구니 상품으로 주문을 생성할 수 있다.",
      "acceptanceCriteria": "정상 주문 생성 시 주문번호를 반환한다.",
      "dueDate": "2026-09-10",
      "deliverableName": "기능 명세서",
      "securityCondition": "민감정보 로그 금지",
      "sourceDocumentName": "requirements.txt",
      "sourceExcerpt": "주문 기능을 제공한다.",
      "priority": "HIGH"
    }
  ]
}
```

출력은 `aiSuggestions`, `finalRequirements`를 포함한 요구사항 목록이다.

### 3-8. 요구사항 삭제

```http
DELETE {{baseUrl}}/api/projects/{{projectId}}/requirements/{{requirementId}}
```

출력 `204 No Content`. 확정된 요구사항 또는 WBS에 연결된 요구사항은 삭제할 수 없다.

## 4. WBS

허용 enum:

- `phase`: `ANALYSIS`, `DESIGN`, `DEVELOPMENT`, `TEST`, `DEPLOYMENT`, `OPERATION`
- `requiredSkills`: `DOCUMENT_ANALYSIS`, `REQUIREMENTS_ANALYSIS`, `ARCHITECTURE_DESIGN`, `BACKEND_DEVELOPMENT`, `FRONTEND_DEVELOPMENT`, `TESTING`, `DEVOPS`
- `difficulty`: `LOW`, `MEDIUM`, `HIGH`

### 4-1. AI WBS 생성

확정 요구사항이 있어야 한다.

```http
POST {{baseUrl}}/api/projects/{{projectId}}/wbs/generate
```

body 없음. Planning Agent가 실행 중이면 `201 Created`로 아래 WBS 조회 구조를 반환한다.

### 4-2. AI WBS 결과 콜백 저장

외부 AI 서버 없이 테스트할 때 직접 호출한다.

```http
POST {{baseUrl}}/api/projects/{{projectId}}/wbs/results
```

```json
{
  "agentExecutionId": "wbs-postman-001",
  "agentVersion": "wbs-agent-v1",
  "tasks": [
    {
      "externalTaskId": "TASK-001",
      "parentExternalTaskId": null,
      "taskCode": "1",
      "taskName": "요구사항 상세 분석",
      "description": "확정 요구사항과 수용 기준을 검토한다.",
      "phase": "ANALYSIS",
      "requiredSkills": [
        "REQUIREMENTS_ANALYSIS"
      ],
      "difficulty": "MEDIUM",
      "estimatedHours": 16,
      "orderIndex": 1,
      "requirementIds": [
        {{requirementId}}
      ]
    },
    {
      "externalTaskId": "TASK-002",
      "parentExternalTaskId": "TASK-001",
      "taskCode": "2",
      "taskName": "주문 API 구현",
      "description": "주문 생성 API와 검증 로직을 구현한다.",
      "phase": "DEVELOPMENT",
      "requiredSkills": [
        "BACKEND_DEVELOPMENT"
      ],
      "difficulty": "HIGH",
      "estimatedHours": 40,
      "orderIndex": 2,
      "requirementIds": [
        {{requirementId}}
      ]
    }
  ]
}
```

출력 `201 Created`:

```json
{
  "wbsResultId": 40,
  "projectId": 2,
  "agentExecutionId": "wbs-postman-001",
  "taskCount": 2
}
```

### 4-3. WBS 조회

```http
GET {{baseUrl}}/api/projects/{{projectId}}/wbs
```

```json
{
  "wbsResultId": 40,
  "projectId": 2,
  "agentExecutionId": "wbs-postman-001",
  "agentVersion": "wbs-agent-v1",
  "finalConfirmed": false,
  "createdAt": "2026-07-30T15:20:00",
  "aiSuggestionTasks": [
    {
      "taskId": 50,
      "externalTaskId": "TASK-001",
      "parentExternalTaskId": null,
      "taskCode": "1",
      "taskName": "요구사항 상세 분석",
      "description": "확정 요구사항과 수용 기준을 검토한다.",
      "phase": "ANALYSIS",
      "requiredSkills": [
        "REQUIREMENTS_ANALYSIS"
      ],
      "difficulty": "MEDIUM",
      "estimatedHours": 16,
      "orderIndex": 1,
      "requirementIds": [
        30
      ],
      "confirmed": false
    }
  ],
  "finalTasks": []
}
```

### 4-4. 최종 WBS 전체 저장

```http
PUT {{baseUrl}}/api/projects/{{projectId}}/wbs/final
```

```json
{
  "tasks": [
    {
      "externalTaskId": "TASK-001",
      "parentExternalTaskId": null,
      "taskCode": "1",
      "taskName": "요구사항 상세 분석",
      "description": "확정 요구사항과 수용 기준을 검토한다.",
      "phase": "ANALYSIS",
      "requiredSkills": [
        "REQUIREMENTS_ANALYSIS"
      ],
      "difficulty": "MEDIUM",
      "estimatedHours": 16,
      "orderIndex": 1,
      "requirementIds": [
        {{requirementId}}
      ]
    },
    {
      "externalTaskId": "TASK-002",
      "parentExternalTaskId": "TASK-001",
      "taskCode": "2",
      "taskName": "주문 API 구현",
      "description": "주문 생성 API와 검증 로직을 구현한다.",
      "phase": "DEVELOPMENT",
      "requiredSkills": [
        "BACKEND_DEVELOPMENT"
      ],
      "difficulty": "HIGH",
      "estimatedHours": 40,
      "orderIndex": 2,
      "requirementIds": [
        {{requirementId}}
      ]
    }
  ]
}
```

출력은 WBS 조회 구조이며 `finalConfirmed=true`, `finalTasks[*].confirmed=true`가 된다. 이후 일정 저장에는 `finalTasks[*].taskId`를 사용한다.

## 5. 일정과 산출물

### 5-1. AI 일정 생성 요청

```http
POST {{baseUrl}}/api/projects/{{projectId}}/schedules/generate
```

body 없음.

출력 `202 Accepted`:

```json
{
  "agentExecutionId": "schedule-20260730-001",
  "status": "REQUESTED",
  "agentVersion": "schedule-agent-v1"
}
```

### 5-2. AI 일정 결과 콜백 저장

```http
POST {{baseUrl}}/api/projects/{{projectId}}/schedules/results
```

`wbsId`와 `predecessorWbsIds`는 외부 task code가 아니라 WBS 조회의 DB `taskId`다.

```json
{
  "agentExecutionId": "schedule-postman-001",
  "agentVersion": "schedule-agent-v1",
  "projectStartDate": "2026-08-03",
  "targetEndDate": "2026-10-30",
  "schedules": [
    {
      "externalScheduleId": "SCH-001",
      "wbsId": {{wbsTaskId}},
      "startDate": "2026-08-03",
      "endDate": "2026-08-05",
      "estimatedDays": 3,
      "predecessorWbsIds": [],
      "milestone": false,
      "bufferDays": 0
    }
  ]
}
```

출력 `201 Created`:

```json
{
  "scheduleResultId": 60,
  "projectId": 2,
  "agentExecutionId": "schedule-postman-001",
  "scheduleCount": 1
}
```

현재 일정 조회/수정 API는 구현되어 있지 않다.

### 5-3. 필수 산출물 현황

```http
GET {{baseUrl}}/api/projects/{{projectId}}/artifacts/status
```

```json
{
  "projectId": 2,
  "totalRequiredCount": 2,
  "registeredCount": 1,
  "approvedCount": 0,
  "registrationRate": 50.0,
  "approvalCompletionRate": 0.0,
  "artifactRegister": [
    {
      "artifactType": "WBS",
      "artifactName": "작업분해구조",
      "status": "COMPLETED",
      "version": "1.0",
      "requiredVersion": "1.0",
      "approvalStatus": "PENDING"
    }
  ],
  "missingArtifacts": [
    {
      "artifactType": "TEST_RESULTS",
      "artifactName": "테스트 결과서",
      "status": "MISSING",
      "version": null,
      "requiredVersion": "1.0",
      "approvalStatus": null
    }
  ],
  "unapprovedArtifacts": [],
  "outdatedArtifacts": [],
  "recommendations": [
    "누락 산출물을 등록하세요."
  ]
}
```

## 6. 운영 AI 분석

아래 API는 `AI_SERVER_BASE_URL`의 AI 서버가 실행 중이어야 한다. 모든 응답은 AI 결과에 따라 값이 달라진다.

### 6-1. 프로젝트 변경 영향도

```http
POST {{baseUrl}}/api/projects/{{projectId}}/impact-analysis
```

```json
{
  "requirementId": {{requirementId}},
  "changeTitle": "소셜 로그인 추가",
  "changeDescription": "Google과 Kakao 로그인을 범위에 추가한다.",
  "affectedTaskCount": 4,
  "affectedMemberCount": 3,
  "remainingDays": 20,
  "additionalWorkDays": 7,
  "scopeChanged": true,
  "databaseChanged": true,
  "apiChanged": true,
  "uiChanged": true
}
```

```json
{
  "projectId": 2,
  "requirementId": 30,
  "impactScore": 78,
  "impactLevel": "HIGH",
  "scheduleImpactScore": 85,
  "scopeImpactScore": 80,
  "resourceImpactScore": 70,
  "technicalImpactScore": 76,
  "riskFactors": [
    "인증 범위 증가",
    "일정 여유 부족"
  ],
  "recommendedActions": [
    "일정을 1주 연장하세요.",
    "백엔드 담당자를 추가 배정하세요."
  ]
}
```

### 6-2. 팀원 지연 위험

```http
POST {{baseUrl}}/api/projects/{{projectId}}/member-delay
```

body 없음. 팀원 현황은 DB에서 조회한다.

```json
{
  "projectId": 2,
  "analyzedMemberCount": 4,
  "highRiskMemberCount": 1,
  "memberResults": [
    {
      "memberId": 1,
      "memberName": "김개발",
      "completionRate": 37.5,
      "overdueRate": 37.5,
      "delayScore": 82,
      "riskLevel": "HIGH",
      "reasons": [
        "지연 작업 3건",
        "업무 부하 85%"
      ],
      "recommendedAction": "업무 재배정을 검토하세요."
    }
  ]
}
```

### 6-3. 담당자 재배정 추천

```http
POST {{baseUrl}}/api/projects/{{projectId}}/assignments/1001
```

입력 body는 생략 가능하다. 생략하면 DB의 팀원 데이터를 사용한다. 직접 입력 예:

```json
{
  "taskName": "주문 API 구현",
  "requiredRole": "Backend",
  "requiredSkills": [
    "Java",
    "Spring"
  ],
  "currentAssignee": {
    "memberId": 1,
    "memberName": "김개발",
    "skills": [
      "Java"
    ],
    "workloadRate": 85.0,
    "overdueTaskCount": 3
  },
  "candidates": [
    {
      "memberId": 2,
      "memberName": "이수현",
      "role": "Backend",
      "skills": [
        "Java",
        "Spring",
        "AWS"
      ],
      "workloadRate": 40.0,
      "overdueTaskCount": 0
    }
  ]
}
```

```json
{
  "projectId": 2,
  "taskId": 1001,
  "reassignmentRequired": true,
  "currentAssigneeRiskScore": 85,
  "currentAssigneeRiskLevel": "HIGH",
  "recommendedAssignee": {
    "memberId": 2,
    "memberName": "이수현",
    "matchScore": 94,
    "skillMatchRate": 100.0,
    "workloadRate": 40.0,
    "overdueTaskCount": 0,
    "reason": "필요 기술을 보유하고 업무 부하가 낮습니다."
  },
  "alternativeCandidates": [],
  "reasons": [
    "현재 담당자의 업무 부하와 지연 건수가 높습니다."
  ]
}
```

### 6-4. 산출물 보안 검사

```http
POST {{baseUrl}}/api/projects/{{projectId}}/deliverables/5001/security-check
```

```json
{
  "artifactName": "API 연동 명세서",
  "artifactType": "FUNCTION_SPECIFICATION",
  "textContent": "담당자 이메일 user@example.com, API Key: sk-test-secret"
}
```

```json
{
  "projectId": 2,
  "artifactName": "API 연동 명세서",
  "securityRiskScore": 92,
  "securityRiskLevel": "CRITICAL",
  "registrationAllowed": false,
  "detections": [
    {
      "detectionType": "EMAIL",
      "count": 1,
      "description": "이메일 주소가 탐지되었습니다."
    },
    {
      "detectionType": "API_KEY",
      "count": 1,
      "description": "API 키 형식 문자열이 탐지되었습니다."
    }
  ],
  "maskedContent": "담당자 이메일 u***@example.com, API Key: sk-****",
  "recommendations": [
    "민감정보를 제거한 뒤 다시 등록하세요."
  ]
}
```

## 7. Slack 연결과 커뮤니케이션 리스크

`SLACK_BOT_TOKEN`이 없으면 Slack 기능은 `409`를 반환한다.

### 7-1. Slack 연결 상태

```http
GET {{baseUrl}}/api/slack/connection
```

현재 SecurityConfig 기준으로 이 API는 인증 없이도 호출된다.

```json
{
  "connected": true,
  "totalChannels": 2,
  "joinedChannels": [
    {
      "channelId": "C0123456789",
      "channelName": "project-shopping",
      "isPrivate": false
    }
  ],
  "notJoinedChannels": [
    {
      "channelId": "C0987654321",
      "channelName": "random",
      "isPrivate": false
    }
  ]
}
```

### 7-2. 연결 후보 채널

```http
GET {{baseUrl}}/api/projects/{{projectId}}/slack-channels/candidates
```

```json
[
  {
    "channelId": "C0123456789",
    "channelName": "project-shopping",
    "isPrivate": false,
    "botJoined": true,
    "alreadyLinked": false
  }
]
```

### 7-3. 프로젝트에 Slack 채널 연결

```http
POST {{baseUrl}}/api/projects/{{projectId}}/slack-channels
```

```json
{
  "channelId": "{{channelId}}"
}
```

출력 `201 Created`:

```json
{
  "id": 1,
  "channelId": "C0123456789",
  "channelName": "project-shopping",
  "lastSyncedTs": null,
  "createdAt": "2026-07-30T15:30:00"
}
```

### 7-4. 연결 채널 목록

```http
GET {{baseUrl}}/api/projects/{{projectId}}/slack-channels
```

출력은 `SlackChannelResponse` 배열이다.

### 7-5. 커뮤니케이션 리스크 조회

```http
GET {{baseUrl}}/api/projects/{{projectId}}/communication-risks
```

분석 전:

```json
{
  "projectId": 2,
  "projectName": "쇼핑몰 고도화 프로젝트",
  "status": "NEVER_ANALYZED",
  "riskLevel": null,
  "reasons": [],
  "evidenceMessages": [],
  "recommendedAction": null,
  "metrics": {
    "recent7dMessageCount": 0,
    "previous7dMessageCount": 0,
    "activityChangePercent": null,
    "longUnansweredCount": 0
  },
  "analysisWindow": null,
  "llmStatus": null,
  "analyzedAt": null
}
```

### 7-6. Slack 동기화 및 리스크 재분석

```http
POST {{baseUrl}}/api/projects/{{projectId}}/communication-risks/refresh
```

body 없음.

```json
{
  "projectId": 2,
  "projectName": "쇼핑몰 고도화 프로젝트",
  "status": "ANALYZED",
  "riskLevel": "MEDIUM",
  "reasons": [
    "최근 7일 메시지 수가 이전 7일 대비 감소했습니다."
  ],
  "evidenceMessages": [
    {
      "channelId": "C0123456789",
      "channelName": "project-shopping",
      "messageTs": "2026-07-29T11:20:00",
      "threadTs": null,
      "messageText": "배송 API 일정 확인이 필요합니다."
    }
  ],
  "recommendedAction": "배송 API 담당자와 일정 확인 회의를 진행하세요.",
  "metrics": {
    "recent7dMessageCount": 42,
    "previous7dMessageCount": 80,
    "activityChangePercent": -47.5,
    "longUnansweredCount": 2
  },
  "analysisWindow": {
    "start": "2026-07-16T00:00:00",
    "end": "2026-07-30T15:30:00"
  },
  "llmStatus": "SUCCEEDED",
  "analyzedAt": "2026-07-30T15:30:05"
}
```

### 7-7. Slack 채널 연결 해제

```http
DELETE {{baseUrl}}/api/projects/{{projectId}}/slack-channels/{{channelId}}
```

출력 `204 No Content`.

## 8. 관리자 사용자 생성

```http
POST {{baseUrl}}/api/admin/users
```

현재 구현상 인증은 필요하지만 별도의 ADMIN role 검사는 없다.

```json
{
  "employeeNumber": "ST-0100",
  "name": "박스태프",
  "role": "STAFF"
}
```

출력 `201 Created`:

```json
{
  "employeeNumber": "ST-0100",
  "name": "박스태프",
  "email": null,
  "role": "STAFF",
  "status": "MUST_CHANGE_PASSWORD"
}
```

초기 비밀번호는 현재 서비스 코드상 `PMagent123!`이다.

## 9. 프로젝트 삭제

흐름 테스트가 모두 끝난 뒤 마지막에 실행한다.

```http
DELETE {{baseUrl}}/api/projects/{{projectId}}
```

출력 `204 No Content`. 인증한 PM이 해당 프로젝트 담당 PM이어야 한다.

## 10. 공통 오류 응답

```json
{
  "timestamp": "2026-07-30T15:40:00",
  "status": 400,
  "code": null,
  "error": "Bad Request",
  "message": "fieldName: must not be blank"
}
```

자주 만나는 상태:

| 상태 | 대표 코드/원인 |
|---|---|
| `400` | DTO 검증 실패, 잘못된 enum/JSON |
| `401` | `AUTH_UNAUTHORIZED`, `AUTH_TOKEN_EXPIRED` |
| `403` | `AUTH_FORBIDDEN`, PM 전용 API를 STAFF가 호출 |
| `404` | `PROJECT_NOT_FOUND`, 리소스 없음 |
| `409` | 중복 데이터, Slack/외부 연동 준비 안 됨 |
| `503` | `MAIL_NOT_CONFIGURED`, AI 서버 연결 불가 |

## 권장 실행 순서

1. 로그인
2. 프로젝트 초안 생성
3. 문서 업로드
4. 문서 목록에서 `documentId` 확보
5. 분석 결과 콜백 저장
6. 요구사항 목록에서 `requirementId` 확보
7. 요구사항 전체 확정
8. WBS 결과 콜백 저장
9. WBS 조회
10. 최종 WBS 저장
11. WBS 조회에서 최종 `taskId` 확보
12. 일정 결과 콜백 저장
13. 산출물 상태 조회
14. 운영 AI/Slack API 선택 테스트
15. 프로젝트 삭제
