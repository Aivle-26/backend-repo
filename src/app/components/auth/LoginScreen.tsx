import { useState } from "react";
import { Sparkles, Briefcase, UserRound } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Input } from "@/app/components/ui/input";
import { Label } from "@/app/components/ui/label";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/app/components/ui/card";
import { cn } from "@/app/components/ui/utils";
import { ThemeToggle } from "@/app/components/common/ThemeToggle";
import type { Role } from "@/app/api/projectRepository";

interface LoginScreenProps {
  onLogin: (role: Role) => void;
}

export function LoginScreen({ onLogin }: LoginScreenProps) {
  const [email, setEmail] = useState("user@bidworks.ai");
  const [password, setPassword] = useState("password");
  const [role, setRole] = useState<Role>("pm");

  return (
    <div className="relative min-h-screen w-full bg-muted flex flex-col items-center justify-center p-6">
      <div className="absolute right-4 top-4">
        <ThemeToggle variant="outline" />
      </div>
      <div className="flex items-center gap-2 mb-6">
        <div className="size-9 rounded-md bg-primary text-primary-foreground flex items-center justify-center">
          <Sparkles className="size-5" />
        </div>
        <span className="text-foreground">BidWorks AI</span>
      </div>

      <Card className="w-full max-w-md">
        <CardHeader className="text-center">
          <CardTitle>로그인</CardTitle>
          <CardDescription>AI 기반 RFP 프로젝트 관리</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="space-y-2">
            <Label htmlFor="email">이메일</Label>
            <Input
              id="email"
              type="email"
              placeholder="이메일을 입력하세요"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">비밀번호</Label>
            <Input
              id="password"
              type="password"
              placeholder="비밀번호를 입력하세요"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <div className="space-y-2">
            <Label>역할 선택</Label>
            <div className="grid grid-cols-2 gap-3">
              <RoleCard
                active={role === "pm"}
                onClick={() => setRole("pm")}
                icon={<Briefcase className="size-4" />}
                title="PM"
                desc="공고 분석 · 업무 배정 · 진행률 관리"
              />
              <RoleCard
                active={role === "staff"}
                onClick={() => setRole("staff")}
                icon={<UserRound className="size-4" />}
                title="직원"
                desc="내 업무 확인 · 산출물 제출 · 피드백 확인"
              />
            </div>
          </div>

          <Button className="w-full" onClick={() => onLogin(role)}>
            로그인
          </Button>
        </CardContent>
      </Card>

      <p className="text-muted-foreground text-xs mt-6 text-center max-w-md">
        공공 RFP 프로젝트를 AI가 분석하고 역할별 화면에서 업무를 관리합니다.
      </p>
    </div>
  );
}

interface RoleCardProps {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  title: string;
  desc: string;
}

function RoleCard({ active, onClick, icon, title, desc }: RoleCardProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "rounded-lg border p-3 text-left transition-colors",
        active
          ? "border-primary bg-accent"
          : "border-border hover:bg-accent/50",
      )}
    >
      <div className="flex items-center gap-2 mb-1">
        <span
          className={cn(
            "flex size-6 items-center justify-center rounded-md",
            active ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground",
          )}
        >
          {icon}
        </span>
        <span className="text-foreground">{title}</span>
      </div>
      <p className="text-muted-foreground text-xs">{desc}</p>
    </button>
  );
}
