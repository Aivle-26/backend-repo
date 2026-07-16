import { useMemo, useState } from "react";
import {
  ArrowLeft,
  Check,
  Sparkles,
  Plus,
  X,
  Play,
  ChevronLeft,
  ChevronRight,
  CheckCircle2,
  AlertTriangle,
  Users,
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
import { Checkbox } from "@/app/components/ui/checkbox";
import { Progress } from "@/app/components/ui/progress";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { cn } from "@/app/components/ui/utils";
import { projectRepository } from "@/app/api/projectRepository";
import { WIZARD_STEPS, type ProjectSummary } from "@/app/data/demoData";

interface ProjectWizardProps {
  project: ProjectSummary;
  onBack: () => void;
  onStart: (id: string) => void;
}

function priorityVariant(p: string) {
  if (p === "높음") return "destructive" as const;
  if (p === "중간") return "secondary" as const;
  return "outline" as const;
}

export function ProjectWizard({ project, onBack, onStart }: ProjectWizardProps) {
  const seed = useMemo(() => projectRepository.getPmAssign(), []);
  const labor = useMemo(() => projectRepository.getPmRisk().laborChecks, []);

  const [step, setStep] = useState(Math.min(project.wizardStep, WIZARD_STEPS.length - 1));
  const [done, setDone] = useState<boolean[]>(() =>
    WIZARD_STEPS.map((_, i) => i < project.wizardStep),
  );

  // 단계별 상태
  const [reqs, setReqs] = useState(() =>
    (project.requirements ?? seed.requirements).map((r) => ({
      id: r.id,
      text: r.text,
      category: r.category,
      priority: r.priority,
    })),
  );
  const [newReq, setNewReq] = useState("");
  const [wbs, setWbs] = useState<string[]>([
    "요구사항 분석 · 정의",
    "화면 설계(IA/와이어프레임)",
    "API/데이터 설계",
    "개발(FE/BE)",
    "통합 테스트",
    "배포 · 안정화",
  ]);
  const [newWbs, setNewWbs] = useState("");
  const [milestones, setMilestones] = useState([
    { label: "요구사항 확정", date: "2026-07-25" },
    { label: "설계 완료", date: "2026-08-10" },
    { label: "1차 개발 완료", date: "2026-09-05" },
    { label: "오픈", date: "2026-09-30" },
  ]);
  const [laborState, setLaborState] = useState(() =>
    labor.map((l) => ({ label: l.label, ok: l.ok })),
  );
  const [estimate, setEstimate] = useState("3,200");

  const markDone = (i: number, v: boolean) =>
    setDone((prev) => prev.map((x, idx) => (idx === i ? v : x)));

  const allDone = done.every(Boolean);

  const goNext = () => {
    markDone(step, true);
    if (step < WIZARD_STEPS.length - 1) setStep(step + 1);
    else toast.success("모든 단계를 완료했어요. 이제 시작할 수 있어요.");
  };

  const start = () => {
    if (!allDone) return toast.error("모든 단계를 완료해야 시작할 수 있어요.");
    onStart(project.id);
    toast.success(`"${project.name}" 프로젝트를 시작했어요.`);
  };

  return (
    <div className="space-y-5">
      <Button variant="ghost" size="sm" className="-ml-2" onClick={onBack}>
        <ArrowLeft className="size-4" /> 프로젝트 목록
      </Button>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="leading-tight">
          <div className="flex items-center gap-2">
            <h2 className="text-foreground text-lg">{project.name}</h2>
            <Badge variant="outline" className="border-amber-200 bg-amber-50 font-normal text-amber-700">
              준비
            </Badge>
          </div>
          <div className="text-muted-foreground text-sm mt-0.5">{project.client}</div>
        </div>
        <Button disabled={!allDone} onClick={start}>
          <Play className="size-4" /> 프로젝트 시작
        </Button>
      </div>

      {/* 스텝바 */}
      <div className="flex flex-wrap items-center gap-1.5">
        {WIZARD_STEPS.map((label, i) => {
          const active = i === step;
          const complete = done[i];
          return (
            <button
              key={label}
              onClick={() => setStep(i)}
              className={cn(
                "inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm transition-colors",
                active
                  ? "bg-primary text-primary-foreground"
                  : complete
                    ? "bg-emerald-50 text-emerald-700"
                    : "bg-muted text-muted-foreground hover:bg-muted/70",
              )}
            >
              <span
                className={cn(
                  "flex size-4 items-center justify-center rounded-full text-[11px]",
                  active ? "bg-white/25" : complete ? "bg-emerald-200/60" : "bg-background",
                )}
              >
                {complete ? <Check className="size-3" /> : i + 1}
              </span>
              {label}
            </button>
          );
        })}
      </div>

      {/* 단계 콘텐츠 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* ===== 1. 요구사항 ===== */}
        {step === 0 && (
          <>
            <AiCard title="AI가 추출한 요구사항" desc={`초기 문서에서 ${reqs.length}건을 추출했어요.`}>
              <ul className="space-y-2">
                {reqs.slice(0, 5).map((r) => (
                  <li key={r.id} className="flex items-start gap-2 text-sm">
                    <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-blue-500" />
                    <span className="text-foreground">{r.text}</span>
                  </li>
                ))}
              </ul>
            </AiCard>
            <PmCard title="요구사항 확정" desc="추가·삭제하고 우선순위를 조정하세요.">
              <div className="space-y-2">
                {reqs.map((r) => (
                  <div key={r.id} className="flex items-center gap-2 rounded-md border border-border px-2.5 py-1.5">
                    <Badge variant="outline" className="font-normal">{r.category}</Badge>
                    <span className="flex-1 truncate text-foreground text-sm">{r.text}</span>
                    <button
                      onClick={() =>
                        setReqs((prev) =>
                          prev.map((x) =>
                            x.id === r.id
                              ? { ...x, priority: x.priority === "높음" ? "중간" : "높음" }
                              : x,
                          ),
                        )
                      }
                    >
                      <Badge variant={priorityVariant(r.priority)}>{r.priority}</Badge>
                    </button>
                    <button
                      onClick={() => setReqs((prev) => prev.filter((x) => x.id !== r.id))}
                      className="text-muted-foreground hover:text-destructive"
                      aria-label="삭제"
                    >
                      <X className="size-4" />
                    </button>
                  </div>
                ))}
              </div>
              <div className="mt-3 flex gap-2">
                <Input
                  value={newReq}
                  onChange={(e) => setNewReq(e.target.value)}
                  placeholder="요구사항 직접 추가"
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && newReq.trim()) {
                      setReqs((prev) => [
                        ...prev,
                        { id: Date.now(), text: newReq.trim(), category: "기타", priority: "중간" },
                      ]);
                      setNewReq("");
                    }
                  }}
                />
                <Button
                  variant="outline"
                  onClick={() => {
                    if (!newReq.trim()) return;
                    setReqs((prev) => [
                      ...prev,
                      { id: Date.now(), text: newReq.trim(), category: "기타", priority: "중간" },
                    ]);
                    setNewReq("");
                  }}
                >
                  <Plus className="size-4" /> 추가
                </Button>
              </div>
            </PmCard>
          </>
        )}

        {/* ===== 2. WBS ===== */}
        {step === 1 && (
          <>
            <AiCard title="AI 작업 분해(WBS) 제안" desc="요구사항을 작업 단위로 분해했어요.">
              <ol className="space-y-2">
                {wbs.map((w, i) => (
                  <li key={w} className="flex items-center gap-2 text-sm">
                    <span className="flex size-5 items-center justify-center rounded-md bg-blue-50 text-blue-600 text-xs">
                      {i + 1}
                    </span>
                    <span className="text-foreground">{w}</span>
                  </li>
                ))}
              </ol>
            </AiCard>
            <PmCard title="WBS 편집" desc="작업을 추가하거나 제거하세요.">
              <div className="space-y-2">
                {wbs.map((w, i) => (
                  <div key={w} className="flex items-center gap-2 rounded-md border border-border px-2.5 py-1.5">
                    <span className="text-muted-foreground text-xs">{i + 1}</span>
                    <span className="flex-1 truncate text-foreground text-sm">{w}</span>
                    <button
                      onClick={() => setWbs((prev) => prev.filter((_, idx) => idx !== i))}
                      className="text-muted-foreground hover:text-destructive"
                      aria-label="삭제"
                    >
                      <X className="size-4" />
                    </button>
                  </div>
                ))}
              </div>
              <div className="mt-3 flex gap-2">
                <Input
                  value={newWbs}
                  onChange={(e) => setNewWbs(e.target.value)}
                  placeholder="작업 추가"
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && newWbs.trim()) {
                      setWbs((prev) => [...prev, newWbs.trim()]);
                      setNewWbs("");
                    }
                  }}
                />
                <Button
                  variant="outline"
                  onClick={() => {
                    if (!newWbs.trim()) return;
                    setWbs((prev) => [...prev, newWbs.trim()]);
                    setNewWbs("");
                  }}
                >
                  <Plus className="size-4" /> 추가
                </Button>
              </div>
            </PmCard>
          </>
        )}

        {/* ===== 3. 담당자 ===== */}
        {step === 2 && (
          <>
            <AiCard title="AI 담당자 추천" desc="요구사항 성격에 맞춰 팀원을 배분했어요.">
              <div className="space-y-2">
                {seed.team.map((m) => (
                  <div key={m.id} className="flex items-center gap-2 text-sm">
                    <Avatar className="size-7">
                      <AvatarFallback className="text-[10px]">{m.name.slice(0, 1)}</AvatarFallback>
                    </Avatar>
                    <span className="text-foreground">{m.name}</span>
                    <span className="text-muted-foreground text-xs">· {m.role}</span>
                    <Badge variant="secondary" className="ml-auto font-normal">
                      추천 {m.total}건
                    </Badge>
                  </div>
                ))}
              </div>
            </AiCard>
            <PmCard title="담당자 워크로드" desc="배분을 확인하고 조정하세요.">
              <div className="space-y-3">
                {seed.team.map((m) => {
                  const pct = Math.round((m.done / m.total) * 100);
                  return (
                    <div key={m.id} className="space-y-1.5">
                      <div className="flex items-center gap-2 text-sm">
                        <Users className="size-3.5 text-muted-foreground" />
                        <span className="text-foreground">{m.name}</span>
                        <span className="ml-auto text-muted-foreground text-xs">
                          {m.done}/{m.total}
                        </span>
                      </div>
                      <Progress value={pct} />
                    </div>
                  );
                })}
              </div>
            </PmCard>
          </>
        )}

        {/* ===== 4. 일정 ===== */}
        {step === 3 && (
          <>
            <AiCard title="AI 마일스톤 제안" desc="주요 일정을 제안했어요.">
              <ol className="relative space-y-3 pl-4">
                {milestones.map((m) => (
                  <li key={m.label} className="text-sm">
                    <span className="absolute -left-0 mt-1.5 size-2 -translate-x-1/2 rounded-full bg-blue-500" />
                    <div className="text-foreground">{m.label}</div>
                    <div className="text-muted-foreground text-xs">{m.date}</div>
                  </li>
                ))}
              </ol>
            </AiCard>
            <PmCard title="일정 조정" desc="마일스톤 날짜를 수정하세요.">
              <div className="space-y-2">
                {milestones.map((m, i) => (
                  <div key={m.label} className="flex items-center gap-2">
                    <span className="flex-1 truncate text-foreground text-sm">{m.label}</span>
                    <Input
                      type="date"
                      value={m.date}
                      onChange={(e) =>
                        setMilestones((prev) =>
                          prev.map((x, idx) => (idx === i ? { ...x, date: e.target.value } : x)),
                        )
                      }
                      className="h-8 w-40"
                    />
                  </div>
                ))}
              </div>
            </PmCard>
          </>
        )}

        {/* ===== 5. 노동법·기업가이드 ===== */}
        {step === 4 && (
          <>
            <AiCard title="AI 준수 검토" desc="노동법·사내 가이드 준수 여부를 점검했어요.">
              <div className="space-y-2">
                {laborState.map((l) => (
                  <div key={l.label} className="flex items-center gap-2 text-sm">
                    {l.ok ? (
                      <CheckCircle2 className="size-4 text-emerald-500" />
                    ) : (
                      <AlertTriangle className="size-4 text-amber-500" />
                    )}
                    <span className="text-foreground">{l.label}</span>
                  </div>
                ))}
              </div>
            </AiCard>
            <PmCard title="PM 확인" desc="검토 항목을 확인 처리하세요.">
              <div className="space-y-2">
                {laborState.map((l, i) => (
                  <label key={l.label} className="flex items-center gap-2 cursor-pointer">
                    <Checkbox
                      checked={l.ok}
                      onCheckedChange={() =>
                        setLaborState((prev) =>
                          prev.map((x, idx) => (idx === i ? { ...x, ok: !x.ok } : x)),
                        )
                      }
                    />
                    <span className={cn("text-sm", l.ok ? "text-foreground" : "text-muted-foreground")}>
                      {l.label}
                    </span>
                  </label>
                ))}
              </div>
            </PmCard>
          </>
        )}

        {/* ===== 6. 견적 ===== */}
        {step === 5 && (
          <>
            <AiCard title="AI 예상 견적" desc="요구사항·공수 기반 예상 범위예요.">
              <div className="rounded-lg bg-muted/50 p-4 text-center">
                <div className="text-muted-foreground text-xs">예상 견적 범위</div>
                <div className="text-foreground text-2xl mt-1">3,200 ~ 3,600만원</div>
              </div>
              <p className="mt-3 text-muted-foreground text-xs">
                요구사항 {reqs.length}건 · WBS {wbs.length}개 기준 산정
              </p>
            </AiCard>
            <PmCard title="견적 확정" desc="최종 견적을 입력하세요.">
              <div className="flex items-center gap-2">
                <Input
                  value={estimate}
                  onChange={(e) => setEstimate(e.target.value)}
                  className="text-right"
                />
                <span className="text-muted-foreground text-sm">만원</span>
              </div>
              <p className="mt-2 text-muted-foreground text-xs">
                확정 견적: {estimate}만원
              </p>
            </PmCard>
          </>
        )}
      </div>

      {/* 하단 내비게이션 */}
      <div className="flex items-center justify-between border-t border-border pt-4">
        <Button
          variant="outline"
          disabled={step === 0}
          onClick={() => setStep(step - 1)}
        >
          <ChevronLeft className="size-4" /> 이전
        </Button>
        <div className="text-muted-foreground text-sm">
          {step + 1} / {WIZARD_STEPS.length} 단계 · {done.filter(Boolean).length}개 완료
        </div>
        {step < WIZARD_STEPS.length - 1 ? (
          <Button onClick={goNext}>
            저장 후 다음 <ChevronRight className="size-4" />
          </Button>
        ) : (
          <Button
            onClick={() => {
              markDone(step, true);
              toast.success("마지막 단계 저장 완료. 상단 '프로젝트 시작'을 눌러 착수하세요.");
            }}
          >
            <Check className="size-4" /> 저장
          </Button>
        )}
      </div>
    </div>
  );
}

function AiCard({
  title,
  desc,
  children,
}: {
  title: string;
  desc: string;
  children: React.ReactNode;
}) {
  return (
    <Card className="border-blue-100 bg-blue-50/30">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-sm">
          <span className="flex size-6 items-center justify-center rounded-md bg-blue-600 text-white">
            <Sparkles className="size-3.5" />
          </span>
          {title}
        </CardTitle>
        <CardDescription>{desc}</CardDescription>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

function PmCard({
  title,
  desc,
  children,
}: {
  title: string;
  desc: string;
  children: React.ReactNode;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">{title}</CardTitle>
        <CardDescription>{desc}</CardDescription>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}
