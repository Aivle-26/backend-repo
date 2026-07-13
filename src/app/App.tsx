import { useState } from "react";
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
import { PmDashboard } from "@/app/components/pm/PmDashboard";
import { PmAnalysis } from "@/app/components/pm/PmAnalysis";
import { PmDocuments } from "@/app/components/pm/PmDocuments";
import { PmRisk } from "@/app/components/pm/PmRisk";
import { StaffDashboard } from "@/app/components/staff/StaffDashboard";
import { StaffTaskDetail } from "@/app/components/staff/StaffTaskDetail";
import { StaffDocuments } from "@/app/components/staff/StaffDocuments";
import { StaffRisk, StaffRiskActions } from "@/app/components/staff/StaffRisk";
import { projectRepository, type Role } from "@/app/api/projectRepository";

const PM_MENU: SidebarItem[] = [
  { key: "dashboard", label: "대시보드", icon: LayoutDashboard },
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
  const [role, setRole] = useState<Role | null>(null);
  const [pmMenu, setPmMenu] = useState("dashboard");
  const [staffMenu, setStaffMenu] = useState("tasks");
  const [taskOpen, setTaskOpen] = useState(false);

  const handleLogin = (r: Role) => {
    setRole(r);
    setPmMenu("dashboard");
    setStaffMenu("tasks");
    setTaskOpen(false);
  };

  const handleLogout = () => setRole(null);

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
    } else {
      setStaffMenu(key);
      setTaskOpen(false);
    }
  };

  const title = projectRepository.getProjectName();
  let subtitle = "";
  let body: React.ReactNode = null;
  let actions: React.ReactNode = null;

  if (isPm) {
    if (pmMenu === "documents") {
      subtitle = "문서 통합 관리";
      body = <PmDocuments />;
    } else if (pmMenu === "risk") {
      subtitle = "리스크 관리";
      body = <PmRisk />;
    } else if (
      pmMenu === "analysis" ||
      pmMenu === "assign" ||
      pmMenu === "requirements"
    ) {
      subtitle = "RFP 분석 및 업무 배정";
      body = <PmAnalysis />;
    } else {
      subtitle = "PM 대시보드";
      body = <PmDashboard />;
    }
  } else {
    if (staffMenu === "documents") {
      subtitle = "문서 통합 관리";
      body = <StaffDocuments />;
    } else if (staffMenu === "risk") {
      subtitle = "리스크";
      body = <StaffRisk />;
      actions = <StaffRiskActions />;
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
          title={title}
          subtitle={subtitle}
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
