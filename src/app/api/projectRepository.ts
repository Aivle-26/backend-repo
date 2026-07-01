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

