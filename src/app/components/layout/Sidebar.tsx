import { Sparkles } from "lucide-react";
import { cn } from "@/app/components/ui/utils";
import { SlackIcon } from "@/app/components/common/SlackIcon";

export interface SidebarItem {
  key: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

interface SidebarProps {
  items: SidebarItem[];
  active: string;
  onSelect: (key: string) => void;
}

export function Sidebar({ items, active, onSelect }: SidebarProps) {
  return (
    <aside className="w-60 shrink-0 border-r border-border bg-sidebar flex flex-col">
      <div className="h-16 flex items-center gap-2.5 px-5">
        <div className="size-9 rounded-xl bg-primary text-primary-foreground flex items-center justify-center">
          <Sparkles className="size-5" />
        </div>
        <div className="leading-tight">
          <div className="text-sidebar-foreground">BidWorks AI</div>
          <div className="text-muted-foreground text-xs">RFP 프로젝트 관리</div>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-2 space-y-0.5">
        {items.map((item) => {
          const Icon = item.icon;
          const isActive = item.key === active;
          return (
            <button
              key={item.key}
              onClick={() => onSelect(item.key)}
              aria-current={isActive ? "page" : undefined}
              className={cn(
                "group relative w-full flex items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition-all",
                isActive
                  ? "bg-sidebar-accent text-sidebar-accent-foreground font-medium"
                  : "text-muted-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
              )}
            >
              <span
                className={cn(
                  "absolute left-0 top-1/2 h-5 w-1 -translate-y-1/2 rounded-r-full bg-primary transition-opacity",
                  isActive ? "opacity-100" : "opacity-0",
                )}
                aria-hidden="true"
              />
              <Icon
                className={cn(
                  "size-4 shrink-0 transition-colors",
                  isActive
                    ? "text-primary"
                    : "text-muted-foreground group-hover:text-sidebar-foreground",
                )}
              />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>

      {/* 연동 서비스 */}
      <div className="border-t border-border p-3">
        <div className="px-2 pb-1.5 text-xs text-muted-foreground">연동 서비스</div>
        <button
          onClick={() => onSelect("slack")}
          aria-current={active === "slack" ? "page" : undefined}
          className={cn(
            "group relative w-full flex items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition-all",
            active === "slack"
              ? "bg-sidebar-accent text-sidebar-accent-foreground font-medium"
              : "text-muted-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
          )}
        >
          <span
            className={cn(
              "absolute left-0 top-1/2 h-5 w-1 -translate-y-1/2 rounded-r-full bg-primary transition-opacity",
              active === "slack" ? "opacity-100" : "opacity-0",
            )}
            aria-hidden="true"
          />
          <SlackIcon className="text-base" />
          <span>Slack 연동</span>
        </button>
      </div>
    </aside>
  );
}
