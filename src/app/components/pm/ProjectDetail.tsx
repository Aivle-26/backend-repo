import { useState } from "react";
import {
  ArrowLeft,
  TrendingUp,
  CalendarClock,
  AlertTriangle,
  FileText,
  Sparkles,
  ListTodo,
  Pencil,
} from "lucide-react";
import { toast } from "sonner";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Badge } from "@/app/components/ui/badge";
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
import { DocPicker } from "@/app/components/pm/DocPicker";
import { projectRepository } from "@/app/api/projectRepository";
import type {
  ProjectDoc,
  ProjectStatus,
  ProjectSummary,
} from "@/app/data/demoData";

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

interface ProjectDetailProps {
  project: ProjectSummary;
  onBack: () => void;
  onUpdateDocs: (docs: ProjectDoc[]) => void;
}

export function ProjectDetail({ project: p, onBack, onUpdateDocs }: ProjectDetailProps) {
  const { aiSummary, risks } = projectRepository.getPmDashboard();
  const [docOpen, setDocOpen] = useState(false);
  const [docDraft, setDocDraft] = useState<ProjectDoc[]>(p.docs);

  return (
    <div className="space-y-5">
      <Button variant="ghost" size="sm" className="-ml-2" onClick={onBack}>
        <ArrowLeft className="size-4" /> 프로젝트 목록
      </Button>

      {/* 프로젝트 헤더 */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="leading-tight">
          <div className="flex items-center gap-2">
            <h2 className="text-foreground text-lg">{p.name}</h2>
            <Badge variant="outline" className={cn("font-normal", statusClass(p.status))}>
              {p.status}
            </Badge>
          </div>
          <div className="text-muted-foreground text-sm mt-0.5">
            {p.client} · 업데이트 {p.updatedAt}
          </div>
        </div>
      </div>

      {/* KPI (프로젝트 실제 데이터) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <Card>
          <CardContent className="pt-5">
            <div className="flex items-center justify-between">
              <span className="text-muted-foreground text-sm">전체 진행률</span>
              <TrendingUp className="size-4 text-muted-foreground" />
            </div>
            <div className="mt-2 text-foreground text-2xl">
              <CountUp value={`${p.progress}%`} />
            </div>
            <Progress value={p.progress} className="mt-3" />
          </CardContent>
        </Card>
        <KpiCard
          icon={<CalendarClock className="size-4 text-muted-foreground" />}
          label="마감일"
          value={p.dueDate}
        />
        <KpiCard
          icon={<AlertTriangle className="size-4 text-destructive" />}
          label="고위험 항목"
          value={`${p.riskCount}건`}
        />
        <KpiCard
          icon={<FileText className="size-4 text-muted-foreground" />}
          label="요구사항"
          value={`${p.reqCount}건`}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* AI 분석 요약 */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="size-4" /> AI 분석 요약
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-3">
              {aiSummary.map((line) => (
                <li key={line} className="flex items-start gap-2">
                  <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" />
                  <span className="text-foreground text-sm">{line}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>

        {/* 상위 리스크 */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <AlertTriangle className="size-4 text-destructive" /> 상위 리스크
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {risks.map((risk) => (
              <div key={risk.id} className="rounded-lg border border-border p-3">
                <div className="flex items-center justify-between">
                  <span className="text-foreground text-sm">{risk.title}</span>
                  <Badge variant={risk.level === "높음" ? "destructive" : "secondary"}>
                    {risk.level}
                  </Badge>
                </div>
                <p className="text-muted-foreground text-sm mt-1">{risk.description}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>

      {/* 초기 문서 */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="flex items-center gap-2">
              <ListTodo className="size-4" /> 초기 문서 {p.docs.length}건
            </CardTitle>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setDocDraft(p.docs);
                setDocOpen(true);
              }}
            >
              <Pencil className="size-3.5" /> 문서 관리
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {p.docs.length > 0 ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {p.docs.map((d, i) => (
                <div
                  key={`${d.name}-${i}`}
                  className="flex items-center gap-2 rounded-md border border-border px-3 py-2"
                >
                  <span className="flex size-8 items-center justify-center rounded-md bg-red-50 text-red-600">
                    <FileText className="size-4" />
                  </span>
                  <span className="flex-1 truncate text-foreground text-sm">{d.name}</span>
                  <Badge variant="secondary" className="font-normal">
                    {d.type}
                  </Badge>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-muted-foreground text-sm">업로드된 문서가 없습니다.</p>
          )}
        </CardContent>
      </Card>

      {/* 문서 관리 다이얼로그 */}
      <Dialog open={docOpen} onOpenChange={setDocOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>문서 관리</DialogTitle>
            <DialogDescription>{p.name} · 초기 문서를 추가·수정·삭제하세요.</DialogDescription>
          </DialogHeader>
          <div className="py-1">
            <DocPicker docs={docDraft} onChange={setDocDraft} />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDocOpen(false)}>
              취소
            </Button>
            <Button
              onClick={() => {
                onUpdateDocs(docDraft);
                setDocOpen(false);
                toast.success("문서를 저장했어요.");
              }}
            >
              저장
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function KpiCard({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <Card>
      <CardContent className="pt-5">
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm">{label}</span>
          <span>{icon}</span>
        </div>
        <div className="mt-2 text-foreground text-2xl">
          <CountUp value={value} />
        </div>
      </CardContent>
    </Card>
  );
}
