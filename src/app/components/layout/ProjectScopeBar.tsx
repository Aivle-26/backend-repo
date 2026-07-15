import { FolderKanban } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import { cn } from "@/app/components/ui/utils";
import type { ProjectStatus, ProjectSummary } from "@/app/data/demoData";

function statusClass(s: ProjectStatus) {
  const map: Record<ProjectStatus, string> = {
    분석중: "bg-muted text-muted-foreground",
    준비: "bg-amber-50 text-amber-700 border-amber-200",
    승인대기: "bg-orange-50 text-orange-700 border-orange-200",
    진행중: "bg-blue-50 text-blue-700 border-blue-200",
    완료: "bg-emerald-50 text-emerald-700 border-emerald-200",
  };
  return map[s];
}

interface ProjectScopeBarProps {
  projects: ProjectSummary[];
  value: string;
  onChange: (id: string) => void;
}

/** 프로젝트 단위 화면(요구사항·리스크·문서 등) 상단에 붙는 대상 프로젝트 선택 바 */
export function ProjectScopeBar({ projects, value, onChange }: ProjectScopeBarProps) {
  const selected = projects.find((p) => p.id === value) ?? null;

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-border bg-card px-4 py-2.5">
      <span className="flex items-center gap-1.5 text-muted-foreground text-sm">
        <FolderKanban className="size-4" /> 대상 프로젝트
      </span>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="h-9 w-72">
          <SelectValue placeholder="프로젝트를 선택하세요" />
        </SelectTrigger>
        <SelectContent>
          {projects.map((p) => (
            <SelectItem key={p.id} value={p.id}>
              {p.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {selected && (
        <>
          <Badge variant="outline" className={cn("font-normal", statusClass(selected.status))}>
            {selected.status}
          </Badge>
          <span className="text-muted-foreground text-xs">{selected.client}</span>
        </>
      )}
      <span className="ml-auto text-muted-foreground text-xs">
        이 화면은 선택한 프로젝트 기준으로 표시됩니다.
      </span>
    </div>
  );
}
