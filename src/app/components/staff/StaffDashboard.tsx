import {
  ListTodo,
  AlarmClock,
  Eye,
  CheckCircle2,
  Sparkles,
  FileText,
} from "lucide-react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/app/components/ui/card";
import { Badge } from "@/app/components/ui/badge";
import {
  KPI_STAFF,
  TASKS,
  AI_TASK_HELPER,
  STAFF_FEEDBACK,
  REQUIREMENTS,
  type Task,
  type TaskColumn,
} from "@/app/data/mock";

const COLUMNS: { key: TaskColumn; label: string }[] = [
  { key: "todo", label: "할 일" },
  { key: "doing", label: "진행 중" },
  { key: "review", label: "검토 요청" },
  { key: "done", label: "완료" },
];

function priorityVariant(p: string) {
  if (p === "높음") return "destructive" as const;
  if (p === "중간") return "secondary" as const;
  return "outline" as const;
}

interface StaffDashboardProps {
  onOpenTask: (taskId: string) => void;
}

export function StaffDashboard({ onOpenTask }: StaffDashboardProps) {
  return (
    <div className="space-y-6">
      {/* KPI */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard icon={<ListTodo className="size-4" />} label="내 업무" value={`${KPI_STAFF.myTasks}건`} />
        <KpiCard icon={<AlarmClock className="size-4 text-destructive" />} label="마감 임박" value={`${KPI_STAFF.dueSoon}건`} />
        <KpiCard icon={<Eye className="size-4" />} label="검토 중" value={`${KPI_STAFF.inReview}건`} />
        <KpiCard icon={<CheckCircle2 className="size-4" />} label="완료" value={`${KPI_STAFF.completed}건`} />
      </div>

      {/* Kanban */}
      <Card>
        <CardHeader>
          <CardTitle>업무 보드</CardTitle>
          <CardDescription>업무 카드를 클릭하면 상세 화면으로 이동합니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
            {COLUMNS.map((col) => {
              const items = TASKS.filter((t) => t.column === col.key);
              return (
                <div key={col.key} className="rounded-lg bg-muted/50 p-3">
                  <div className="flex items-center justify-between mb-3 px-1">
                    <span className="text-foreground text-sm">{col.label}</span>
                    <Badge variant="secondary">{items.length}</Badge>
                  </div>
                  <div className="space-y-2">
                    {items.map((task) => (
                      <TaskCard key={task.id} task={task} onClick={() => onOpenTask(task.id)} />
                    ))}
                    {items.length === 0 && (
                      <p className="text-muted-foreground text-xs px-1 py-4 text-center">
                        업무 없음
                      </p>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Related RFP */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <FileText className="size-4" /> 관련 RFP 요구사항
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {REQUIREMENTS.slice(0, 3).map((r) => (
              <div key={r.id} className="rounded-md border border-border p-3">
                <Badge variant="outline" className="mb-1">{r.category}</Badge>
                <p className="text-foreground text-sm">{r.text}</p>
              </div>
            ))}
          </CardContent>
        </Card>

        {/* AI helper */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="size-4" /> AI 업무 도우미
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-3">
              {AI_TASK_HELPER.map((line) => (
                <li key={line} className="flex items-start gap-2">
                  <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" />
                  <span className="text-foreground text-sm">{line}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>

        {/* Recent feedback */}
        <Card>
          <CardHeader>
            <CardTitle>최근 PM 피드백</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {STAFF_FEEDBACK.map((f) => (
              <div key={f.id} className="rounded-md border border-border p-3">
                <div className="flex items-center justify-between mb-1">
                  <span className="text-foreground text-sm">{f.author}</span>
                  <span className="text-muted-foreground text-xs">{f.date}</span>
                </div>
                <p className="text-muted-foreground text-sm">{f.text}</p>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function TaskCard({ task, onClick }: { task: Task; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className="w-full rounded-md border border-border bg-card p-3 text-left transition-shadow hover:shadow-sm"
    >
      <div className="flex items-start justify-between gap-2">
        <span className="text-foreground text-sm">{task.title}</span>
        <Badge variant={priorityVariant(task.priority)}>{task.priority}</Badge>
      </div>
      <div className="mt-2 flex items-center justify-between text-muted-foreground text-xs">
        <span>{task.relatedReq}</span>
        <span>~{task.due}</span>
      </div>
    </button>
  );
}

interface KpiCardProps {
  icon: React.ReactNode;
  label: string;
  value: string;
}

function KpiCard({ icon, label, value }: KpiCardProps) {
  return (
    <Card>
      <CardContent className="pt-6">
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm">{label}</span>
          <span className="text-muted-foreground">{icon}</span>
        </div>
        <div className="mt-2 text-foreground text-2xl">{value}</div>
      </CardContent>
    </Card>
  );
}
