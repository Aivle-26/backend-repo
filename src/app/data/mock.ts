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
