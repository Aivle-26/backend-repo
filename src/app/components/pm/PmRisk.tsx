import { useState } from "react";
import {
  Shield,
  Siren,
  Scale,
  CalendarDays,
  Bot,
  FileText,
  Calendar,
  PencilLine,
  UserRound,
  BarChart3,
  ListChecks,
  ShieldCheck,
  Network,
  ClipboardCheck,
  AlertTriangle,
  CheckCircle2,
  Phone,
  Mail,
  KeyRound,
  ArrowRight,
  FileLock2,
  Calculator,
  Users,
  RefreshCw,
  Bell,
  MessageSquare,
  PauseCircle,
  CheckCircle,
} from "lucide-react";
import { toast } from "sonner";
import { Card, CardContent } from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Badge } from "@/app/components/ui/badge";
import { Checkbox } from "@/app/components/ui/checkbox";
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
import { CountUp } from "@/app/components/common/CountUp";
import type {
  RiskKpi,
  PmRiskState,
  PmRiskPriority,
  PmRiskRow,
} from "@/app/data/demoData";

const KPI_TONE: Record<
  RiskKpi["tone"],
  { icon: React.ComponentType<{ className?: string }>; bg: string; fg: string }
> = {
  info: { icon: Shield, bg: "bg-blue-50", fg: "text-blue-600" },
  danger: { icon: Siren, bg: "bg-red-50", fg: "text-red-600" },
  warn: { icon: Scale, bg: "bg-amber-50", fg: "text-amber-600" },
  success: { icon: CalendarDays, bg: "bg-emerald-50", fg: "text-emerald-600" },
};

const TARGET_ICON: Record<PmRiskRow["targetIcon"], React.ComponentType<{ className?: string }>> = {
  doc: FileText,
  calendar: Calendar,
  edit: PencilLine,
  user: UserRound,
  chart: BarChart3,
};

function stateBadge(s: PmRiskState) {
  const map: Record<PmRiskState, string> = {
    "미조치": "bg-red-50 text-red-700 border-red-200",
    "검토중": "bg-amber-50 text-amber-700 border-amber-200",
    "분석 완료": "bg-emerald-50 text-emerald-700 border-emerald-200",
    "체크리스트 생성": "bg-indigo-50 text-indigo-700 border-indigo-200",
    "산출 완료": "bg-emerald-50 text-emerald-700 border-emerald-200",
  };
  return map[s];
}

function priorityBadge(p: PmRiskPriority) {
  const map: Record<PmRiskPriority, string> = {
    "긴급": "bg-red-50 text-red-700 border-red-200",
    "주의": "bg-amber-50 text-amber-700 border-amber-200",
    "보통": "bg-blue-50 text-blue-700 border-blue-200",
  };
  return map[p];
}

export function PmRisk() {
  const {
    kpis,
    rows,
    comment,
    commentTags,
    laborChecks,
    privacyItems,
    handoverChecks: initialHandover,
    actions,
    quickTools,
  } = projectRepository.getPmRisk();

  const [handover, setHandover] = useState(initialHandover);
  const toggleHandover = (id: string) =>
    setHandover((prev) =>
      prev.map((h) => (h.id === id ? { ...h, done: !h.done } : h)),
    );
  const doneCount = handover.filter((h) => h.done).length;

  const tagIcons = [FileLock2, FileText, Network, Users];

  return (
    <div className="space-y-6">
      {/* KPI */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        {kpis.map((k) => {
          const t = KPI_TONE[k.tone];
          const Icon = t.icon;
          return (
            <Card key={k.id}>
              <CardContent className="pt-5">
                <div className="flex items-center gap-3">
                  <span className={cn("flex size-11 items-center justify-center rounded-xl", t.bg, t.fg)}>
                    <Icon className="size-5" />
                  </span>
                  <div className="leading-tight">
                    <div className="text-muted-foreground text-sm">{k.label}</div>
                    <div className="text-foreground text-2xl">
                      <CountUp value={k.value} />
                    </div>
                    <div className="text-muted-foreground text-xs">{k.sub}</div>
                  </div>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        {/* ===== 좌측 컬럼 ===== */}
        <div className="space-y-6">
          {/* A. 리스크 탐지 현황 */}
          <Card>
            <CardContent className="pt-5">
              <SectionTitle
                icon={<Bot className="size-4" />}
                title="A. 리스크 탐지 현황"
                desc="AI가 분석한 현재 프로젝트의 리스크 목록입니다."
                right={
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => toast("전체 리스크 목록을 표시합니다.")}
                  >
                    전체 리스크 보기
                  </Button>
                }
              />
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-8">#</TableHead>
                    <TableHead>리스크 유형</TableHead>
                    <TableHead>대상</TableHead>
                    <TableHead className="w-28">상태</TableHead>
                    <TableHead className="w-20">우선순위</TableHead>
                    <TableHead className="w-20">영향도</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.map((r) => {
                    const TIcon = TARGET_ICON[r.targetIcon];
                    return (
                      <TableRow key={r.id}>
                        <TableCell className="text-muted-foreground">{r.id}</TableCell>
                        <TableCell className="text-foreground text-sm">{r.type}</TableCell>
                        <TableCell>
                          <div className="flex items-center gap-1.5 text-sm text-foreground">
                            <TIcon className="size-3.5 text-muted-foreground" />
                            {r.target}
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline" className={cn("font-normal", stateBadge(r.state))}>
                            {r.state}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline" className={cn("font-normal", priorityBadge(r.priority))}>
                            {r.priority}
                          </Badge>
                        </TableCell>
                        <TableCell className="text-muted-foreground text-xs">{r.impact}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </CardContent>
          </Card>

          {/* B. AI 리스크 분석 코멘트 */}
          <Card>
            <CardContent className="pt-5">
              <SectionTitle
                icon={<Bot className="size-4" />}
                title="B. AI 리스크 분석 코멘트"
              />
              <p className="text-muted-foreground text-sm leading-relaxed">{comment}</p>
              <div className="mt-4 flex flex-wrap gap-2">
                {commentTags.map((t, i) => {
                  const Icon = tagIcons[i % tagIcons.length];
                  return (
                    <span
                      key={t.id}
                      className="inline-flex items-center gap-1.5 rounded-md bg-muted/60 px-2.5 py-1 text-xs text-foreground"
                    >
                      <Icon className="size-3.5 text-muted-foreground" />
                      {t.label}
                    </span>
                  );
                })}
              </div>
            </CardContent>
          </Card>

          {/* AI 대응 액션 추천 */}
          <Card>
            <CardContent className="pt-5">
              <SectionTitle
                icon={<Bot className="size-4" />}
                title="AI 대응 액션 추천"
                desc="현재 리스크를 기반으로 AI가 추천하는 대응 액션입니다."
              />
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {actions.map((a, i) => {
                  const Icon = [FileLock2, RefreshCw, Calculator, Users][i % 4];
                  return (
                    <button
                      key={a.id}
                      onClick={() => toast.success(`'${a.title}' 액션을 실행했습니다.`)}
                      className="flex items-start gap-2 rounded-lg border border-border p-3 text-left transition-colors hover:bg-muted/60"
                    >
                      <span className="flex size-8 items-center justify-center rounded-md bg-blue-50 text-blue-600">
                        <Icon className="size-4" />
                      </span>
                      <div className="flex-1 leading-tight">
                        <div className="text-foreground text-sm">{a.title}</div>
                        <div className="text-muted-foreground text-xs">{a.sub}</div>
                      </div>
                      <ArrowRight className="size-4 text-muted-foreground" />
                    </button>
                  );
                })}
              </div>
            </CardContent>
          </Card>
        </div>

        {/* ===== 우측 컬럼 ===== */}
        <div className="space-y-6">
          {/* B1. 기업 가이드·노동법 */}
          <Card>
            <CardContent className="pt-5">
              <div className="flex items-start justify-between gap-3 mb-3">
                <SectionTitle
                  icon={<ListChecks className="size-4" />}
                  title="B1. 기업 가이드 · 노동법 준수 및 예상 견적"
                  compact
                />
                <Badge variant="outline" className="bg-emerald-50 text-emerald-700 border-emerald-200 font-normal">
                  부분 준수
                </Badge>
              </div>
              <div className="space-y-2">
                {laborChecks.map((c) => (
                  <div key={c.id} className="flex items-center gap-2 text-sm">
                    {c.ok ? (
                      <CheckCircle2 className="size-4 text-emerald-500" />
                    ) : (
                      <AlertTriangle className="size-4 text-amber-500" />
                    )}
                    <span className="text-foreground">{c.label}</span>
                  </div>
                ))}
                <div className="flex items-center gap-2 pt-1 text-sm">
                  <span className="flex size-4 items-center justify-center rounded-full bg-muted text-[10px] text-muted-foreground">₩</span>
                  <span className="text-foreground">예상 견적: 3,200만원 ~ 3,600만원</span>
                </div>
              </div>
              <div className="mt-3 flex justify-end">
                <Button variant="outline" size="sm" onClick={() => toast("기준을 표시합니다.")}>
                  기준 보기
                </Button>
              </div>
            </CardContent>
          </Card>

          {/* B2. 공유 문서 개인정보 검사 */}
          <Card>
            <CardContent className="pt-5">
              <SectionTitle
                icon={<ShieldCheck className="size-4" />}
                title="B2. 공유 문서 개인정보 · 기밀사항 검사"
              />
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  {privacyItems.map((p, i) => {
                    const Icon = [Phone, Mail, KeyRound][i % 3];
                    return (
                      <div key={p.id} className="flex items-center justify-between text-sm">
                        <span className="flex items-center gap-2 text-foreground">
                          <Icon className="size-4 text-muted-foreground" />
                          {p.label}
                        </span>
                        <span className="text-red-600">{p.count}</span>
                      </div>
                    );
                  })}
                </div>
                <div className="flex flex-col justify-between">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">심각도</span>
                    <span className="text-red-600">높음</span>
                  </div>
                  <div className="my-2 h-2 overflow-hidden rounded-full bg-muted">
                    <div className="h-full w-4/5 bg-gradient-to-r from-red-500 to-amber-400" />
                  </div>
                  <Button variant="outline" size="sm" onClick={() => toast("상세 결과를 표시합니다.")}>
                    상세 결과 보기
                  </Button>
                </div>
              </div>
              <p className="mt-3 text-muted-foreground text-xs">
                권장 조치: 마스킹 / 접근권한 축소
              </p>
            </CardContent>
          </Card>

          {/* B3. 요구사항 변경 영향도 */}
          <Card>
            <CardContent className="pt-5">
              <SectionTitle
                icon={<Network className="size-4" />}
                title="B3. 요구사항 변경 영향도 분석"
              />
              <p className="text-muted-foreground text-sm mb-3">
                결제 기능 범위 확대로 WBS 4개, 일정 3건, 담당자 2명에 영향이 예상됩니다.
              </p>
              <div className="flex flex-wrap items-center gap-2">
                {[
                  { label: "요구사항 변경", v: "1건" },
                  { label: "WBS", v: "4개" },
                  { label: "일정", v: "3건" },
                  { label: "담당자", v: "2명" },
                ].map((step, i, arr) => (
                  <div key={step.label} className="flex items-center gap-2">
                    <div className="rounded-md border border-border px-2.5 py-1.5 text-center">
                      <div className="text-foreground text-xs">{step.label}</div>
                      <div className="text-muted-foreground text-[11px]">{step.v}</div>
                    </div>
                    {i < arr.length - 1 && <ArrowRight className="size-3.5 text-muted-foreground" />}
                  </div>
                ))}
                <Button
                  variant="outline"
                  size="sm"
                  className="ml-auto"
                  onClick={() => toast("영향도 맵을 표시합니다.")}
                >
                  영향도 맵 보기
                </Button>
              </div>
            </CardContent>
          </Card>

          {/* B4. 담당자 변동 인수인계 체크리스트 */}
          <Card>
            <CardContent className="pt-5">
              <SectionTitle
                icon={<ClipboardCheck className="size-4" />}
                title="B4. 담당자 변동 인수인계 체크리스트"
              />
              <div className="flex items-start gap-4">
                <div className="grid flex-1 grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2">
                  {handover.map((h) => (
                    <label key={h.id} className="flex items-center gap-2 cursor-pointer">
                      <Checkbox checked={h.done} onCheckedChange={() => toggleHandover(h.id)} />
                      <span
                        className={cn(
                          "text-sm",
                          h.done ? "text-muted-foreground line-through" : "text-foreground",
                        )}
                      >
                        {h.label}
                      </span>
                    </label>
                  ))}
                </div>
                <RingProgress done={doneCount} total={handover.length} />
              </div>
            </CardContent>
          </Card>

          {/* 빠른 조치 도구 */}
          <Card>
            <CardContent className="pt-5">
              <SectionTitle
                icon={<Bot className="size-4" />}
                title="빠른 조치 도구"
                desc="즉시 실행 가능한 빠른 조치 도구입니다."
              />
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {quickTools.map((q, i) => {
                  const Icon = [Bell, MessageSquare, PauseCircle, CheckCircle][i % 4];
                  return (
                    <Button
                      key={q.id}
                      variant="outline"
                      size="sm"
                      onClick={() => toast.success(`'${q.label}' 실행`)}
                    >
                      <Icon className="size-4" /> {q.label}
                    </Button>
                  );
                })}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

function SectionTitle({
  icon,
  title,
  desc,
  right,
  compact,
}: {
  icon: React.ReactNode;
  title: string;
  desc?: string;
  right?: React.ReactNode;
  compact?: boolean;
}) {
  return (
    <div className={cn("flex items-start justify-between gap-3", compact ? "mb-0" : "mb-4")}>
      <div className="flex items-center gap-2">
        <span className="flex size-7 items-center justify-center rounded-md bg-blue-50 text-blue-600">
          {icon}
        </span>
        <div className="leading-tight">
          <div className="text-foreground text-sm">{title}</div>
          {desc && <div className="text-muted-foreground text-xs">{desc}</div>}
        </div>
      </div>
      {right}
    </div>
  );
}

function RingProgress({ done, total }: { done: number; total: number }) {
  const pct = total === 0 ? 0 : Math.round((done / total) * 100);
  const size = 64;
  const stroke = 6;
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const offset = c - (pct / 100) * c;
  return (
    <div className="relative shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={r} strokeWidth={stroke} className="fill-none stroke-muted" />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          strokeWidth={stroke}
          strokeDasharray={c}
          strokeDashoffset={offset}
          strokeLinecap="round"
          className="fill-none stroke-blue-600 transition-all"
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-foreground text-sm">
          {done}/{total}
        </span>
        <span className="text-muted-foreground text-[10px]">체크 완료</span>
      </div>
    </div>
  );
}
