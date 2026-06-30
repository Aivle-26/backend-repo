import {
  TrendingUp,
  CalendarClock,
  ListChecks,
  AlertTriangle,
  UploadCloud,
  Sparkles,
  FileText,
} from "lucide-react";
import { toast } from "sonner";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/app/components/ui/table";
import {
  KPI_PM,
  AI_SUMMARY,
  REQUIREMENTS,
  TEAM,
  RISKS,
} from "@/app/data/mock";
import { WorkflowFooter } from "@/app/components/common/WorkflowFooter";

function priorityVariant(p: string) {
  if (p === "높음") return "destructive" as const;
  if (p === "중간") return "secondary" as const;
  return "outline" as const;
}

export function PmDashboard() {
  return (
    <div className="space-y-6">
      {/* KPI */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard
          icon={<TrendingUp className="size-4" />}
          label="전체 진행률"
          value={`${KPI_PM.progress}%`}
          extra={<Progress value={KPI_PM.progress} className="mt-3" />}
        />
        <KpiCard
          icon={<CalendarClock className="size-4" />}
          label="남은 일수"
          value={`${KPI_PM.daysLeft}일`}
        />
        <KpiCard
          icon={<ListChecks className="size-4" />}
          label="진행 중 업무"
          value={`${KPI_PM.inProgress}건`}
        />
        <KpiCard
          icon={<AlertTriangle className="size-4 text-destructive" />}
          label="고위험 항목"
          value={`${KPI_PM.highRisk}건`}
        />
      </div>

      {/* Upload + AI summary */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle>RFP 공고문 업로드</CardTitle>
            <CardDescription>PDF 형식의 공고문을 업로드하면 AI가 자동 분석합니다.</CardDescription>
          </CardHeader>
          <CardContent>
            <div
              onClick={() => toast.success("공고문이 업로드되었습니다. (mock)")}
              className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-border bg-muted/40 py-12 text-center transition-colors hover:bg-muted"
            >
              <UploadCloud className="size-7 text-muted-foreground" />
              <div className="text-foreground">파일을 끌어다 놓거나 클릭하여 업로드</div>
              <div className="text-muted-foreground text-xs">PDF · 최대 50MB</div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="size-4" /> AI 분석 요약
            </CardTitle>
            <CardDescription>도시인프라-rfp-2024.pdf 분석 결과</CardDescription>
          </CardHeader>
          <CardContent>
            <ul className="space-y-3">
              {AI_SUMMARY.map((line) => (
                <li key={line} className="flex items-start gap-2">
                  <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" />
                  <span className="text-foreground text-sm">{line}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>

      {/* Requirements preview */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <FileText className="size-4" /> 요구사항 미리보기
          </CardTitle>
          <CardDescription>AI가 추출한 요구사항 일부입니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">#</TableHead>
                <TableHead>요구사항</TableHead>
                <TableHead className="w-24">분류</TableHead>
                <TableHead className="w-24">우선순위</TableHead>
                <TableHead className="w-24">상태</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {REQUIREMENTS.slice(0, 5).map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="text-muted-foreground">{r.id}</TableCell>
                  <TableCell>{r.text}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{r.category}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={priorityVariant(r.priority)}>{r.priority}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{r.status}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* Team + Risks */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle>팀원 업무 현황</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {TEAM.map((m) => {
              const pct = Math.round((m.done / m.total) * 100);
              return (
                <div key={m.id} className="space-y-1.5">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-foreground">
                      {m.name}{" "}
                      <span className="text-muted-foreground">· {m.role}</span>
                    </span>
                    <span className="text-muted-foreground">
                      {m.done}/{m.total}
                    </span>
                  </div>
                  <Progress value={pct} />
                </div>
              );
            })}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <AlertTriangle className="size-4 text-destructive" /> 리스크 알림
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {RISKS.map((risk) => (
              <div
                key={risk.id}
                className="rounded-lg border border-border p-3"
              >
                <div className="flex items-center justify-between">
                  <span className="text-foreground">{risk.title}</span>
                  <Badge
                    variant={risk.level === "높음" ? "destructive" : "secondary"}
                  >
                    {risk.level}
                  </Badge>
                </div>
                <p className="text-muted-foreground text-sm mt-1">
                  {risk.description}
                </p>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>

      <WorkflowFooter />
    </div>
  );
}

interface KpiCardProps {
  icon: React.ReactNode;
  label: string;
  value: string;
  extra?: React.ReactNode;
}

function KpiCard({ icon, label, value, extra }: KpiCardProps) {
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm">{label}</span>
          <span className="text-muted-foreground">{icon}</span>
        </div>
        <div className="mt-2 text-foreground text-2xl">{value}</div>
        {extra}
      </CardContent>
    </Card>
  );
}
