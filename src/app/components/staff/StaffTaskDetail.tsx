import { useState } from "react";
import {
  ArrowLeft,
  CalendarClock,
  Flag,
  UserRound,
  Paperclip,
  Link2,
  Sparkles,
  MessageSquare,
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
import { Checkbox } from "@/app/components/ui/checkbox";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import { Separator } from "@/app/components/ui/separator";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import {
  TASK_CHECKLIST,
  AI_TASK_SUMMARY,
  STAFF_FEEDBACK,
} from "@/app/data/mock";

interface StaffTaskDetailProps {
  onBack: () => void;
}

export function StaffTaskDetail({ onBack }: StaffTaskDetailProps) {
  const [checklist, setChecklist] = useState(TASK_CHECKLIST);
  const [comment, setComment] = useState("");
  const [comments, setComments] = useState([
    { id: "cm1", author: "나", text: "대기질 완화 단락 초안 작성 중입니다.", date: "2026-06-30" },
  ]);

  const toggle = (id: string) =>
    setChecklist((prev) =>
      prev.map((c) => (c.id === id ? { ...c, done: !c.done } : c)),
    );

  const addComment = () => {
    if (!comment.trim()) return;
    setComments((prev) => [
      ...prev,
      { id: `cm${prev.length + 1}`, author: "나", text: comment, date: "2026-06-30" },
    ]);
    setComment("");
    toast.success("댓글을 등록했습니다. (mock)");
  };

  return (
    <div className="space-y-4">
      <Button variant="ghost" size="sm" onClick={onBack} className="-ml-2">
        <ArrowLeft className="size-4" /> 업무 보드로 돌아가기
      </Button>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main */}
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <CardHeader>
              <div className="flex items-center gap-2 mb-1">
                <Badge variant="secondary">진행 중</Badge>
                <Badge variant="destructive">높음</Badge>
              </div>
              <CardTitle>3.2 환경 규정 준수 초안 작성</CardTitle>
              <CardDescription>RFP 3.2 환경 규정 준수 항목 관련 산출물</CardDescription>
            </CardHeader>
            <CardContent className="space-y-5">
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                <Meta icon={<CalendarClock className="size-4" />} label="마감일" value="2026-07-04" />
                <Meta icon={<Flag className="size-4" />} label="우선순위" value="높음" />
                <Meta icon={<UserRound className="size-4" />} label="배정자" value="PM 정하늘" />
              </div>

              <div>
                <h3 className="text-foreground mb-2">관련 RFP 요구사항</h3>
                <div className="rounded-md border border-border bg-muted/40 p-3 text-sm text-foreground">
                  폐수 관리에 대한 환경영향 보고서를 작성하고, 배수 처리·대기질
                  완화·폐기물 처리 조건을 충족해야 합니다.
                </div>
              </div>

              <div>
                <h3 className="text-foreground mb-2">업무 설명</h3>
                <p className="text-muted-foreground text-sm">
                  RFP 3.2 환경 규정 준수 항목을 바탕으로 환경영향 완화 방안 초안을
                  작성합니다. 배수 처리, 대기질 완화, 폐기물 처리에 대한 구체적인
                  대응 방안을 포함해야 합니다.
                </p>
              </div>

              <div>
                <h3 className="text-foreground mb-2">체크리스트</h3>
                <div className="space-y-2">
                  {checklist.map((c) => (
                    <label
                      key={c.id}
                      className="flex items-center gap-2 cursor-pointer"
                    >
                      <Checkbox checked={c.done} onCheckedChange={() => toggle(c.id)} />
                      <span
                        className={
                          c.done
                            ? "text-muted-foreground line-through text-sm"
                            : "text-foreground text-sm"
                        }
                      >
                        {c.label}
                      </span>
                    </label>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>

          {/* Submit */}
          <Card>
            <CardHeader>
              <CardTitle>산출물 제출</CardTitle>
              <CardDescription>파일 또는 링크를 첨부하여 검토를 요청하세요.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div
                onClick={() => toast.success("파일이 첨부되었습니다. (mock)")}
                className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border border-dashed border-border bg-muted/40 py-8 text-center hover:bg-muted"
              >
                <Paperclip className="size-6 text-muted-foreground" />
                <span className="text-foreground text-sm">파일을 끌어다 놓거나 클릭하여 첨부</span>
              </div>
              <div className="flex items-center gap-2">
                <Link2 className="size-4 text-muted-foreground" />
                <Input placeholder="링크 첨부 (https://...)" />
              </div>
              <Button
                className="w-full"
                onClick={() => toast.success("검토 요청을 제출했습니다. (mock)")}
              >
                검토 요청 제출
              </Button>
            </CardContent>
          </Card>
        </div>

        {/* Right panel */}
        <div className="lg:col-span-1 space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <MessageSquare className="size-4" /> PM 피드백
              </CardTitle>
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

          <Card>
            <CardHeader>
              <CardTitle>댓글</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {comments.map((c) => (
                <div key={c.id} className="flex gap-2">
                  <Avatar className="size-7">
                    <AvatarFallback>{c.author.slice(0, 1)}</AvatarFallback>
                  </Avatar>
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <span className="text-foreground text-sm">{c.author}</span>
                      <span className="text-muted-foreground text-xs">{c.date}</span>
                    </div>
                    <p className="text-muted-foreground text-sm">{c.text}</p>
                  </div>
                </div>
              ))}
              <Separator />
              <Textarea
                placeholder="댓글을 입력하세요."
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                rows={3}
              />
              <Button variant="outline" size="sm" className="w-full" onClick={addComment}>
                댓글 등록
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Sparkles className="size-4" /> AI 업무 요약
              </CardTitle>
            </CardHeader>
            <CardContent>
              <ul className="space-y-3">
                {AI_TASK_SUMMARY.map((line) => (
                  <li key={line} className="flex items-start gap-2">
                    <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-primary" />
                    <span className="text-foreground text-sm">{line}</span>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

function Meta({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="rounded-md border border-border p-3">
      <div className="flex items-center gap-1.5 text-muted-foreground text-xs mb-1">
        {icon}
        {label}
      </div>
      <div className="text-foreground text-sm">{value}</div>
    </div>
  );
}
