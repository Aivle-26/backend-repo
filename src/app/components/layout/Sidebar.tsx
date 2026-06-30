import { Sparkles } from "lucide-react";
import { cn } from "@/app/components/ui/utils";

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
      <div className="h-16 flex items-center gap-2 px-5 border-b border-border">
        <div className="size-8 rounded-md bg-primary text-primary-foreground flex items-center justify-center">
          <Sparkles className="size-4" />
        </div>
        <div className="leading-tight">
          <div className="text-sidebar-foreground">BidWorks AI</div>
          <div className="text-muted-foreground text-xs">RFP 프로젝트 관리</div>
        </div>
      </div>
      <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
        {items.map((item) => {
          const Icon = item.icon;
          const isActive = item.key === active;
          return (
            <button
              key={item.key}
              onClick={() => onSelect(item.key)}
              className={cn(
                "w-full flex items-center gap-3 px-3 py-2 rounded-md text-left transition-colors",
                isActive
                  ? "bg-sidebar-accent text-sidebar-accent-foreground"
                  : "text-muted-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-foreground",
              )}
            >
              <Icon className="size-4 shrink-0" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>
    </aside>
  );
}
