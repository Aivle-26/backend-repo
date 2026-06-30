import { Bell, LogOut } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { Badge } from "@/app/components/ui/badge";

interface TopBarProps {
  title: string;
  subtitle?: string;
  userName: string;
  roleLabel: string;
  onLogout: () => void;
}

export function TopBar({ title, subtitle, userName, roleLabel, onLogout }: TopBarProps) {
  return (
    <header className="h-16 shrink-0 border-b border-border bg-card flex items-center justify-between px-6">
      <div className="leading-tight">
        <div className="text-foreground">{title}</div>
        {subtitle && <div className="text-muted-foreground text-xs">{subtitle}</div>}
      </div>
      <div className="flex items-center gap-3">
        <Badge variant="secondary">{roleLabel}</Badge>
        <Button variant="ghost" size="icon" aria-label="알림">
          <Bell className="size-4" />
        </Button>
        <Avatar className="size-8">
          <AvatarFallback>{userName.slice(0, 1)}</AvatarFallback>
        </Avatar>
        <Button variant="outline" size="sm" onClick={onLogout}>
          <LogOut className="size-4" />
          로그아웃
        </Button>
      </div>
    </header>
  );
}
