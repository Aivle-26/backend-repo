# BidWorks AI — 클릭 가능한 프론트엔드 프로토타입

## Context
사용자는 첨부한 와이어플로우 이미지를 기준으로 공공 RFP 분석/업무 관리 SaaS "BidWorks AI"의 **클릭 가능한 프론트엔드 MVP 프로토타입**을 원한다. 백엔드/API 없이 mock data로 구성하며, 모든 UI 문구는 한국어, B2B SaaS 대시보드 스타일(흰색/회색, 카드형, 좌측 사이드바)로 만든다. PM 화면과 직원 화면이 명확히 구분되어야 하고, 와이어플로우의 5개 화면을 충실히 재현한다.

현재 `src/app/App.tsx`는 빈 스텁이며, shadcn/ui 컴포넌트(47개)가 `src/app/components/ui/`에 준비되어 있고 lucide-react, recharts, sonner 등이 설치되어 있다. `@make-kits` 디자인 시스템은 없으므로 기존 shadcn/ui 컴포넌트와 `theme.css` 토큰을 사용한다.

## 분류
**PureFrontend** — 외부 API/Supabase 불필요. 모든 데이터는 mock.

## 네비게이션 방식
URL 라우팅 대신 **React 상태 기반 화면 전환**을 사용(프로토타입에 충분하고 단순). App.tsx에서:
- `role: 'pm' | 'staff' | null`
- `screen` 상태 (로그인 / pm 메뉴 / 직원 메뉴)
- 로그인 → 역할에 따라 PM 또는 직원 대시보드로 이동.

## 파일 구조 (모두 신규 `.tsx`, `src/app/components/` 하위)

### 공통
- `src/app/App.tsx` — 최상위 상태 관리 + 화면 라우팅, `<Toaster />`(sonner) 포함
- `src/app/data/mock.ts` — 모든 mock data (프로젝트, 요구사항, 업무, 팀원, 리스크, 피드백, 댓글) 와 타입 정의
- `src/app/components/layout/Sidebar.tsx` — 역할별 메뉴를 받아 렌더하는 재사용 사이드바 (로고 "BidWorks AI", 활성 메뉴 강조)
- `src/app/components/layout/TopBar.tsx` — 상단 헤더(프로젝트명, 알림 아이콘, 아바타, 로그아웃)

### 1. 로그인 / 역할 선택
- `src/app/components/auth/LoginScreen.tsx`
  - 중앙 카드: 로고, 이메일/비밀번호 Input, 역할 선택(PM / 직원) 토글 카드 2개(설명 포함), 로그인 Button
  - 로그인 시 `onLogin(role)` 호출 → 해당 대시보드로 이동

### 2. PM 대시보드
- `src/app/components/pm/PmDashboard.tsx`
  - 상단 프로젝트명 "도시 인프라 RFP 2024"
  - KPI 카드 4개: 전체 진행률(Progress), 남은 일수(14), 진행 중 업무(28), 고위험 항목(3)
  - RFP 공고문 업로드 영역(드롭존 UI, mock)
  - AI 분석 요약 카드 (요청된 예시 문구 3개)
  - 요구사항 미리보기 테이블 (Table: #, 요구사항, 분류, 우선순위, 상태)
  - 팀원 업무 현황 (팀원별 진행률 바)
  - 리스크 알림 영역
  - 사이드바 메뉴: 대시보드 / 공고문 업로드 / AI 분석 / 요구사항 / 업무 배정 / 리스크 / 검토
  - "AI 분석" 또는 "업무 배정" 메뉴 클릭 시 PmAnalysis 화면으로 전환

### 3. PM — RFP 분석 및 업무 배정
- `src/app/components/pm/PmAnalysis.tsx`
  - 업로드된 RFP 파일 정보 바 (도시인프라-rfp-2024.pdf, 다시 업로드/분석 버튼)
  - AI 추출 요구사항 테이블 (체크 선택 가능): #, 요구사항, 분류, 우선순위, 난이도, 추천 담당자 — 요청된 예시 요구사항 4개+ 포함
  - 우측 업무 배정 패널: 선택한 요구사항, 담당자 Select, 마감일 Input(date), 업무 메모 Textarea, [업무 배정하기] / [검토 요청] 버튼 (클릭 시 toast)
  - 하단 AI 리스크 분석 카드 3개: 공급망 지연 / 규정 준수 미흡 / 구조적 실패 위험

### 4. 직원 대시보드
- `src/app/components/staff/StaffDashboard.tsx`
  - 사이드바 메뉴: 내 업무 / RFP 맥락 / 산출물 제출 / 피드백 / 댓글
  - KPI 카드 4개: 내 업무(12) / 마감 임박(3) / 검토 중(5) / 완료(48)
  - 칸반 보드 4열: 할 일 / 진행 중 / 검토 요청 / 완료 — 요청된 업무 예시 카드들 배치
  - 관련 RFP 요구사항 카드
  - AI 업무 도우미 카드 (요청된 예시 문구)
  - 최근 PM 피드백 영역
  - 업무 카드 클릭 → StaffTaskDetail 화면으로 전환

### 5. 직원 — 업무 상세 / 제출 / 피드백
- `src/app/components/staff/StaffTaskDetail.tsx`
  - 3단 레이아웃: 사이드바 / 가운데 메인 / 우측 피드백 패널
  - 메인: 업무 제목 "3.2 환경 규정 준수 초안 작성", 진행 상태 Badge, 마감일, 우선순위, 배정자, 관련 RFP 요구사항, 업무 설명, 체크리스트(Checkbox), 산출물 제출 영역(파일/링크 첨부 + [검토 요청 제출] 버튼 → toast)
  - 우측 패널: PM 피드백, 댓글(입력 가능 mock), AI 업무 요약 (요청된 예시 문구)

### 하단 협업 흐름 (선택적 표시)
- `src/app/components/common/WorkflowFooter.tsx` — 7단계 흐름 시각화(PM RFP 업로드 → … → 직원 수정 후 완료). 대시보드 하단에 배치.

## 스타일/구현 규칙
- 기존 shadcn/ui 컴포넌트 사용: Button, Input, Card, Table, Badge, Progress, Checkbox, Select, Textarea, Avatar, Separator, Tabs 등 — `import { Button } from "@/app/components/ui/button"` 패턴.
- 색상은 `theme.css` 토큰 활용(흰/회색 베이스). raw hex 산발 금지.
- 아이콘은 lucide-react.
- 폰트 import는 필요 시 `src/styles/fonts.css` 상단에만 추가(Inter). 토큰 변경은 최소화.
- 모든 버튼/제출은 mock 동작(toast 등). 실제 API 없음.
- 데스크톱 중심 레이아웃.
- `text-xl`, `font-bold` 등 폰트 크기/굵기 Tailwind 클래스는 사용자가 요청하지 않는 한 사용 안 함 — 시맨틱 태그/기존 토큰 의존.

## 검증
1. dev 서버는 이미 실행 중 — preview에서 확인.
2. 로그인 화면 → PM 선택 후 로그인 → PM 대시보드 진입 확인.
3. PM 대시보드에서 "AI 분석"/"업무 배정" 메뉴 클릭 → 분석/배정 화면 전환, 요구사항 선택 → 우측 패널 반영, [업무 배정하기] → toast.
4. 로그아웃 후 직원 선택 로그인 → 직원 대시보드 진입, 칸반 표시 확인.
5. 업무 카드 클릭 → 업무 상세 3단 레이아웃, 체크리스트 토글, [검토 요청 제출] → toast.
6. 콘솔 에러 없는지 확인.
