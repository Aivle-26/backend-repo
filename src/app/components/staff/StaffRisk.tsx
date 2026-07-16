import { useMemo, useState } from "react";
import {
  Search,
  ShieldCheck,
  Share2,
  Download,
  Plus,
  MessageSquare,
  Settings,
  Megaphone,
  Github,
  Cloud,
  Bot,
  CheckCircle2,
} from "lucide-react";
import { toast } from "sonner";
import { Card, CardContent } from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Badge } from "@/app/components/ui/badge";
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
import type {
  RiskSeverity,
  TeamCommItem,
  OpenRiskState,
} from "@/app/data/demoData";

function severityBadge(s: RiskSeverity) {
  const map: Record<RiskSeverity, string> = {
    "심각": "bg-red-50 text-red-700 border-red-200",
    "주의": "bg-amber-50 text-amber-700 border-amber-200",
    "정보": "bg-blue-50 text-blue-700 border-blue-200",
  };
  return map[s];
}

function stateBadge(s: OpenRiskState) {
  const map: Record<OpenRiskState, string> = {
    "미해결": "bg-red-50 text-red-700 border-red-200",
    "처리 중": "bg-blue-50 text-blue-700 border-blue-200",
    "대기 중": "bg-amber-50 text-amber-700 border-amber-200",
    "주의": "bg-amber-50 text-amber-700 border-amber-200",
  };
  return map[s];
}

const COMM_ICON: Record<TeamCommItem["kind"], React.ComponentType<{ className?: string }>> = {
  message: MessageSquare,
  system: Settings,
  notice: Megaphone,
};

const SERVICE_ICON: Record<string, React.ComponentType<{ className?: string }>> = {
  github: Github,
  jira: Share2,
  cloudwatch: Cloud,
};

type CommFilter = "전체" | "안읽음" | "공지";

export function StaffRisk() {
  const { detections, teamComms, openRisks, solutions } =
    projectRepository.getRiskBoard();

  const [filter, setFilter] = useState<CommFilter>("전체");
  const unreadCount = teamComms.filter((c) => c.unread).length;

  const filteredComms = useMemo(() => {
    if (filter === "안읽음") return teamComms.filter((c) => c.unread);
    if (filter === "공지") return teamComms.filter((c) => c.kind === "notice");
    return teamComms;
  }, [filter, teamComms]);

  return (
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
      {/* AI 리스크 탐지 현황 */}
      <Card>
        <CardContent className="pt-5">
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <Search className="size-4 text-blue-600" />
              <span className="text-foreground">AI 리스크 탐지 현황 (FE/BE/CLOUD)</span>
            </div>
            <span className="flex size-9 items-center justify-center rounded-lg bg-blue-600 text-white">
              <ShieldCheck className="size-5" />
            </span>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-20">리스크 ID</TableHead>
                <TableHead>서비스</TableHead>
                <TableHead>탐지 소스</TableHead>
                <TableHead className="w-28">탐지 일시</TableHead>
                <TableHead className="w-16">심각도</TableHead>
                <TableHead>설명</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {detections.map((d) => (
                <TableRow key={d.id}>
                  <TableCell className="text-foreground text-sm">{d.id}</TableCell>
                  <TableCell className="text-foreground text-sm">{d.service}</TableCell>
                  <TableCell className="text-muted-foreground text-xs">{d.source}</TableCell>
                  <TableCell className="text-muted-foreground text-xs">{d.detectedAt}</TableCell>
                  <TableCell>
                    <RiskPill label={d.severity} critical={d.severity === "심각"} tone={severityBadge(d.severity)} />
                  </TableCell>
                  <TableCell className="text-muted-foreground text-xs">{d.description}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* 팀 커뮤니케이션 */}
      <Card>
        <CardContent className="pt-5">
          <div className="mb-4">
            <span className="text-foreground">팀 커뮤니케이션</span>
          </div>
          <div className="mb-3 flex items-center gap-2">
            {(["전체", "안읽음", "공지"] as CommFilter[]).map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm transition-colors",
                  filter === f
                    ? "bg-blue-600 text-white"
                    : "bg-muted text-muted-foreground hover:bg-muted/70",
                )}
              >
                {f}
                {f === "안읽음" && (
                  <span
                    className={cn(
                      "flex size-5 items-center justify-center rounded-full text-[11px]",
                      filter === f ? "bg-white/20 text-white" : "bg-red-500 text-white",
                    )}
                  >
                    {unreadCount}
                  </span>
                )}
                {f === "공지" && <Megaphone className="size-3.5" />}
              </button>
            ))}
          </div>
          <div className="divide-y divide-border">
            {filteredComms.map((c) => {
              const Icon = COMM_ICON[c.kind];
              return (
                <div key={c.id} className="flex items-start gap-3 py-3">
                  <span
                    className={cn(
                      "flex size-8 shrink-0 items-center justify-center rounded-full",
                      c.kind === "notice"
                        ? "bg-amber-50 text-amber-600"
                        : c.kind === "system"
                          ? "bg-muted text-muted-foreground"
                          : "bg-blue-50 text-blue-600",
                    )}
                  >
                    <Icon className="size-4" />
                  </span>
                  <div className="flex-1 leading-tight">
                    <div className="flex items-center gap-2">
                      <span className="text-foreground text-sm">{c.title}</span>
                      {c.unread && <span className="size-1.5 rounded-full bg-red-500" />}
                    </div>
                    <p className="text-muted-foreground text-xs mt-0.5">{c.body}</p>
                  </div>
                  <span className="shrink-0 text-muted-foreground text-xs">{c.time}</span>
                </div>
              );
            })}
            {filteredComms.length === 0 && (
              <p className="py-6 text-center text-muted-foreground text-sm">
                해당하는 항목이 없습니다.
              </p>
            )}
          </div>
        </CardContent>
      </Card>

      {/* 미해결 주요 리스크 */}
      <Card className="border-red-100 bg-red-50/30">
        <CardContent className="pt-5">
          <div className="mb-4">
            <span className="text-foreground">미해결 주요 리스크</span>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>리스크</TableHead>
                <TableHead className="w-32">연동 서비스</TableHead>
                <TableHead className="w-24">발생 시간</TableHead>
                <TableHead className="w-20">상태</TableHead>
                <TableHead className="w-16">심각도</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {openRisks.map((r) => {
                const SIcon = SERVICE_ICON[r.serviceIcon] ?? Share2;
                return (
                  <TableRow key={r.id}>
                    <TableCell className="text-foreground text-sm">{r.title}</TableCell>
                    <TableCell>
                      <span className="inline-flex items-center gap-1.5 text-sm text-foreground">
                        <SIcon className="size-4 text-muted-foreground" />
                        {r.service}
                      </span>
                    </TableCell>
                    <TableCell className="text-muted-foreground text-xs">{r.occurredAt}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={cn("font-normal", stateBadge(r.state))}>
                        {r.state}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {r.severity && (
                        <RiskPill label={r.severity} critical={r.severity === "심각"} tone={severityBadge(r.severity)} />
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* AI 리스크 해결 방안 */}
      <Card>
        <CardContent className="pt-5">
          <div className="mb-4 flex items-center gap-2">
            <Bot className="size-4 text-blue-600" />
            <span className="text-foreground">AI 리스크 해결 방안 (AI 기능: GitHub 연동 등)</span>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>항목</TableHead>
                <TableHead className="w-20">담당자</TableHead>
                <TableHead className="w-24">예상 소요 시간</TableHead>
                <TableHead className="w-16 text-center">자동화</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {solutions.map((s) => (
                <TableRow key={s.id}>
                  <TableCell>
                    <div className="flex items-start gap-2">
                      <span className="mt-0.5 flex size-7 items-center justify-center rounded-md bg-blue-50 text-blue-600">
                        <Settings className="size-4" />
                      </span>
                      <span className="text-foreground text-sm">{s.item}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-muted-foreground text-sm">{s.owner}</TableCell>
                  <TableCell className="text-muted-foreground text-xs">{s.eta}</TableCell>
                  <TableCell className="text-center">
                    {s.automated && (
                      <CheckCircle2 className="mx-auto size-5 text-emerald-500" />
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}

/* 리스크 상태/심각도 배지 — 심각은 솔리드 빨강 + 라이브 핑 점 */
function RiskPill({
  label,
  critical,
  tone,
}: {
  label: string;
  critical: boolean;
  tone: string;
}) {
  if (critical) {
    return (
      <Badge className="border-transparent bg-red-700 text-white font-normal dark:bg-red-800">
        {label}
      </Badge>
    );
  }
  return (
    <Badge variant="outline" className={cn("font-normal", tone)}>
      {label}
    </Badge>
  );
}

/* 상단 액션 바 (공유/내보내기/새 리스크) — 페이지 헤더 보조용 export */
export function StaffRiskActions() {
  return (
    <div className="flex items-center gap-2">
      <Button variant="outline" size="sm" onClick={() => toast("공유 링크를 생성했습니다.")}>
        <Share2 className="size-4" /> 공유
      </Button>
      <Button variant="outline" size="sm" onClick={() => toast("리스크 현황을 내보냈습니다.")}>
        <Download className="size-4" /> 내보내기
      </Button>
      <Button size="sm" onClick={() => toast.success("새 리스크 등록 화면")}>
        <Plus className="size-4" /> 새 리스크 등록
      </Button>
    </div>
  );
}
