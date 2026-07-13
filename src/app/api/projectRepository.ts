import {
  AI_SUMMARY,
  AI_TASK_HELPER,
  AI_TASK_SUMMARY,
  ASSIGNEES,
  KPI_PM,
  KPI_STAFF,
  PROJECT_NAME,
  REQUIREMENTS,
  RISKS,
  STAFF_FEEDBACK,
  TASKS,
  TASK_CHECKLIST,
  TEAM,
  WORKFLOW_STEPS,
  PM_AI_FILES,
  PM_LIBRARY_FILES,
  PM_PLANNING_AGENTS,
  PM_REPORT_AGENTS,
  PM_GENERATED_ARTIFACTS,
  PM_ANALYSIS_STATS,
  PM_CHAT_HISTORY,
  STAFF_SHARED_DOCS,
  STAFF_MY_DOCS,
  STAFF_ASSET_ICONS,
  STAFF_REVIEW_ACTIVITY,
  RISK_DETECTIONS,
  TEAM_COMMS,
  OPEN_RISKS,
  RISK_SOLUTIONS,
  PM_RISK_KPIS,
  PM_RISK_ROWS,
  PM_RISK_COMMENT,
  PM_RISK_COMMENT_TAGS,
  PM_LABOR_CHECKS,
  PM_PRIVACY_ITEMS,
  PM_HANDOVER_CHECKS,
  PM_RISK_ACTIONS,
  PM_QUICK_TOOLS,
  type Role,
  type Task,
  type TaskColumn,
} from "@/app/data/demoData";

export type { Role, Task, TaskColumn };

export interface AssignRequirementInput {
  requirementId: number;
  assignee: string;
  dueDate?: string;
  memo?: string;
}

export interface SubmitReviewInput {
  taskId?: string;
  attachmentUrl?: string;
}

export interface AddCommentInput {
  taskId?: string;
  text: string;
}

export const projectRepository = {
  getProjectName() {
    return PROJECT_NAME;
  },

  getWorkflowSteps() {
    return WORKFLOW_STEPS;
  },

  getPmDashboard() {
    return {
      kpis: KPI_PM,
      aiSummary: AI_SUMMARY,
      requirements: REQUIREMENTS,
      team: TEAM,
      risks: RISKS,
    };
  },

  getPmAnalysis() {
    return {
      requirements: REQUIREMENTS,
      risks: RISKS,
      assignees: ASSIGNEES,
    };
  },

  getStaffDashboard() {
    return {
      kpis: KPI_STAFF,
      tasks: TASKS,
      aiHelper: AI_TASK_HELPER,
      feedback: STAFF_FEEDBACK,
      requirements: REQUIREMENTS,
    };
  },

  getTaskDetail() {
    return {
      checklist: TASK_CHECKLIST,
      aiSummary: AI_TASK_SUMMARY,
      feedback: STAFF_FEEDBACK,
    };
  },

  getPmDocuments() {
    return {
      aiFiles: PM_AI_FILES,
      libraryFiles: PM_LIBRARY_FILES,
      planningAgents: PM_PLANNING_AGENTS,
      reportAgents: PM_REPORT_AGENTS,
      artifacts: PM_GENERATED_ARTIFACTS,
      stats: PM_ANALYSIS_STATS,
      chatHistory: PM_CHAT_HISTORY,
    };
  },

  getStaffDocuments() {
    return {
      sharedDocs: STAFF_SHARED_DOCS,
      myDocs: STAFF_MY_DOCS,
      assetIcons: STAFF_ASSET_ICONS,
      reviewActivity: STAFF_REVIEW_ACTIVITY,
    };
  },

  getRiskBoard() {
    return {
      detections: RISK_DETECTIONS,
      teamComms: TEAM_COMMS,
      openRisks: OPEN_RISKS,
      solutions: RISK_SOLUTIONS,
    };
  },

  getPmRisk() {
    return {
      kpis: PM_RISK_KPIS,
      rows: PM_RISK_ROWS,
      comment: PM_RISK_COMMENT,
      commentTags: PM_RISK_COMMENT_TAGS,
      laborChecks: PM_LABOR_CHECKS,
      privacyItems: PM_PRIVACY_ITEMS,
      handoverChecks: PM_HANDOVER_CHECKS,
      actions: PM_RISK_ACTIONS,
      quickTools: PM_QUICK_TOOLS,
    };
  },

  async uploadRfp() {
    return { ok: true };
  },

  async reanalyzeRfp() {
    return { ok: true };
  },

  async assignRequirement(_input: AssignRequirementInput) {
    return { ok: true };
  },

  async requestReview(_input?: SubmitReviewInput) {
    return { ok: true };
  },

  async attachFile(_input?: SubmitReviewInput) {
    return { ok: true };
  },

  async addComment(_input: AddCommentInput) {
    return { ok: true };
  },
};

