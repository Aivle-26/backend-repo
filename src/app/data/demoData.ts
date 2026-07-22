export type Role = "pm" | "staff";

export type Priority = "높음" | "중간" | "낮음";
export type Difficulty = "상" | "중" | "하";
export type ReqStatus = "미배정" | "배정됨" | "검토중" | "완료";

export interface Requirement {
  id: number;
  text: string;
  category: string;
  priority: Priority;
  difficulty: Difficulty;
  recommendedOwner: string;
  status: ReqStatus;
}

export interface TeamMember {
  id: string;
  name: string;
  role: string;
  done: number;
  total: number;
}

export interface RiskItem {
  id: string;
  title: string;
  level: "높음" | "중간" | "낮음";
  description: string;
}

export type TaskColumn = "todo" | "doing" | "review" | "done";

export interface Task {
  id: string;
  title: string;
  column: TaskColumn;
  priority: Priority;
  due: string;
  assignee: string;
  relatedReq: string;
}

export interface Feedback {
  id: string;
  author: string;
  date: string;
  text: string;
}

export const PROJECT_NAME = "도시 인프라 RFP 2024";

/* =====================================================================
 * 프로젝트 생애주기 (홈 · 프로젝트 보드)
 * ===================================================================*/

export type ProjectStatus = "분석중" | "준비" | "승인대기" | "진행중" | "완료";

export type ProjectDocType = "RFP" | "요구사항정의서" | "제안서";

export interface ProjectDoc {
  name: string;
  type: ProjectDocType;
}

export interface ProjectRequirement {
  id: number;
  text: string;
  category: string;
  priority: Priority;
  source: string; // 추출 출처 문서명
}

export interface ProjectSummary {
  id: string;
  name: string;
  client: string;
  status: ProjectStatus;
  progress: number; // 진행중/완료: 0~100
  dueDate: string;
  riskCount: number;
  reqCount: number;
  wizardStep: number; // 준비: 0~6 (6 완료 시 시작 가능)
  estimate: string; // 견적
  updatedAt: string;
  docs: ProjectDoc[]; // 초기 문서 (없으면 나중에 업로드)
  requirements?: ProjectRequirement[]; // AI 추출 요구사항 (있으면 마법사에서 사용)
}

/** AI 추출 시뮬레이션용 요구사항 풀 */
export const EXTRACTED_REQ_POOL: {
  text: string;
  category: string;
  priority: Priority;
}[] = [
  { text: "회원가입·로그인 시 소셜 인증(OAuth)을 지원해야 합니다.", category: "기능", priority: "높음" },
  { text: "결제 모듈은 카드·간편결제·계좌이체를 모두 지원해야 합니다.", category: "기능", priority: "높음" },
  { text: "개인정보는 저장 시 암호화하고 접근 로그를 남겨야 합니다.", category: "보안", priority: "높음" },
  { text: "주요 API 응답 시간은 평균 300ms 이내여야 합니다.", category: "성능", priority: "중간" },
  { text: "관리자 대시보드에서 실시간 통계를 확인할 수 있어야 합니다.", category: "기능", priority: "중간" },
  { text: "모바일 반응형 UI를 지원해야 합니다.", category: "UI", priority: "중간" },
  { text: "데이터는 일 1회 자동 백업되어야 합니다.", category: "데이터", priority: "중간" },
  { text: "장애 발생 시 30분 이내 복구 가능한 이중화 구성을 갖춰야 합니다.", category: "인프라", priority: "높음" },
  { text: "사용자 활동 감사(audit) 로그를 1년간 보관해야 합니다.", category: "보안", priority: "중간" },
  { text: "다국어(한/영) 전환을 지원해야 합니다.", category: "기능", priority: "낮음" },
  { text: "알림(이메일·푸시) 발송 기능을 제공해야 합니다.", category: "기능", priority: "중간" },
  { text: "접근성 지침(WCAG 2.1 AA)을 준수해야 합니다.", category: "UI", priority: "낮음" },
  { text: "외부 협업툴(Slack·Jira)과 연동할 수 있어야 합니다.", category: "운영", priority: "중간" },
  { text: "배포는 무중단(blue-green) 방식을 지원해야 합니다.", category: "인프라", priority: "낮음" },
];

export const WIZARD_STEPS = [
  "요구사항",
  "WBS",
  "담당자",
  "일정",
  "노동법·가이드",
  "견적",
];

export const PROJECTS: ProjectSummary[] = [
  {
    id: "prj-launch",
    name: "신제품 출시 프로젝트",
    client: "㈜미래커머스",
    status: "진행중",
    progress: 62,
    dueDate: "2026-07-22",
    riskCount: 3,
    reqCount: 24,
    wizardStep: 6,
    estimate: "3,400만원",
    updatedAt: "오늘 10:32",
    docs: [
      { name: "RFP_신제품출시_v2.pdf", type: "RFP" },
      { name: "요구사항정의서_v1.3.docx", type: "요구사항정의서" },
    ],
  },
  {
    id: "prj-renewal",
    name: "브랜드 리뉴얼 프로젝트",
    client: "블루밍 리테일",
    status: "준비",
    progress: 0,
    dueDate: "2026-09-07",
    riskCount: 0,
    reqCount: 18,
    wizardStep: 3,
    estimate: "-",
    updatedAt: "오늘 09:14",
    docs: [
      { name: "브랜드리뉴얼_RFP.pdf", type: "RFP" },
      { name: "제안서_초안.pdf", type: "제안서" },
    ],
  },
  {
    id: "prj-traffic",
    name: "교통관제 시스템 구축",
    client: "성남시청",
    status: "승인대기",
    progress: 0,
    dueDate: "2026-11-30",
    riskCount: 0,
    reqCount: 30,
    wizardStep: 6,
    estimate: "3,200만원 ~ 3,600만원",
    updatedAt: "어제 18:40",
    docs: [
      { name: "교통관제_RFP_2026.pdf", type: "RFP" },
      { name: "요구사항정의서_v2.docx", type: "요구사항정의서" },
      { name: "제안서_최종.pdf", type: "제안서" },
    ],
  },
  {
    id: "prj-internal",
    name: "내부 관리도구 고도화",
    client: "사내 · 운영팀",
    status: "진행중",
    progress: 41,
    dueDate: "2026-08-10",
    riskCount: 1,
    reqCount: 15,
    wizardStep: 6,
    estimate: "1,800만원",
    updatedAt: "오늘 08:50",
    docs: [{ name: "내부관리도구_요구사항정의서.docx", type: "요구사항정의서" }],
  },
  {
    id: "prj-migration",
    name: "구 사이트 마이그레이션",
    client: "㈜한빛출판",
    status: "완료",
    progress: 100,
    dueDate: "2026-06-20",
    riskCount: 0,
    reqCount: 12,
    wizardStep: 6,
    estimate: "2,100만원",
    updatedAt: "2026-06-20",
    docs: [{ name: "마이그레이션_RFP.pdf", type: "RFP" }],
  },
];

export const KPI_PM = {
  progress: 62,
  daysLeft: 14,
  inProgress: 28,
  highRisk: 3,
};

export const KPI_STAFF = {
  myTasks: 12,
  dueSoon: 3,
  inReview: 5,
  completed: 48,
};

export const AI_SUMMARY: string[] = [
  "총 24개의 요구사항이 추출되었습니다.",
  "보안, 환경, 일정 관련 고위험 항목 3개가 발견되었습니다.",
  "PM 검토 후 7개 업무로 분해할 수 있습니다.",
];

export const REQUIREMENTS: Requirement[] = [
  {
    id: 1,
    text: "비상 조명 시스템은 4시간 이상 배터리 예비 전원을 확보해야 합니다.",
    category: "안전",
    priority: "높음",
    difficulty: "중",
    recommendedOwner: "김지훈",
    status: "미배정",
  },
  {
    id: 2,
    text: "폐수 관리에 대한 환경영향 보고서를 작성해야 합니다.",
    category: "환경",
    priority: "높음",
    difficulty: "상",
    recommendedOwner: "이서연",
    status: "배정됨",
  },
  {
    id: 3,
    text: "기술 구역 주변에 보안 펜스 설치 계획을 수립해야 합니다.",
    category: "보안",
    priority: "중간",
    difficulty: "중",
    recommendedOwner: "박민수",
    status: "미배정",
  },
  {
    id: 4,
    text: "승강 설비 유지보수 일정을 작성해야 합니다.",
    category: "일정",
    priority: "낮음",
    difficulty: "하",
    recommendedOwner: "최예나",
    status: "검토중",
  },
  {
    id: 5,
    text: "전력 공급 이중화 설계 기준을 정의해야 합니다.",
    category: "인프라",
    priority: "높음",
    difficulty: "상",
    recommendedOwner: "김지훈",
    status: "미배정",
  },
  {
    id: 6,
    text: "공사 단계별 소음 저감 대책을 제시해야 합니다.",
    category: "환경",
    priority: "중간",
    difficulty: "중",
    recommendedOwner: "이서연",
    status: "완료",
  },
];

export const TEAM: TeamMember[] = [
  { id: "m1", name: "김지훈", role: "인프라 엔지니어", done: 6, total: 9 },
  { id: "m2", name: "이서연", role: "환경 컨설턴트", done: 4, total: 7 },
  { id: "m3", name: "박민수", role: "보안 담당", done: 2, total: 5 },
  { id: "m4", name: "최예나", role: "운영 담당", done: 3, total: 4 },
];

export const RISKS: RiskItem[] = [
  {
    id: "r1",
    title: "공급망 지연",
    level: "높음",
    description:
      "주요 자재 조달 리드타임이 8주 이상으로 예상되어 착공 일정에 영향을 줄 수 있습니다.",
  },
  {
    id: "r2",
    title: "규정 준수 미흡",
    level: "높음",
    description:
      "폐수 처리 및 대기질 기준이 최신 환경 규정과 부분적으로 불일치합니다.",
  },
  {
    id: "r3",
    title: "구조적 실패 위험",
    level: "중간",
    description:
      "지반 조사 데이터가 일부 구역에서 부족하여 구조 안정성 검토가 필요합니다.",
  },
];

export const ASSIGNEES = ["김지훈", "이서연", "박민수", "최예나"];

export const TASKS: Task[] = [
  {
    id: "t1",
    title: "입찰 전략 API 최적화",
    column: "todo",
    priority: "높음",
    due: "2026-07-08",
    assignee: "나",
    relatedReq: "RFP 4.1 기술 요건",
  },
  {
    id: "t2",
    title: "LLM 피드백 루프 연동",
    column: "todo",
    priority: "중간",
    due: "2026-07-12",
    assignee: "나",
    relatedReq: "RFP 5.3 운영 요건",
  },
  {
    id: "t3",
    title: "운영 로그 정리",
    column: "doing",
    priority: "낮음",
    due: "2026-07-05",
    assignee: "나",
    relatedReq: "RFP 5.1 유지보수",
  },
  {
    id: "t4",
    title: "RFP V3 데이터셋 검토",
    column: "doing",
    priority: "중간",
    due: "2026-07-06",
    assignee: "나",
    relatedReq: "RFP 2.4 데이터 요건",
  },
  {
    id: "t5",
    title: "UI 리팩토링: 업무 보드",
    column: "review",
    priority: "중간",
    due: "2026-07-03",
    assignee: "나",
    relatedReq: "RFP 6.2 사용성",
  },
  {
    id: "t6",
    title: "컴플라이언스 매트릭스 초안",
    column: "done",
    priority: "높음",
    due: "2026-06-28",
    assignee: "나",
    relatedReq: "RFP 3.2 환경 규정",
  },
  {
    id: "t7",
    title: "3.2 환경 규정 준수 초안 작성",
    column: "doing",
    priority: "높음",
    due: "2026-07-04",
    assignee: "나",
    relatedReq: "RFP 3.2 환경 규정 준수",
  },
];

export const STAFF_FEEDBACK: Feedback[] = [
  {
    id: "f1",
    author: "PM 정하늘",
    date: "2026-06-29",
    text: "환경영향 완화 단락의 근거 데이터를 RFP 3.2 기준으로 보강해 주세요.",
  },
  {
    id: "f2",
    author: "PM 정하늘",
    date: "2026-06-27",
    text: "컴플라이언스 매트릭스 초안 잘 확인했습니다. 검토 완료 처리합니다.",
  },
];

export const AI_TASK_HELPER: string[] = [
  "다음 작업: 환경영향 완화 방안 초안을 작성하세요.",
  "이 업무는 RFP 3.2 환경 규정 준수 항목과 연결됩니다.",
];

export const AI_TASK_SUMMARY: string[] = [
  "이 업무는 환경 규정 준수 항목과 관련됩니다.",
  "RFP의 배수 처리, 대기질 완화, 폐기물 처리 조건을 반영해야 합니다.",
  "다음 작업: 환경영향 완화 단락 초안을 작성하세요.",
];

export const TASK_CHECKLIST = [
  { id: "c1", label: "RFP 3.2 환경 규정 원문 검토", done: true },
  { id: "c2", label: "배수 처리 완화 방안 정리", done: true },
  { id: "c3", label: "대기질 완화 단락 초안 작성", done: false },
  { id: "c4", label: "폐기물 처리 조건 반영", done: false },
  { id: "c5", label: "최종 검토 요청 제출", done: false },
];

export const WORKFLOW_STEPS = [
  "PM RFP 업로드",
  "AI 요구사항 추출",
  "PM 업무 배정",
  "직원 업무 수행",
  "직원 산출물 제출",
  "PM 검토 및 피드백",
  "직원 수정 후 완료",
];

/* =====================================================================
 * 공고문 업로드 / 검토 / 산출물 제출 (미연결 기능 복원용)
 * ===================================================================*/

export interface UploadedRfp {
  id: string;
  name: string;
  size: string;
  uploadedAt: string;
  status: "분석 완료" | "분석 중" | "대기";
  requirementCount: number;
}

export const UPLOADED_RFPS: UploadedRfp[] = [
  { id: "rfp1", name: "도시인프라-rfp-2024.pdf", size: "4.2MB", uploadedAt: "오늘 09:10", status: "분석 완료", requirementCount: 24 },
  { id: "rfp2", name: "상수도-정비-공고문.pdf", size: "2.8MB", uploadedAt: "어제 16:40", status: "분석 완료", requirementCount: 17 },
  { id: "rfp3", name: "교통관제-시스템-RFP.pdf", size: "5.1MB", uploadedAt: "2026-07-10 11:05", status: "대기", requirementCount: 0 },
];

export type ReviewState = "검토 대기" | "승인" | "반려";

export interface ReviewSubmission {
  id: string;
  title: string;
  author: string;
  submittedAt: string;
  relatedReq: string;
  attachment: string;
  state: ReviewState;
}

export const REVIEW_SUBMISSIONS: ReviewSubmission[] = [
  { id: "sub1", title: "3.2 환경 규정 준수 초안", author: "이서연", submittedAt: "2026-07-01 14:20", relatedReq: "RFP 3.2 환경 규정", attachment: "환경규정_초안_v2.docx", state: "검토 대기" },
  { id: "sub2", title: "전력 공급 이중화 설계 기준", author: "김지훈", submittedAt: "2026-06-30 18:05", relatedReq: "RFP 4.1 인프라", attachment: "이중화설계_기준.pdf", state: "검토 대기" },
  { id: "sub3", title: "보안 펜스 설치 계획", author: "박민수", submittedAt: "2026-06-29 10:11", relatedReq: "RFP 5.2 보안", attachment: "보안펜스_계획.docx", state: "승인" },
  { id: "sub4", title: "승강 설비 유지보수 일정", author: "최예나", submittedAt: "2026-06-28 09:40", relatedReq: "RFP 6.1 일정", attachment: "유지보수_일정.xlsx", state: "반려" },
];

export interface SubmittableTask {
  id: string;
  title: string;
  relatedReq: string;
  due: string;
  status: "작성 중" | "제출 완료";
}

export const STAFF_SUBMITTABLE: SubmittableTask[] = [
  { id: "st1", title: "3.2 환경 규정 준수 초안 작성", relatedReq: "RFP 3.2 환경 규정 준수", due: "2026-07-04", status: "작성 중" },
  { id: "st2", title: "폐수 관리 영향 보고서", relatedReq: "RFP 3.1 폐수 관리", due: "2026-07-08", status: "작성 중" },
  { id: "st3", title: "컴플라이언스 매트릭스 초안", relatedReq: "RFP 3.2 환경 규정", due: "2026-06-28", status: "제출 완료" },
];

/* =====================================================================
 * 문서 통합 관리 (Document Integration Management)
 * ===================================================================*/

export type FileKind = "pdf" | "word" | "excel" | "ppt" | "image" | "figma";
export type GenStatus = "생성 완료" | "오늘 업데이트" | "생성 중";

export interface AiGeneratedFile {
  id: string;
  name: string;
  kind: FileKind;
  createdAt: string;
  status: GenStatus;
}

export interface LibraryFile {
  id: string;
  name: string;
  kind: FileKind;
  category: string;
  updatedAt: string;
}

export interface GeneratedArtifact {
  id: string;
  title: string;
  meta: string;
  kind: FileKind;
}

export interface AnalysisStat {
  id: string;
  label: string;
  value: string;
}

export interface ChatMessage {
  id: string;
  sender: "user" | "ai";
  text: string;
  bullets?: string[];
  time: string;
}

// PM 문서 통합 관리 (이미지 1)
export const PM_AI_FILES: AiGeneratedFile[] = [
  { id: "af1", name: "요구사항_분석_요약.pdf", kind: "pdf", createdAt: "오늘 10:32", status: "생성 완료" },
  { id: "af2", name: "프로젝트_목표_마일스톤.docx", kind: "word", createdAt: "오늘 10:31", status: "생성 완료" },
  { id: "af3", name: "MC일정계획_WBS.xlsx", kind: "excel", createdAt: "오늘 10:31", status: "생성 완료" },
  { id: "af4", name: "기능명세서_초안.docx", kind: "word", createdAt: "오늘 10:30", status: "생성 완료" },
  { id: "af5", name: "ERD_초안.png", kind: "image", createdAt: "오늘 10:30", status: "오늘 업데이트" },
  { id: "af6", name: "UI프로토타입_초안.fig", kind: "figma", createdAt: "오늘 10:30", status: "오늘 업데이트" },
];

export const PM_LIBRARY_FILES: LibraryFile[] = [
  { id: "lf1", name: "RFP_신제품출시_v2.pdf", kind: "pdf", category: "RFP", updatedAt: "2025.07.01 16:24" },
  { id: "lf2", name: "요구사항정의서_v1.3.docx", kind: "word", category: "요구사항", updatedAt: "2025.07.01 14:10" },
  { id: "lf3", name: "주간회의록_0701.docx", kind: "word", category: "회의록", updatedAt: "2025.07.01 11:05" },
  { id: "lf4", name: "프로젝트_제안서.pdf", kind: "pdf", category: "제안서", updatedAt: "2025.06.30 18:42" },
  { id: "lf5", name: "7월_스크럼보고서.docx", kind: "word", category: "보고서", updatedAt: "2025.07.01 09:18" },
  { id: "lf6", name: "결정사항_로그.xlsx", kind: "excel", category: "로그", updatedAt: "2025.07.01 08:50" },
];

export const PM_PLANNING_AGENTS = [
  "초기 문서 분석",
  "요구사항 추출",
  "프로젝트 목표/마일스톤",
  "MC 일정 계획",
  "WBS 생성",
  "기능 명세서 초안",
  "ERD 초안",
  "UI 프로토타입 추천",
];

export const PM_REPORT_AGENTS = [
  "회의록 요약",
  "주간 스크럼 보고서",
  "결정사항 로그 생성",
  "산출물 요약",
  "RAG 챗봇 질의",
];

export const PM_GENERATED_ARTIFACTS: GeneratedArtifact[] = [
  { id: "ga1", title: "요구사항 목록", meta: "12건", kind: "word" },
  { id: "ga2", title: "프로젝트 목표/마일스톤", meta: "8건/4건", kind: "word" },
  { id: "ga3", title: "MC 일정 계획", meta: "간트 차트", kind: "excel" },
  { id: "ga4", title: "WBS 초안", meta: "작업 분해 구조", kind: "excel" },
  { id: "ga5", title: "기능 명세서 초안", meta: "내용 요약", kind: "word" },
  { id: "ga6", title: "ERD 초안", meta: "이미지", kind: "image" },
  { id: "ga7", title: "UI 프로토타입 초안", meta: "스크린 흐름", kind: "figma" },
  { id: "ga8", title: "결정사항 로그", meta: "5건", kind: "excel" },
];

export const PM_ANALYSIS_STATS: AnalysisStat[] = [
  { id: "s1", label: "추출 요구사항", value: "12건" },
  { id: "s2", label: "생성 파일", value: "8건" },
  { id: "s3", label: "회의록 반영", value: "1건" },
  { id: "s4", label: "추천 후속 작업", value: "3건" },
];

export const PM_CHAT_HISTORY: ChatMessage[] = [
  {
    id: "cm1",
    sender: "user",
    text: "RFP와 요구사항 정의서를 분석해서 요구사항, 목표, 마일스톤, 일정 계획, WBS를 정리해줘. 기능명세서와 ERD, UI 프로토타입 초안도 생성해줘.",
    time: "오전 10:33",
  },
  {
    id: "cm2",
    sender: "ai",
    text: "초기 문서 분석이 완료되었습니다.",
    bullets: [
      "핵심 요구사항 12건 추출",
      "프로젝트 목표 3건 및 마일스톤 4건 정리",
      "MC 방법론 기반 일정 계획 및 WBS 초안 생성",
      "기능 명세서, ERD, UI 프로토타입 초안 생성 완료",
    ],
    time: "오전 10:35",
  },
];

// 직원 문서 통합 관리 (이미지 3)
export interface SharedDoc {
  id: string;
  name: string;
  kind: FileKind;
  owner: string;
  sharedAt: string;
}

export interface MyDoc {
  id: string;
  name: string;
  kind: FileKind;
  category: string;
  updatedAt: string;
}

export type ReviewStatus = "수정 중" | "검토 요청" | "승인 대기" | "승인 완료";

export interface ReviewActivity {
  id: string;
  text: string;
  sub: string;
  author: string;
  status: ReviewStatus;
}

export const STAFF_SHARED_DOCS: SharedDoc[] = [
  { id: "sd1", name: "디자인_시스템_가이드_v2.pdf", kind: "pdf", owner: "김정수", sharedAt: "2025.07.02" },
  { id: "sd2", name: "브랜딩_시안_24_07_01.docx", kind: "word", owner: "이영희", sharedAt: "2025.07.01" },
  { id: "sd3", name: "모바일_UI_개선안_최종.xlsx", kind: "excel", owner: "강철수", sharedAt: "2025.07.01" },
  { id: "sd4", name: "기획서_최종_안.docx", kind: "word", owner: "이영희", sharedAt: "2025.07.02" },
  { id: "sd5", name: "ERD_초안.png", kind: "image", owner: "이영희", sharedAt: "2025.07.01" },
  { id: "sd6", name: "UI_마이크_스타일가이드.pdf", kind: "pdf", owner: "김정수", sharedAt: "2025.07.02" },
];

export const STAFF_MY_DOCS: MyDoc[] = [
  { id: "md1", name: "모바일_배너UI_개선안.fig", kind: "figma", category: "기획서", updatedAt: "2025.07.01 16:24" },
  { id: "md2", name: "UI_아이콘_스타일가이드.pdf", kind: "pdf", category: "디자인", updatedAt: "2025.07.01 14:10" },
  { id: "md3", name: "Q3_타라인_업데이트_최종.docx", kind: "word", category: "기능개선", updatedAt: "2025.07.01 11:05" },
  { id: "md4", name: "프로젝트_제안서.pptx", kind: "ppt", category: "제안서", updatedAt: "2025.07.01 16:42" },
  { id: "md5", name: "7월_스타일보고서.docx", kind: "word", category: "보고서", updatedAt: "2025.07.01 08:16" },
  { id: "md6", name: "결장사업_로그.xlsx", kind: "excel", category: "로그", updatedAt: "2025.07.01 08:50" },
];

export const STAFF_ASSET_ICONS = [
  "heart", "map-pin", "message-square", "image", "grid", "layout",
  "bell", "home", "message-circle", "file", "tag", "star",
];

export const STAFF_REVIEW_ACTIVITY: ReviewActivity[] = [
  { id: "rv1", text: "최근 댓글: '디자인_시스템_가이드_v2.pdf' 보고 수정했습니다.", sub: "강철수 · 믿고보댐 성정", author: "강철수", status: "수정 중" },
  { id: "rv2", text: "브랜딩_시스템_가이드_v1.docx 리뷰 요청합니다.", sub: "이영희 · 디자인 팀", author: "이영희", status: "검토 요청" },
  { id: "rv3", text: "Q3_디자인_업데이트_최종.docx 리뷰 요청합니다.", sub: "최근 리뷰: 목대명님이 일부내 내용 수정 제안습니다.", author: "이영희", status: "승인 대기" },
  { id: "rv4", text: "7월_스타일보고서.docx 승인 요청합니다.", sub: "최근 에셋 라이브러리 업데이트 확인 부탁드립니다.", author: "이영희", status: "승인 대기" },
  { id: "rv5", text: "프로젝트_제안서.pdf 승인 요청합니다.", sub: "용다영 · 디자인 원형", author: "용다영", status: "승인 완료" },
];

/* =====================================================================
 * 리스크 관리 (Risk Management)
 * ===================================================================*/

export type RiskSeverity = "심각" | "주의" | "정보";

export interface RiskDetection {
  id: string;
  service: string;
  source: string;
  detectedAt: string;
  severity: RiskSeverity;
  description: string;
}

export interface TeamCommItem {
  id: string;
  kind: "message" | "system" | "notice";
  title: string;
  body: string;
  time: string;
  unread: boolean;
}

export type OpenRiskState = "미해결" | "처리 중" | "대기 중" | "주의";

export interface OpenRisk {
  id: string;
  title: string;
  service: string;
  serviceIcon: "github" | "jira" | "cloudwatch";
  occurredAt: string;
  state: OpenRiskState;
  severity: RiskSeverity | null;
}

export interface RiskSolution {
  id: string;
  item: string;
  sub: string;
  owner: string;
  eta: string;
  automated: boolean;
}

// 직원/공통 리스크 탐지 현황 (이미지 2)
export const RISK_DETECTIONS: RiskDetection[] = [
  {
    id: "ID-001",
    service: "회원가입 페이지",
    source: "FE (프론트엔드)",
    detectedAt: "2026.07.10 09:15",
    severity: "심각",
    description: "최근 커밋에서 SQL 인젝션 취약점 탐지",
  },
  {
    id: "ID-002",
    service: "결제 API",
    source: "BE (백엔드)",
    detectedAt: "2026.07.10 08:30",
    severity: "주의",
    description: "서버 응답 시간 지연 및 리소스 사용량 증가",
  },
  {
    id: "ID-003",
    service: "AWS S3",
    source: "CLOUD",
    detectedAt: "2026.07.09 17:00",
    severity: "정보",
    description: "S3 버킷 권한이 공개로 변경됨",
  },
];

export const TEAM_COMMS: TeamCommItem[] = [
  {
    id: "tc1",
    kind: "message",
    title: "리스크 ID-001 | 강철수 (보안 엔지니어)",
    body: "S3 버킷 권한 수정 완료. 확인 부탁드립니다.",
    time: "10:45 AM",
    unread: true,
  },
  {
    id: "tc2",
    kind: "message",
    title: "리스크 ID-002 | 이영회 (개발)",
    body: "API 최적화 패치 적용 중. 성능 테스트 결과 양호.",
    time: "10:15 AM",
    unread: true,
  },
  {
    id: "tc3",
    kind: "system",
    title: "새 리스크 탐지 | 'System'",
    body: "회원가입 페이지에서 SQL 주입 패턴 탐지됨. (ID-001)",
    time: "9:15 AM",
    unread: false,
  },
  {
    id: "tc4",
    kind: "notice",
    title: "권한 변경 알림 | 김영마 (DevOps)",
    body: "AWS S3 권한 설정 변경 완료. (ID-003)",
    time: "8:15 AM",
    unread: false,
  },
];

export const OPEN_RISKS: OpenRisk[] = [
  { id: "or1", title: "리스크 ID-001 '회원가입 SQL 주입'", service: "GitHub", serviceIcon: "github", occurredAt: "1시간 전", state: "미해결", severity: "심각" },
  { id: "or2", title: "리스크 ID-001 '결제 API 성능 저하'", service: "Jira (티켓)", serviceIcon: "jira", occurredAt: "2시간 전", state: "처리 중", severity: null },
  { id: "or3", title: "리스크 ID-002 '결제 API 성능 저하'", service: "Jira (티켓)", serviceIcon: "jira", occurredAt: "주의", state: "대기 중", severity: null },
  { id: "or4", title: "리스크 ID-003 'AWS S3 권한 노출'", service: "CloudWatch", serviceIcon: "cloudwatch", occurredAt: "어제", state: "처리 중", severity: null },
];

export const RISK_SOLUTIONS: RiskSolution[] = [
  { id: "rs1", item: "AI 추천 해결책: SQL 주입 방지 코드 패치 (GitHub 연동)", sub: "GitHub 연동", owner: "김민석", eta: "30분 전", automated: true },
  { id: "rs2", item: "AI 분석 결과: API 서버 스케일링 권장 (Jira 티켓 생성)", sub: "Jira 티켓 생성", owner: "김민석", eta: "1시간 전", automated: true },
  { id: "rs3", item: "자동화된 조치: S3 버킷 퍼블릭 액세스 차단 (CLOUD)", sub: "CLOUD", owner: "System 자동", eta: "어제", automated: true },
];

// PM 리스크 상세 (이미지 4)
export interface RiskKpi {
  id: string;
  label: string;
  value: string;
  sub: string;
  tone: "info" | "danger" | "warn" | "success";
}

export const PM_RISK_KPIS: RiskKpi[] = [
  { id: "k1", label: "전체 리스크", value: "8건", sub: "AI 탐지 기준", tone: "info" },
  { id: "k2", label: "긴급 대응", value: "2건", sub: "즉시 조치 필요", tone: "danger" },
  { id: "k3", label: "법·가이드 검토", value: "3건 확인 필요", sub: "검토 대기 중", tone: "warn" },
  { id: "k4", label: "예상 영향 일정", value: "+4일", sub: "전체 일정 영향 예상", tone: "success" },
];

export type PmRiskState = "미조치" | "검토중" | "분석 완료" | "체크리스트 생성" | "산출 완료";
export type PmRiskPriority = "긴급" | "주의" | "보통";

export interface PmRiskRow {
  id: number;
  type: string;
  target: string;
  targetIcon: "doc" | "calendar" | "edit" | "user" | "chart";
  state: PmRiskState;
  priority: PmRiskPriority;
  impact: string;
}

export const PM_RISK_ROWS: PmRiskRow[] = [
  { id: 1, type: "개인정보 포함 문서", target: "주간회의록_0701.docx", targetIcon: "doc", state: "미조치", priority: "긴급", impact: "보안" },
  { id: 2, type: "노동법 검토 필요", target: "개발 일정 계획안", targetIcon: "calendar", state: "검토중", priority: "주의", impact: "인력/일정" },
  { id: 3, type: "요구사항 변경 영향", target: "결제 기능 범위 확대", targetIcon: "edit", state: "분석 완료", priority: "긴급", impact: "일정/WBS" },
  { id: 4, type: "담당자 변경 리스크", target: "Backend 담당자 교체", targetIcon: "user", state: "체크리스트 생성", priority: "주의", impact: "인수인계" },
  { id: 5, type: "예상 견적 변동", target: "신규 기능 추가 요청", targetIcon: "chart", state: "산출 완료", priority: "보통", impact: "비용" },
];

export const PM_RISK_COMMENT =
  "공유 문서에서 개인정보가 포함된 회의록 1건이 탐지되었으며, 요구사항 변경으로 인해 결제 기능 WBS와 태스크 일정에 연쇄 영향이 예상됩니다. 또한 인력 변경 예정으로 백엔드 영역 인수인계 누락 가능성이 있어 체크리스트 기반 대응이 필요합니다.";

export const PM_RISK_COMMENT_TAGS = [
  { id: "t1", label: "개인정보 1건" },
  { id: "t2", label: "기밀 문서 1건" },
  { id: "t3", label: "요구사항 변경 영향 3건" },
  { id: "t4", label: "인수인계 필요 1건" },
];

export const PM_LABOR_CHECKS = [
  { id: "lc1", label: "주52시간 기준 초과 가능성: 주의", ok: false },
  { id: "lc2", label: "야간 작업 수당 반영 필요", ok: true },
  { id: "lc3", label: "사내 보안 가이드 준수 확인", ok: true },
];

export const PM_PRIVACY_ITEMS = [
  { id: "p1", label: "전화번호", count: "2건" },
  { id: "p2", label: "이메일", count: "1건" },
  { id: "p3", label: "기밀 키워드", count: "1건" },
];

export interface HandoverCheck {
  id: string;
  label: string;
  done: boolean;
}

export const PM_HANDOVER_CHECKS: HandoverCheck[] = [
  { id: "h1", label: "코드 저장소 권한 이전", done: true },
  { id: "h2", label: "API 앤셀 공유", done: true },
  { id: "h3", label: "미완료 업무 인계", done: false },
  { id: "h4", label: "태스트 이슈 정리", done: false },
  { id: "h5", label: "회의록/결정사항 전달", done: false },
];

export const PM_RISK_ACTIONS = [
  { id: "ra1", title: "문서 마스킹 적용", sub: "개인정보 즉시 보호 조치" },
  { id: "ra2", title: "일정 재계산", sub: "영향 일정 자동 재계산" },
  { id: "ra3", title: "견적 재산출", sub: "변동사항 반영한 견적 산출" },
  { id: "ra4", title: "인수인계 미니 회의 생성", sub: "체크리스트 기반 회의 개설" },
];

export const PM_QUICK_TOOLS = [
  { id: "qt1", label: "담당자 알림" },
  { id: "qt2", label: "리스크 코멘트" },
  { id: "qt3", label: "보류" },
  { id: "qt4", label: "완료 처리" },
];
