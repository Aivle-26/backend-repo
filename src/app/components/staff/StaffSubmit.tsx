import { useState } from "react";
import { Send, Paperclip, Link2, CheckCircle2, Clock } from "lucide-react";
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import { cn } from "@/app/components/ui/utils";
import { projectRepository } from "@/app/api/projectRepository";
import type { SubmittableTask } from "@/app/data/demoData";

export function StaffSubmit() {
  const { tasks } = projectRepository.getStaffSubmit();
  const [items, setItems] = useState<SubmittableTask[]>(tasks);
  const firstOpen = tasks.find((t) => t.status === "작성 중") ?? tasks[0];
  const [taskId, setTaskId] = useState<string>(firstOpen?.id ?? "");
  const [link, setLink] = useState("");
  const [fileName, setFileName] = useState<string>("");

  const selected = items.find((t) => t.id === taskId) ?? null;

  const attach = async () => {
    await projectRepository.attachFile();
    setFileName("산출물_초안.docx");
    toast.success("파일을 첨부했습니다.");
  };

  const submit = async () => {
    if (!selected) return toast.error("제출할 업무를 선택하세요.");
    if (!fileName && !link.trim())
      return toast.error("파일 또는 링크를 첨부하세요.");
    await projectRepository.requestReview({ taskId: selected.id, attachmentUrl: link });
    setItems((prev) =>
      prev.map((t) => (t.id === selected.id ? { ...t, status: "제출 완료" } : t)),
    );
    toast.success(`"${selected.title}" 검토 요청을 제출했습니다.`);
    setLink("");
    setFileName("");
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div className="lg:col-span-2 space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Send className="size-4" /> 산출물 제출
            </CardTitle>
            <CardDescription>
              업무를 선택하고 파일 또는 링크를 첨부하여 검토를 요청하세요.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm text-foreground">제출 대상 업무</label>
              <Select value={taskId} onValueChange={setTaskId}>
                <SelectTrigger>
                  <SelectValue placeholder="업무를 선택하세요" />
                </SelectTrigger>
                <SelectContent>
                  {items.map((t) => (
                    <SelectItem key={t.id} value={t.id}>
                      {t.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {selected && (
              <div className="rounded-md border border-border bg-muted/40 p-3 text-sm">
                <span className="text-muted-foreground">연관 요구사항 · </span>
                <span className="text-foreground">{selected.relatedReq}</span>
                <span className="text-muted-foreground"> · 마감 {selected.due}</span>
              </div>
            )}

            <div
              onClick={attach}
              className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-border bg-muted/40 py-8 text-center hover:bg-muted"
            >
              <Paperclip className="size-6 text-muted-foreground" />
              <span className="text-foreground text-sm">
                {fileName ? `첨부됨: ${fileName}` : "파일을 끌어다 놓거나 클릭하여 첨부"}
              </span>
            </div>

            <div className="flex items-center gap-2">
              <Link2 className="size-4 text-muted-foreground" />
              <Input
                value={link}
                onChange={(e) => setLink(e.target.value)}
                placeholder="링크 첨부 (https://...)"
              />
            </div>

            <Button className="w-full" onClick={submit}>
              검토 요청 제출
            </Button>
          </CardContent>
        </Card>
      </div>

      <div className="lg:col-span-1">
        <Card>
          <CardHeader>
            <CardTitle>제출 현황</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {items.map((t) => {
              const done = t.status === "제출 완료";
              return (
                <div key={t.id} className="rounded-md border border-border p-3">
                  <div className="flex items-start justify-between gap-2">
                    <span className="text-foreground text-sm">{t.title}</span>
                    <Badge
                      variant="outline"
                      className={cn(
                        "font-normal",
                        done
                          ? "bg-emerald-50 text-emerald-700 border-emerald-200"
                          : "bg-amber-50 text-amber-700 border-amber-200",
                      )}
                    >
                      {done ? (
                        <CheckCircle2 className="size-3" />
                      ) : (
                        <Clock className="size-3" />
                      )}
                      {t.status}
                    </Badge>
                  </div>
                  <div className="mt-1 text-muted-foreground text-xs">
                    마감 {t.due}
                  </div>
                </div>
              );
            })}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
