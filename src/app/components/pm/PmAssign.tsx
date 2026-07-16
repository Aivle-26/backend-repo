import { useMemo, useState } from "react";
import {
  Users,
  Inbox,
  CheckCircle2,
  Gauge,
  Sparkles,
  CalendarClock,
} from "lucide-react";
import { toast } from "sonner";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Badge } from "@/app/components/ui/badge";
import { Input } from "@/app/components/ui/input";
import { Progress } from "@/app/components/ui/progress";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/app/components/ui/table";
import { cn } from "@/app/components/ui/utils";
import { projectRepository } from "@/app/api/projectRepository";
import {
  projectRequirements,
  type ProjectSummary,
  type Requirement,
} from "@/app/data/demoData";
import { CountUp } from "@/app/components/common/CountUp";

type AssignFilter = "미배정" | "배정됨" | "전체";

interface AssignRow extends Requirement {
  owner: string;
  due: string;
}

function priorityVariant(p: string) {
  if (p === "높음") return "destructive" as const;
  if (p === "중간") return "secondary" as const;
  return "outline" as const;
}

const ASSIGNED_STATES = ["배정됨", "검토중", "완료"];

export function PmAssign({ project }: { project: ProjectSummary }) {
  const { team, assignees } = projectRepository.getPmAssign();
  const requirements = projectRequirements(project);

  const [rows, setRows] = useState<AssignRow[]>(
    requirements.map((r) => ({
      ...r,
      owner: r.status === "미배정" ? "" : r.recommendedOwner,
      due: "",
    })),
  );
  // 미배정 행에서 선택 중인 담당자/마감일 (배정 전 임시 값)
  const [pick, setPick] = useState<Record<number, { owner: string; due: string }>>({});
  const [filter, setFilter] = useState<AssignFilter>("미배정");
  const [query, setQuery] = useState("");

  const isAssigned = (r: AssignRow) => ASSIGNED_STATES.includes(r.status);

  const counts = useMemo(() => {
    const unassigned = rows.filter((r) => !isAssigned(r)).length;
    return {
      미배정: unassigned,
      배정됨: rows.length - unassigned,
      전체: rows.length,
    };
  }, [rows]);

  // 담당자별 현재 배정 건수 (실시간 워크로드)
  const loadByMember = useMemo(() => {
    const map = new Map<string, number>();
    team.forEach((m) => map.set(m.name, 0));
    rows.forEach((r) => {
      if (isAssigned(r) && r.owner) {
        map.set(r.owner, (map.get(r.owner) ?? 0) + 1);
      }
    });
    return map;
  }, [rows, team]);

  const maxLoad = Math.max(1, ...Array.from(loadByMember.values()));
  const topMember = useMemo(() => {
    let name = "-";
    let max = -1;
    loadByMember.forEach((v, k) => {
      if (v > max) {
        max = v;
        name = k;
      }
    });
    return { name, count: Math.max(0, max) };
  }, [loadByMember]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return rows.filter((r) => {
      const byTab =
        filter === "전체" ||
        (filter === "미배정" ? !isAssigned(r) : isAssigned(r));
      const byQuery =
        !q ||
        r.text.toLowerCase().includes(q) ||
        r.category.toLowerCase().includes(q) ||
        r.recommendedOwner.toLowerCase().includes(q);
      return byTab && byQuery;
    });
  }, [rows, filter, query]);

  const setPickOwner = (id: number, owner: string) =>
    setPick((p) => ({ ...p, [id]: { owner, due: p[id]?.due ?? "" } }));
  const setPickDue = (id: number, due: string) =>
    setPick((p) => ({ ...p, [id]: { owner: p[id]?.owner ?? "", due } }));

  const assign = async (r: AssignRow) => {
    const chosen = pick[r.id]?.owner || r.recommendedOwner;
    const due = pick[r.id]?.due ?? "";
    await projectRepository.assignRequirement({
      requirementId: r.id,
      assignee: chosen,
      dueDate: due,
    });
    setRows((prev) =>
      prev.map((x) =>
        x.id === r.id ? { ...x, owner: chosen, due, status: "배정됨" } : x,
      ),
    );
    toast.success(`"${r.text.slice(0, 14)}…" 업무를 ${chosen}님에게 배정했어요.`);
  };

  const unassign = (r: AssignRow) => {
    setRows((prev) =>
      prev.map((x) =>
        x.id === r.id ? { ...x, owner: "", due: "", status: "미배정" } : x,
      ),
    );
    toast("배정을 취소했어요.");
  };

  return (
    <div className="space-y-6">
      {/* 요약 통계 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <StatCard
          icon={<Inbox className="size-5" />}
          tone="bg-blue-50 text-blue-600"
          label="미배정 요구사항"
          value={`${counts.미배정}건`}
        />
        <StatCard
          icon={<CheckCircle2 className="size-5" />}
          tone="bg-emerald-50 text-emerald-600"
          label="배정 완료"
          value={`${counts.배정됨}건`}
        />
        <StatCard
          icon={<Users className="size-5" />}
          tone="bg-indigo-50 text-indigo-600"
          label="참여 팀원"
          value={`${team.length}명`}
        />
        <StatCard
          icon={<Gauge className="size-5" />}
          tone="bg-amber-50 text-amber-600"
          label="최다 부하 담당자"
          value={topMember.count > 0 ? `${topMember.name} · ${topMember.count}건` : "-"}
        />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {/* 배정 테이블 */}
        <div className="xl:col-span-2">
          <Card>
            <CardHeader>
              <CardTitle>업무 배정</CardTitle>
              <CardDescription>
                AI 추천 담당자를 참고해 요구사항을 팀원에게 배정하세요.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex flex-wrap items-center gap-2">
                {(["미배정", "배정됨", "전체"] as AssignFilter[]).map((f) => (
                  <button
                    key={f}
                    onClick={() => setFilter(f)}
                    className={cn(
                      "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm transition-colors",
                      filter === f
                        ? "bg-primary text-primary-foreground"
                        : "bg-muted text-muted-foreground hover:bg-muted/70",
                    )}
                  >
                    {f}
                    <span
                      className={cn(
                        "rounded-full px-1.5 text-xs",
                        filter === f ? "bg-white/20" : "bg-background",
                      )}
                    >
                      {counts[f]}
                    </span>
                  </button>
                ))}
                <Input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="요구사항·담당자 검색"
                  className="ml-auto h-8 w-48"
                />
              </div>

              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>요구사항</TableHead>
                    <TableHead className="w-20">우선순위</TableHead>
                    <TableHead className="w-64">담당자 / 마감</TableHead>
                    <TableHead className="w-24 text-right">배정</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filtered.map((r) => {
                    const assigned = isAssigned(r);
                    const chosen = pick[r.id]?.owner || r.recommendedOwner;
                    return (
                      <TableRow key={r.id}>
                        <TableCell>
                          <div className="text-foreground text-sm">{r.text}</div>
                          <div className="mt-1 flex items-center gap-1.5">
                            <Badge variant="outline" className="font-normal">
                              {r.category}
                            </Badge>
                            {!assigned && (
                              <button
                                onClick={() => setPickOwner(r.id, r.recommendedOwner)}
                                className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-600 hover:bg-blue-100"
                                title="AI 추천 담당자로 지정"
                              >
                                <Sparkles className="size-3" /> 추천 {r.recommendedOwner}
                              </button>
                            )}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={priorityVariant(r.priority)}>{r.priority}</Badge>
                        </TableCell>
                        <TableCell>
                          {assigned ? (
                            <div className="flex items-center gap-2">
                              <Avatar className="size-6">
                                <AvatarFallback className="text-[10px]">
                                  {r.owner.slice(0, 1)}
                                </AvatarFallback>
                              </Avatar>
                              <span className="text-foreground text-sm">{r.owner}</span>
                              {r.due && (
                                <span className="inline-flex items-center gap-1 text-muted-foreground text-xs">
                                  <CalendarClock className="size-3" />
                                  {r.due}
                                </span>
                              )}
                            </div>
                          ) : (
                            <div className="flex items-center gap-2">
                              <Select
                                value={chosen}
                                onValueChange={(v) => setPickOwner(r.id, v)}
                              >
                                <SelectTrigger className="h-8 w-28">
                                  <SelectValue placeholder="담당자" />
                                </SelectTrigger>
                                <SelectContent>
                                  {assignees.map((a) => (
                                    <SelectItem key={a} value={a}>
                                      {a}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                              <Input
                                type="date"
                                value={pick[r.id]?.due ?? ""}
                                onChange={(e) => setPickDue(r.id, e.target.value)}
                                className="h-8 w-36"
                              />
                            </div>
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          {assigned ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              className="h-8 text-muted-foreground"
                              onClick={() => unassign(r)}
                            >
                              취소
                            </Button>
                          ) : (
                            <Button size="sm" className="h-8" onClick={() => assign(r)}>
                              배정
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                  {filtered.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                        {filter === "미배정"
                          ? "미배정 요구사항이 없어요. 모두 배정되었습니다 🎉"
                          : "해당하는 요구사항이 없습니다."}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </div>

        {/* 팀 워크로드 */}
        <div className="xl:col-span-1">
          <Card className="sticky top-6">
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Users className="size-4" /> 팀 워크로드
              </CardTitle>
              <CardDescription>배정 시 실시간으로 반영됩니다.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {team.map((m) => {
                const load = loadByMember.get(m.name) ?? 0;
                const pct = Math.round((load / maxLoad) * 100);
                const isTop = m.name === topMember.name && topMember.count > 0;
                return (
                  <div key={m.id} className="space-y-1.5">
                    <div className="flex items-center gap-2">
                      <Avatar className="size-7">
                        <AvatarFallback className="text-[10px]">
                          {m.name.slice(0, 1)}
                        </AvatarFallback>
                      </Avatar>
                      <div className="leading-tight">
                        <div className="text-foreground text-sm">{m.name}</div>
                        <div className="text-muted-foreground text-xs">{m.role}</div>
                      </div>
                      <span
                        className={cn(
                          "ml-auto rounded-full px-2 py-0.5 text-xs font-medium",
                          isTop
                            ? "bg-amber-50 text-amber-700"
                            : "bg-muted text-muted-foreground",
                        )}
                      >
                        {load}건
                      </span>
                    </div>
                    <Progress value={pct} />
                  </div>
                );
              })}
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

function StatCard({
  icon,
  tone,
  label,
  value,
}: {
  icon: React.ReactNode;
  tone: string;
  label: string;
  value: string;
}) {
  return (
    <Card>
      <CardContent className="pt-5">
        <div className="flex items-center gap-3">
          <span className={cn("flex size-11 items-center justify-center rounded-xl", tone)}>
            {icon}
          </span>
          <div className="leading-tight">
            <div className="text-muted-foreground text-sm">{label}</div>
            <div className="text-foreground text-xl">
              <CountUp value={value} />
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
