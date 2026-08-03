# 커밋되지 않은 변경사항

작성 기준: 2026-07-30

## 현재 Git 상태

- 백엔드 저장소: 로컬 `dev` 브랜치
- 원격 추적 브랜치: `origin/dev`
- 기준 커밋: `28f34eb`
- 백엔드: 수정된 파일 19개
- 프론트엔드: 수정된 파일 10개, 추적되지 않은 `.npm-cache/` 디렉터리 1개
- 모든 변경사항은 아직 커밋되지 않음

> 이 문서 자체도 현재 추가된 미추적 변경사항입니다.

## 1. 운영용 이메일 인증 시스템

회원가입 이메일 인증과 비밀번호 재설정 메일을 개인 Gmail SMTP 중심 구조에서 운영용 SMTP 구조로 확장했습니다.

### 백엔드 변경

- `MailService`에 운영 SMTP 전송 설정을 반영했습니다.
  - SMTP 인증 및 STARTTLS 사용
  - 발신자 이름, 회신 주소, 제목 접두사 설정
  - 전송 실패 재시도 및 재시도 지연
  - 운영 환경에서 메일 설정 누락 시 시작 단계에서 확인
- 이메일 인증번호와 비밀번호 재설정 토큰을 평문 대신 해시 형태로 저장·검증하도록 변경했습니다.
- 인증번호 실패 횟수(`failedAttempts`)를 이메일 인증 엔티티에 추가했습니다.
- 사용자 인증 코드 컬럼 길이를 해시값 저장에 맞게 확장했습니다.
- 인증번호·재설정 메일 발송 및 검증 흐름을 `UserService`에 반영했습니다.

### 설정·문서 변경

- `.env.example`에 `MAIL_*` 운영 SMTP 환경변수 예시를 추가했습니다.
- `application.yaml`과 `application-prod.yml`에 메일 설정 기본값과 운영 정책을 추가했습니다.
- `README.md`에 AWS SES, SendGrid, Mailgun 등의 운영 메일 서비스 설정과 SPF/DKIM/DMARC 준비사항을 추가했습니다.

실제 SMTP 사용자명, 비밀번호, 도메인 인증 정보는 저장소에 넣지 않고 배포 환경 변수로 설정해야 합니다.

## 2. 30분 미사용 자동 로그아웃 제거

30분 동안 동작이 없으면 로그아웃시키던 기능을 프론트엔드와 백엔드에서 제거했습니다.

- `lastActivityAt` 상태 저장 및 갱신 제거
- 비활동 시간 검증 로직 제거
- `/api/users/activity` API 제거
- 비활동 관련 인증 응답 필드와 환경변수 제거
- 프론트엔드의 `inactive` 로그아웃 사유와 활동 보고 함수 제거
- 관련 테스트와 테스트 설정 정리

다음 만료 정책은 비활동 로그아웃과 별개이므로 유지됩니다.

- 액세스 토큰 만료: 기본 30분
- 로그인 세션의 절대 만료: 기본 8시간

액세스 토큰 30분 만료는 리프레시 토큰으로 갱신할 수 있는 토큰 수명이며, 사용자의 미동작 여부를 판단하는 기능이 아닙니다.

## 3. 프론트엔드의 project-planning 연동

프론트엔드 화면에서 예시 데이터를 표시하던 흐름을 실제 백엔드 API와 연결하는 변경이 포함되어 있습니다.

- 프로젝트 문서 업로드 및 문서 추출 API 연동
- 문서 분석 결과와 요구사항 조회
- 요구사항 검색·분류·상태 필터링
- 요구사항 전체 확정 API 호출
- 확정 요구사항을 기반으로 WBS 조회 또는 생성
- WBS 결과 로딩·오류·재시도 UI 추가
- 프로젝트 보드와 프로젝트 마법사에서 서버 상태를 반영
- 분석 중, 성공, 실패, 재시도 상태 표시
- 기존 인증 세션 응답에서 제거된 비활동 필드에 맞춘 타입·매핑 정리

주요 파일:

- `src/app/api/projectRepository.ts`
- `src/app/App.tsx`
- `src/app/components/pm/PmRequirements.tsx`
- `src/app/components/pm/PmUpload.tsx`
- `src/app/components/pm/ProjectExtraction.tsx`
- `src/app/components/pm/ProjectWizard.tsx`
- `src/app/components/pm/ProjectBoard.tsx`
- `src/app/components/pm/DocPicker.tsx`

## 4. 인증 응답 및 설정 정리

자동 로그아웃 제거에 맞춰 다음 백엔드 인증 파일을 함께 수정했습니다.

- `AuthProperties`
- `SecurityConfig`
- `UserController`
- `AuthSessionResponse`
- `LoginVerifyResponse`
- `AuthCodes`
- `AuthService`
- `User`
- `AuthServiceTest`
- 테스트용 `application.yaml`

## 5. 검증 결과

- 백엔드 전체 테스트: 146개 실행, 실패 0개, 오류 0개, 스킵 0개
- 프론트엔드 프로덕션 빌드: 성공
- 비활동 자동 로그아웃 관련 코드 검색: 구현 코드 잔여 없음
- `git diff --check`: 오류 없음

## 6. 주의사항

- 실제 배포 전 SMTP 공급자 계정과 발신 도메인의 SPF, DKIM, DMARC 설정이 필요합니다.
- 운영 SMTP 비밀번호는 `.env` 또는 배포 플랫폼의 Secret/Environment Variables에 저장해야 합니다.
- 기존 데이터베이스에 `last_activity_at` 컬럼이 이미 생성되어 있다면 컬럼 자체는 남아 있을 수 있지만, 현재 애플리케이션 코드는 해당 컬럼을 읽거나 기록하지 않습니다.
- 프론트엔드의 `.npm-cache/`는 추적되지 않은 로컬 캐시 디렉터리입니다. 커밋 대상인지 확인해야 합니다.
- 이번 문서에 나열된 변경사항은 아직 커밋·푸시되지 않았습니다.
