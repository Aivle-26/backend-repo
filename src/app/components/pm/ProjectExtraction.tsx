import { useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowLeft,
  Sparkles,
  FileText,
  Loader2,
  Plus,
  X,
  Check,
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
import { cn } from "@/app/components/ui/utils";
import {
  EXTRACTED_REQ_POOL,
  type ProjectRequirement,
  type ProjectSummary,
} from "@/app/data/demoData";

function priorityVariant(p: string) {
  if (p === "높음") return "destructive" as const;
  if (p === "중간") return "secondary" as const;
  return "outline" as const;
}

interface ProjectExtractionProps {
  project: ProjectSummary;
  onBack: () => void;
  onConfirm: (requirements: ProjectRequirement[]) => void;
}

export function ProjectExtraction({ project, onBack, onConfirm }: ProjectExtractionProps) {
  const [analyzing, setAnalyzing] = useState(true);
  const [progress, setProgress] = useState(0);
  const [reqs, setReqs] = useState<ProjectRequirement[]>([]);
  const [newReq, setNewReq] = useState("");
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  // 문서 기반 추출 결과(시뮬레이션)
  const generated = useMemo<ProjectRequirement[]>(() => {
    const docCount = project.docs.length || 1;
    const count = Math.min(EXTRACTED_REQ_POOL.length, Math.max(6, docCount * 4));
    return EXTRACTED_REQ_POOL.slice(0, count).map((r, i) => ({
      id: i + 1,
      text: r.text,
      category: r.category,
      priority: r.priority,
      source: project.docs.length ? project.docs[i % project.docs.length].name : "초기 문서",
    }));
  }, [project.docs]);

  // 분석 진행 애니메이션
  useEffect(() => {
    timer.current = setInterval(() => {
      setProgress((p) => {
        const next = p + Math.random() * 18 + 8;
        if (next >= 100) {
          if (timer.current) clearInterval(timer.current);
          setReqs(generated);
          setAnalyzing(false);
          return 100;
        }
        return next;
      });
    }, 220);
    return () => {
      if (timer.current) clearInterval(timer.current);
    };
  }, [generated]);

  const removeReq = (id: number) =>
    setReqs((prev) => prev.filter((r) => r.id !== id));
  const togglePriority = (id: number) =>
    setReqs((prev) =>
      prev.map((r) =>
        r.id === id
          ? { ...r, priority: r.priority === "높음" ? "중간" : "높음" }
          : r,
      ),
    );
  const addReq = () => {
    if (!newReq.trim()) return;
    setReqs((prev) => [
      ...prev,
      {
        id: Date.now(),
        text: newReq.trim(),
        category: "기타",
        priority: "중간",
        source: "직접 추가",
      },
    ]);
    setNewReq("");
  };

  const confirm = () => {
    if (reqs.length === 0) return toast.error("최소 1개 이상의 요구사항이 필요해요.");
    onConfirm(reqs);
    toast.success(`요구사항 ${reqs.length}건을 확정했어요. 준비 단계로 이동합니다.`);
  };

  return (
    <div className="space-y-5">
      <Button variant="ghost" size="sm" className="-ml-2" onClick={onBack}>
        <ArrowLeft className="size-4" /> 프로젝트 목록
      </Button>

      <div className="leading-tight">
        <div className="flex items-center gap-2">
          <span className="flex size-8 items-center justify-center rounded-lg bg-blue-600 text-white">
            <Sparkles className="size-4" />
          </span>
          <h2 className="text-foreground text-lg">{project.name} · AI 요구사항 추출</h2>
        </div>
        <p className="text-muted-foreground text-sm mt-1">
          업로드한 초기 문서를 분석해 요구사항을 뽑아냈어요. 검토 후 확정하세요.
        </p>
      </div>

      {/* 문서 목록 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-sm">분석 대상 문서 {project.docs.length}건</CardTitle>
        </CardHeader>
        <CardContent>
          {project.docs.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {project.docs.map((d, i) => (
                <span
                  key={`${d.name}-${i}`}
                  className="inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1 text-sm"
                >
                  <FileText className="size-3.5 text-muted-foreground" />
                  {d.name}
                  <Badge variant="secondary" className="font-normal">
                    {d.type}
                  </Badge>
                </span>
              ))}
            </div>
          ) : (
            <p className="text-muted-foreground text-sm">문서가 없어 기본 템플릿으로 추출합니다.</p>
          )}
        </CardContent>
      </Card>

      {/* 분석 중 / 결과 */}
      {analyzing ? (
        <Card>
          <CardContent className="py-10">
            <div className="mx-auto max-w-md text-center">
              <Loader2 className="mx-auto size-8 animate-spin text-blue-600" />
              <div className="mt-3 text-foreground">AI가 문서를 분석하고 있어요…</div>
              <div className="mt-1 text-muted-foreground text-sm">
                요구사항을 추출하는 중입니다.
              </div>
              <Progress value={progress} className="mt-4" />
              <div className="mt-1 text-muted-foreground text-xs">
                {Math.min(100, Math.round(progress))}%
              </div>
            </div>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-sm">추출된 요구사항 {reqs.length}건</CardTitle>
                <CardDescription>필요 없는 항목은 삭제하고, 우선순위를 조정하세요.</CardDescription>
              </div>
              <Button onClick={confirm}>
                <Check className="size-4" /> 요구사항 확정 → 준비
              </Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-2">
            {reqs.map((r) => (
              <div
                key={r.id}
                className="flex items-center gap-2 rounded-md border border-border px-3 py-2"
              >
                <Badge variant="outline" className="font-normal">
                  {r.category}
                </Badge>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-foreground text-sm">{r.text}</div>
                  <div className="text-muted-foreground text-xs">출처: {r.source}</div>
                </div>
                <button onClick={() => togglePriority(r.id)} aria-label="우선순위 전환">
                  <Badge variant={priorityVariant(r.priority)}>{r.priority}</Badge>
                </button>
                <button
                  onClick={() => removeReq(r.id)}
                  className="text-muted-foreground hover:text-destructive"
                  aria-label="삭제"
                >
                  <X className="size-4" />
                </button>
              </div>
            ))}

            <div className="flex gap-2 pt-1">
              <Input
                value={newReq}
                onChange={(e) => setNewReq(e.target.value)}
                placeholder="문서에서 누락된 요구사항 직접 추가"
                onKeyDown={(e) => e.key === "Enter" && addReq()}
              />
              <Button variant="outline" onClick={addReq}>
                <Plus className="size-4" /> 추가
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
