import { useEffect, useState } from "react";
import {
  LayoutDashboard,
  UploadCloud,
  Sparkles,
  FileText,
  Users,
  AlertTriangle,
  ClipboardCheck,
  ListTodo,
  BookOpen,
  Send,
  MessageSquareReply,
  MessagesSquare,
  FolderKanban,
} from "lucide-react";
import { Toaster } from "@/app/components/ui/sonner";
import { Sidebar, type SidebarItem } from "@/app/components/layout/Sidebar";
import { TopBar } from "@/app/components/layout/TopBar";
import { LoginScreen } from "@/app/components/auth/LoginScreen";
import { PmAnalysis } from "@/app/components/pm/PmAnalysis";
import { PmDocuments } from "@/app/components/pm/PmDocuments";
import { PmRisk } from "@/app/components/pm/PmRisk";
import { PmUpload } from "@/app/components/pm/PmUpload";
import { PmReview } from "@/app/components/pm/PmReview";
import { PmRequirements } from "@/app/components/pm/PmRequirements";
import { PmAssign } from "@/app/components/pm/PmAssign";
import { ProjectBoard } from "@/app/components/pm/ProjectBoard";
import { ProjectDetail } from "@/app/components/pm/ProjectDetail";
import { ProjectWizard } from "@/app/components/pm/ProjectWizard";
import { ProjectExtraction } from "@/app/components/pm/ProjectExtraction";
import { StaffDashboard } from "@/app/components/staff/StaffDashboard";
import { StaffTaskDetail } from "@/app/components/staff/StaffTaskDetail";
import { StaffDocuments } from "@/app/components/staff/StaffDocuments";
import { StaffRisk, StaffRiskActions } from "@/app/components/staff/StaffRisk";
import { StaffContext } from "@/app/components/staff/StaffContext";
import { StaffSubmit } from "@/app/components/staff/StaffSubmit";
import { StaffFeedback } from "@/app/components/staff/StaffFeedback";
import { StaffComments } from "@/app/components/staff/StaffComments";
import { SlackIntegration } from "@/app/components/integrations/SlackIntegration";
import type { Role } from "@/app/api/projectRepository";
import { slackApi } from "@/app/api/slackApi";
import {
  PROJECTS,
  type ProjectRequirement,
  type ProjectSummary,
} from "@/app/data/demoData";

const PM_MENU: SidebarItem[] = [
  { key: "dashboard", label: "프로젝트", icon: LayoutDashboard },
  { key: "upload", label: "공고문 업로드", icon: UploadCloud },
  { key: "analysis", label: "AI 분석", icon: Sparkles },
  { key: "requirements", label: "요구사항", icon: FileText },
  { key: "assign", label: "업무 배정", icon: Users },
  { key: "documents", label: "문서 통합 관리", icon: FolderKanban },
  { key: "risk", label: "리스크", icon: AlertTriangle },
  { key: "review", label: "검토", icon: ClipboardCheck },
];

const STAFF_MENU: SidebarItem[] = [
  { key: "tasks", label: "내 업무", icon: ListTodo },
  { key: "context", label: "RFP 맥락", icon: BookOpen },
  { key: "documents", label: "문서 통합 관리", icon: FolderKanban },
  { key: "risk", label: "리스크", icon: AlertTriangle },
  { key: "submit", label: "산출물 제출", icon: Send },
  { key: "feedback", label: "피드백", icon: MessageSquareReply },
  { key: "comments", label: "댓글", icon: MessagesSquare },
];

export default function App() {
  const [role, setRole] = useState<Role | null>(() => {
    const saved =
      typeof window !== "undefined" ? localStorage.getItem("app-role") : null;
    return saved === "pm" || saved === "staff" ? saved : null;
  });
  const [pmMenu, setPmMenu] = useState("dashboard");
  const [staffMenu, setStaffMenu] = useState("tasks");
  const [taskOpen, setTaskOpen] = useState(false);
  const [projects, setProjects] = useState<ProjectSummary[]>(PROJECTS);
  const [pmDetail, setPmDetail] = useState<ProjectSummary | null>(null);
  const [pmWizard, setPmWizard] = useState<ProjectSummary | null>(null);
  const [pmExtract, setPmExtract] = useState<ProjectSummary | null>(null);

  const startProject = (id: string) =>
    setProjects((prev) =>
      prev.map((p) =>
        p.id === id
          ? { ...p, status: "진행중", progress: Math.max(p.progress, 5) }
          : p,
      ),
    );

  // Slack OAuth 콜백(?session=)으로 돌아오면 세션 저장 후 연동 화면으로 이동
  useEffect(() => {
    if (slackApi.captureSessionFromUrl()) {
      setPmMenu("slack");
      setStaffMenu("slack");
    }
  }, []);

  const handleLogin = (r: Role) => {
    setRole(r);
    try {
      localStorage.setItem("app-role", r);
    } catch {
      /* ignore */
    }
    setPmMenu("dashboard");
    setStaffMenu("tasks");
    setTaskOpen(false);
  };

  const handleLogout = () => {
    setRole(null);
    try {
      localStorage.removeItem("app-role");
    } catch {
      /* ignore */
    }
  };

  if (!role) {
    return (
      <>
        <LoginScreen onLogin={handleLogin} />
        <Toaster />
      </>
    );
  }

  const isPm = role === "pm";
  const menu = isPm ? PM_MENU : STAFF_MENU;
  const activeMenu = isPm ? pmMenu : staffMenu;

  const handleSelect = (key: string) => {
    if (isPm) {
      setPmMenu(key);
      setPmDetail(null);
      setPmWizard(null);
      setPmExtract(null);
    } else {
      setStaffMenu(key);
      setTaskOpen(false);
    }
  };

  let subtitle = "";
  let body: React.ReactNode = null;
  let actions: React.ReactNode = null;

  if (isPm) {
    if (pmMenu === "slack") {
      subtitle = "Slack 연동";
      body = <SlackIntegration />;
    } else if (pmMenu === "upload") {
      subtitle = "공고문 업로드";
      body = <PmUpload />;
    } else if (pmMenu === "documents") {
      subtitle = "문서 통합 관리";
      body = <PmDocuments />;
    } else if (pmMenu === "risk") {
      subtitle = "리스크 관리";
      body = <PmRisk />;
    } else if (pmMenu === "review") {
      subtitle = "산출물 검토";
      body = <PmReview />;
    } else if (pmMenu === "requirements") {
      subtitle = "요구사항";
      body = <PmRequirements />;
    } else if (pmMenu === "assign") {
      subtitle = "업무 배정";
      body = <PmAssign />;
    } else if (pmMenu === "analysis") {
      subtitle = "AI 분석";
      body = <PmAnalysis />;
    } else if (pmExtract) {
      subtitle = `${pmExtract.name} · AI 추출`;
      body = (
        <ProjectExtraction
          project={pmExtract}
          onBack={() => setPmExtract(null)}
          onConfirm={(requirements: ProjectRequirement[]) => {
            const updated: ProjectSummary = {
              ...pmExtract,
              status: "준비",
              requirements,
              reqCount: requirements.length,
              wizardStep: 1,
              updatedAt: "방금",
            };
            setProjects((prev) =>
              prev.map((p) => (p.id === updated.id ? updated : p)),
            );
            setPmExtract(null);
            setPmWizard(updated);
          }}
        />
      );
    } else if (pmWizard) {
      subtitle = `${pmWizard.name} · 준비`;
      body = (
        <ProjectWizard
          project={pmWizard}
          onBack={() => setPmWizard(null)}
          onStart={(id) => {
            startProject(id);
            setPmWizard(null);
          }}
        />
      );
    } else if (pmDetail) {
      subtitle = `${pmDetail.name} · 운영`;
      body = (
        <ProjectDetail
          project={pmDetail}
          onBack={() => setPmDetail(null)}
          onUpdateDocs={(docs) => {
            const updated: ProjectSummary = { ...pmDetail, docs, updatedAt: "방금" };
            setProjects((prev) =>
              prev.map((p) => (p.id === updated.id ? updated : p)),
            );
            setPmDetail(updated);
          }}
        />
      );
    } else {
      subtitle = "프로젝트";
      body = (
        <ProjectBoard
          projects={projects}
          setProjects={setProjects}
          onOpenOperational={(p) => {
            setPmWizard(null);
            setPmDetail(p);
          }}
          onOpenWizard={(p) => {
            setPmDetail(null);
            setPmWizard(p);
          }}
          onExtract={(p) => {
            setPmDetail(null);
            setPmWizard(null);
            setPmExtract(p);
          }}
        />
      );
    }
  } else {
    if (staffMenu === "slack") {
      subtitle = "Slack 연동";
      body = <SlackIntegration />;
    } else if (staffMenu === "documents") {
      subtitle = "문서 통합 관리";
      body = <StaffDocuments />;
    } else if (staffMenu === "risk") {
      subtitle = "리스크";
      body = <StaffRisk />;
      actions = <StaffRiskActions />;
    } else if (staffMenu === "context") {
      subtitle = "RFP 맥락";
      body = <StaffContext />;
    } else if (staffMenu === "submit") {
      subtitle = "산출물 제출";
      body = <StaffSubmit />;
    } else if (staffMenu === "feedback") {
      subtitle = "피드백";
      body = <StaffFeedback />;
    } else if (staffMenu === "comments") {
      subtitle = "댓글";
      body = <StaffComments />;
    } else if (taskOpen) {
      subtitle = "업무 상세";
      body = <StaffTaskDetail onBack={() => setTaskOpen(false)} />;
    } else {
      subtitle = "직원 대시보드";
      body = <StaffDashboard onOpenTask={() => setTaskOpen(true)} />;
    }
  }

  return (
    <div className="flex h-screen w-full overflow-hidden bg-muted/40">
      <Sidebar items={menu} active={activeMenu} onSelect={handleSelect} />
      <div className="flex flex-1 flex-col overflow-hidden">
        <TopBar
          title={subtitle}
          userName={isPm ? "정하늘" : "나"}
          roleLabel={isPm ? "PM" : "직원"}
          onLogout={handleLogout}
          actions={actions}
        />
        <main className="flex-1 overflow-y-auto p-6">{body}</main>
      </div>
      <Toaster />
    </div>
  );
}
