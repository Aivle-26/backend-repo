import {
  useMemo,
  useState,
  type Dispatch,
  type SetStateAction,
} from "react";
import {
  FolderPlus,
  Search,
  Loader2,
  Clock,
  Activity,
  CheckCircle2,
  PencilRuler,
  Play,
  Pencil,
  Trash2,
  ArrowRight,
  CalendarClock,
  AlertTriangle,
  FileText,
  UploadCloud,
} from "lucide-react";
import { toast } from "sonner";
import {
  Card,
  CardContent,
} from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Badge } from "@/app/components/ui/badge";
import { Input } from "@/app/components/ui/input";
import { Progress } from "@/app/components/ui/progress";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/app/components/ui/dialog";
import { cn } from "@/app/components/ui/utils";
import { CountUp } from "@/app/components/common/CountUp";
import {
  WIZARD_STEPS,
  type ProjectDoc,
  type ProjectDocType,
  type ProjectStatus,
  type ProjectSummary,
} from "@/app/data/demoData";

import { DocPicker } from "@/app/components/pm/DocPicker";
import { projectRepository } from "@/app/api/projectRepository";

type Filter = "전체" | ProjectStatus;

const STATUS_META: Record<
  ProjectStatus,
  { label: string; badge: string; icon: React.ComponentType<{ className?: string }> }
> = {
  분석중: { label: "분석중", badge: "bg-muted text-muted-foreground", icon: Loader2 },
  준비: { label: "준비", badge: "bg-amber-50 text-amber-700 border-amber-200", icon: PencilRuler },
  승인대기: { label: "승인대기", badge: "bg-orange-50 text-orange-700 border-orange-200", icon: Clock },
  진행중: { label: "진행중", badge: "bg-blue-50 text-blue-700 border-blue-200", icon: Activity },
  완료: { label: "완료", badge: "bg-emerald-50 text-emerald-700 border-emerald-200", icon: CheckCircle2 },
};

const FILTERS: Filter[] = ["전체", "진행중", "준비", "승인대기", "완료"];

interface ProjectBoardProps {
  projects: ProjectSummary[];
  setProjects: Dispatch<SetStateAction<ProjectSummary[]>>;
  onOpenOperational: (project: ProjectSummary) => void;
  onOpenWizard: (project: ProjectSummary) => void;
  onExtract: (project: ProjectSummary) => void;
}

export function ProjectBoard({
  projects,
  setProjects,
  onOpenOperational,
  onOpenWizard,
  onExtract,
}: ProjectBoardProps) {
  const [filter, setFilter] = useState<Filter>("전체");
  const [query, setQuery] = useState("");
  const [detail, setDetail] = useState<ProjectSummary | null>(null);
  const [newOpen, setNewOpen] = useState(false);
  const [newName, setNewName] = useState("");
  const [pendingDocs, setPendingDocs] = useState<ProjectDoc[]>([]);
  const [uploadFor, setUploadFor] = useState<ProjectSummary | null>(null);
  const [uploadDocs, setUploadDocs] = useState<ProjectDoc[]>([]);

  const counts = useMemo(() => {
    const c: Record<Filter, number> = {
      전체: projects.length,
      진행중: 0,
      준비: 0,
      승인대기: 0,
      완료: 0,
    };
    projects.forEach((p) => {
      if (p.status in c) c[p.status as Filter] += 1;
    });
    return c;
  }, [projects]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return projects.filter((p) => {
      const byTab = filter === "전체" || p.status === filter;
      const byQ =
        !q ||
        p.name.toLowerCase().includes(q) ||
        p.client.toLowerCase().includes(q);
      return byTab && byQ;
    });
  }, [projects, filter, query]);

  const startProject = (id: string) => {
    setProjects((prev) =>
      prev.map((p) =>
        p.id === id ? { ...p, status: "진행중", progress: 5, updatedAt: "방금" } : p,
      ),
    );
    setDetail(null);
    toast.success("프로젝트를 시작했어요. 진행중으로 이동합니다.");
  };

  const removeProject = (id: string) => {
    setProjects((prev) => prev.filter((p) => p.id !== id));
    setDetail(null);
    toast("프로젝트를 삭제했어요.");
  };

  const openNew = () => {
    setNewName("");
    setPendingDocs([]);
    setNewOpen(true);
  };

  const createProject = async () => {
    if (!newName.trim()) return toast.error("프로젝트 이름을 입력하세요.");
    const uploadFiles = pendingDocs
      .map((doc) => doc.file)
      .filter((file): file is File => file instanceof File);

    if (pendingDocs.length > 0 && uploadFiles.length !== pendingDocs.length) {
      return toast.error("실제 업로드 파일을 확인할 수 없습니다. 문서를 다시 선택해주세요.");
    }

    if (uploadFiles.length > 0) {
      try {
        const response = await projectRepository.createDraftFromDocuments(uploadFiles, true);
        const p: ProjectSummary = {
          id: String(response.projectId),
          name: response.projectName,
          client: "AI 분석 결과",
          status: "준비",
          progress: 0,
          dueDate: "-",
          riskCount: 0,
          reqCount: response.requirementCount,
          wizardStep: 0,
          estimate: "-",
          updatedAt: "방금",
          docs: pendingDocs.map(({ file, ...doc }) => doc),
        };
        setProjects((prev) => [p, ...prev]);
        setNewOpen(false);
        setNewName("");
        setPendingDocs([]);
        toast.success(response.message || "AI 문서 분석 결과로 프로젝트가 생성되었습니다.");
        onExtract(p);
      } catch (error) {
        toast.error(error instanceof Error ? error.message : "프로젝트 문서 분석 요청에 실패했습니다.");
      }
      return;
    }

    const id = `prj-${Date.now()}`;
    const hasDocs = pendingDocs.length > 0;
    const p: ProjectSummary = {
      id,
      name: newName.trim(),
      client: "신규 · 미지정",
      status: hasDocs ? "분석중" : "준비",
      progress: 0,
      dueDate: "-",
      riskCount: 0,
      reqCount: 0,
      wizardStep: 0,
      estimate: "-",
      updatedAt: "방금",
      docs: pendingDocs,
    };
    setProjects((prev) => [p, ...prev]);
    setNewOpen(false);
    setNewName("");
    setPendingDocs([]);
    if (hasDocs) {
      // 문서가 있으면 AI 추출 화면으로 이동
      onExtract(p);
    } else {
      toast.success(`"${p.name}" 생성 — 문서는 나중에 업로드할 수 있어요.`);
    }
  };

  const openUpload = (p: ProjectSummary) => {
    setDetail(null);
    setUploadDocs(p.docs);
    setUploadFor(p);
  };

  const saveUpload = () => {
    if (!uploadFor) return;
    const target = uploadFor;
    const firstUpload =
      target.docs.length === 0 && uploadDocs.length > 0 && target.status === "준비";
    const updated: ProjectSummary = {
      ...target,
      docs: uploadDocs,
      status: firstUpload ? "분석중" : target.status,
      updatedAt: "방금",
    };
    setProjects((prev) => prev.map((x) => (x.id === target.id ? updated : x)));
    setUploadFor(null);
    if (firstUpload) {
      // 문서가 없던 준비 프로젝트에 처음 올리면 AI 추출 화면으로
      onExtract(updated);
    } else {
      toast.success(`"${target.name}" 문서를 저장했어요.`);
    }
  };

  const openWizard = (p: ProjectSummary) => {
    setDetail(null);
    onOpenWizard(p);
  };

  return (
    <div className="space-y-5">
      {/* 상단: 필터 + 검색 + 새 프로젝트 */}
      <div className="flex flex-wrap items-center gap-2">
        {FILTERS.map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={cn(
              "inline-flex items-center gap-1.5 rounded-full px-3.5 py-1.5 text-sm transition-colors",
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
        <div className="relative ml-auto">
          <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="프로젝트·고객사 검색"
            className="h-9 w-56 pl-8"
          />
        </div>
        <Button onClick={openNew}>
          <FolderPlus className="size-4" /> 새 프로젝트
        </Button>
      </div>

      {/* 카드 그리드 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
        {filtered.map((p) => (
          <ProjectCard
            key={p.id}
            project={p}
            onOpenName={() => setDetail(p)}
            onOpen={() => onOpenOperational(p)}
            onEdit={() => openWizard(p)}
            onDelete={() => removeProject(p.id)}
            onStart={() => startProject(p.id)}
          />
        ))}
        {filtered.length === 0 && (
          <div className="col-span-full rounded-xl border border-dashed border-border py-16 text-center text-muted-foreground">
            해당 상태의 프로젝트가 없습니다.
          </div>
        )}
      </div>

      {/* 상세 다이얼로그 */}
      <Dialog open={!!detail} onOpenChange={(o) => !o && setDetail(null)}>
        <DialogContent>
          {detail && (
            <>
              <DialogHeader>
                <div className="flex items-center gap-2">
                  <StatusBadge status={detail.status} />
                </div>
                <DialogTitle className="mt-1">{detail.name}</DialogTitle>
                <DialogDescription>{detail.client}</DialogDescription>
              </DialogHeader>

              <div className="grid grid-cols-2 gap-3 py-1">
                <DetailStat label="상태" value={STATUS_META[detail.status].label} />
                <DetailStat label="요구사항" value={`${detail.reqCount}건`} />
                {detail.status === "진행중" || detail.status === "완료" ? (
                  <>
                    <DetailStat label="진행률" value={`${detail.progress}%`} />
                    <DetailStat label="마감" value={detail.dueDate} />
                    <DetailStat label="리스크" value={`${detail.riskCount}건`} />
                  </>
                ) : (
                  <>
                    <DetailStat
                      label="준비 단계"
                      value={`${detail.wizardStep}/${WIZARD_STEPS.length}`}
                    />
                    <DetailStat label="견적" value={detail.estimate} />
                  </>
                )}
                <DetailStat label="업데이트" value={detail.updatedAt} />
              </div>

              {/* 초기 문서 */}
              <div className="rounded-lg border border-border p-3">
                <div className="flex items-center justify-between">
                  <span className="text-foreground text-sm">
                    초기 문서 {detail.docs.length}건
                  </span>
                  {detail.status !== "완료" && (
                    <Button variant="outline" size="sm" onClick={() => openUpload(detail)}>
                      <UploadCloud className="size-3.5" /> 문서 업로드
                    </Button>
                  )}
                </div>
                {detail.docs.length > 0 ? (
                  <div className="mt-2 space-y-1">
                    {detail.docs.map((d, i) => (
                      <div key={`${d.name}-${i}`} className="flex items-center gap-2 text-sm">
                        <FileText className="size-3.5 text-muted-foreground" />
                        <span className="flex-1 truncate text-foreground">{d.name}</span>
                        <Badge variant="secondary" className="font-normal">
                          {d.type}
                        </Badge>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="mt-1 text-muted-foreground text-xs">
                    아직 업로드된 문서가 없어요. 나중에 올릴 수 있어요.
                  </p>
                )}
              </div>

              <DialogFooter>
                {detail.status === "진행중" || detail.status === "완료" ? (
                  <Button
                    onClick={() => {
                      const d = detail;
                      setDetail(null);
                      onOpenOperational(d);
                    }}
                  >
                    운영 대시보드 열기 <ArrowRight className="size-4" />
                  </Button>
                ) : detail.status === "분석중" ? (
                  <Button disabled>
                    <Loader2 className="size-4 animate-spin" /> AI 분석 중…
                  </Button>
                ) : (
                  <>
                    <Button variant="outline" onClick={() => removeProject(detail.id)}>
                      <Trash2 className="size-4" /> 삭제
                    </Button>
                    <Button variant="outline" onClick={() => openWizard(detail)}>
                      <Pencil className="size-4" /> 준비 마법사
                    </Button>
                    <Button
                      disabled={detail.wizardStep < WIZARD_STEPS.length}
                      onClick={() => startProject(detail.id)}
                    >
                      <Play className="size-4" /> 시작
                    </Button>
                  </>
                )}
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>

      {/* 새 프로젝트 다이얼로그 */}
      <Dialog open={newOpen} onOpenChange={setNewOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>새 프로젝트</DialogTitle>
            <DialogDescription>
              초기 문서를 올리면 AI가 요구사항을 추출해 '준비' 상태로 만들어요.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-1">
            <div className="space-y-2">
              <label className="text-sm text-foreground">프로젝트 이름</label>
              <Input
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                placeholder="예: 신규 커머스 플랫폼 구축"
                onKeyDown={(e) => e.key === "Enter" && createProject()}
                autoFocus
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm text-foreground">초기 문서 업로드</label>
              <DocPicker docs={pendingDocs} onChange={setPendingDocs} />
              <p className="text-muted-foreground text-xs">
                지금 문서가 없으면 그냥 생성하고 나중에 업로드해도 돼요.
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setNewOpen(false)}>
              취소
            </Button>
            <Button onClick={createProject}>
              <FolderPlus className="size-4" /> 생성
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 문서 업로드(나중에) 다이얼로그 */}
      <Dialog open={!!uploadFor} onOpenChange={(o) => !o && setUploadFor(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>문서 업로드</DialogTitle>
            <DialogDescription>
              {uploadFor ? `${uploadFor.name} · 초기 문서를 추가/수정하세요.` : ""}
            </DialogDescription>
          </DialogHeader>
          <div className="py-1">
            <DocPicker docs={uploadDocs} onChange={setUploadDocs} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setUploadFor(null)}>
              취소
            </Button>
            <Button onClick={saveUpload}>
              <UploadCloud className="size-4" /> 저장
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function StatusBadge({ status }: { status: ProjectStatus }) {
  const meta = STATUS_META[status];
  const Icon = meta.icon;
  return (
    <Badge variant="outline" className={cn("gap-1 font-normal", meta.badge)}>
      <Icon className={cn("size-3", status === "분석중" && "animate-spin")} />
      {meta.label}
    </Badge>
  );
}

function DetailStat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <div className="text-muted-foreground text-xs">{label}</div>
      <div className="text-foreground text-sm mt-0.5">{value}</div>
    </div>
  );
}

interface ProjectCardProps {
  project: ProjectSummary;
  onOpenName: () => void;
  onOpen: () => void;
  onEdit: () => void;
  onDelete: () => void;
  onStart: () => void;
}

function ProjectCard({
  project: p,
  onOpenName,
  onOpen,
  onEdit,
  onDelete,
  onStart,
}: ProjectCardProps) {
  const isActive = p.status === "진행중" || p.status === "완료";
  const isPrep = p.status === "준비" || p.status === "승인대기";
  const stepPct = Math.round((p.wizardStep / WIZARD_STEPS.length) * 100);

  return (
    <Card className="transition-shadow hover:shadow-md">
      <CardContent className="pt-5">
        <div className="flex items-start justify-between gap-2">
          <button onClick={onOpenName} className="min-w-0 text-left">
            <div className="text-foreground truncate hover:underline">{p.name}</div>
            <div className="text-muted-foreground text-xs truncate">{p.client}</div>
          </button>
          <StatusBadge status={p.status} />
        </div>

        {/* 상태별 본문 */}
        <div className="mt-4 min-h-[52px]">
          {isActive && (
            <>
              <div className="flex items-center justify-between text-sm mb-1.5">
                <span className="text-muted-foreground">진행률</span>
                <span className="text-foreground">
                  <CountUp value={`${p.progress}%`} />
                </span>
              </div>
              <Progress value={p.progress} />
              <div className="mt-3 flex items-center gap-4 text-muted-foreground text-xs">
                <span className="inline-flex items-center gap-1">
                  <CalendarClock className="size-3.5" /> {p.dueDate}
                </span>
                <span className="inline-flex items-center gap-1">
                  <AlertTriangle className="size-3.5" /> 리스크 {p.riskCount}
                </span>
              </div>
            </>
          )}

          {p.status === "준비" && (
            <>
              <div className="flex items-center justify-between text-sm mb-1.5">
                <span className="text-muted-foreground">준비 단계</span>
                <span className="text-foreground">
                  {p.wizardStep}/{WIZARD_STEPS.length}
                </span>
              </div>
              <Progress value={stepPct} />
              <div className="mt-3 text-muted-foreground text-xs">
                요구사항 {p.reqCount}건 · AI 추천 검토 중
              </div>
            </>
          )}

          {p.status === "승인대기" && (
            <div className="space-y-1.5 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">견적</span>
                <span className="text-foreground">{p.estimate}</span>
              </div>
              <div className="text-muted-foreground text-xs">
                요구사항 {p.reqCount}건 · 2사 승인 대기
              </div>
            </div>
          )}

          {p.status === "분석중" && (
            <div className="flex items-center gap-2 py-2 text-muted-foreground text-sm">
              <Loader2 className="size-4 animate-spin" /> AI가 초기 문서를 분석하고 있어요…
            </div>
          )}
        </div>

        {/* 액션 */}
        <div className="mt-4 flex items-center justify-end gap-1.5 border-t border-border pt-3">
          {isActive && (
            <Button variant="outline" size="sm" onClick={onOpen}>
              열기 <ArrowRight className="size-3.5" />
            </Button>
          )}
          {isPrep && (
            <>
              <Button variant="ghost" size="sm" onClick={onEdit}>
                <Pencil className="size-3.5" /> 수정
              </Button>
              <Button
                variant="ghost"
                size="sm"
                className="text-muted-foreground"
                onClick={onDelete}
              >
                <Trash2 className="size-3.5" /> 삭제
              </Button>
              <Button
                size="sm"
                disabled={p.wizardStep < WIZARD_STEPS.length}
                onClick={onStart}
              >
                <Play className="size-3.5" /> 시작
              </Button>
            </>
          )}
          {p.status === "분석중" && (
            <Button variant="ghost" size="sm" className="text-muted-foreground" onClick={onDelete}>
              <Trash2 className="size-3.5" /> 삭제
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
