import { useState } from "react";
import { MessageSquareReply, CheckCircle2 } from "lucide-react";
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
import { Textarea } from "@/app/components/ui/textarea";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { projectRepository } from "@/app/api/projectRepository";

interface FeedbackState {
  id: string;
  author: string;
  date: string;
  text: string;
  resolved: boolean;
  reply?: string;
}

export function StaffFeedback() {
  const { feedback } = projectRepository.getStaffFeedback();
  const [items, setItems] = useState<FeedbackState[]>(
    feedback.map((f) => ({ ...f, resolved: false })),
  );
  const [replyFor, setReplyFor] = useState<string | null>(null);
  const [reply, setReply] = useState("");

  const openCount = items.filter((i) => !i.resolved).length;

  const sendReply = async (id: string) => {
    if (!reply.trim()) return toast.error("회신 내용을 입력하세요.");
    await projectRepository.addComment({ taskId: id, text: reply });
    setItems((prev) =>
      prev.map((i) => (i.id === id ? { ...i, reply, resolved: true } : i)),
    );
    setReply("");
    setReplyFor(null);
    toast.success("피드백에 회신했습니다.");
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <MessageSquareReply className="size-4" /> PM 피드백
            {openCount > 0 && (
              <Badge variant="secondary" className="ml-1">
                미확인 {openCount}
              </Badge>
            )}
          </CardTitle>
          <CardDescription>
            PM이 남긴 피드백을 확인하고 회신하세요.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {items.map((f) => (
            <div key={f.id} className="rounded-lg border border-border p-4">
              <div className="flex items-start gap-3">
                <Avatar className="size-8">
                  <AvatarFallback>{f.author.slice(-1)}</AvatarFallback>
                </Avatar>
                <div className="flex-1">
                  <div className="flex items-center justify-between">
                    <span className="text-foreground text-sm">{f.author}</span>
                    <div className="flex items-center gap-2">
                      <span className="text-muted-foreground text-xs">{f.date}</span>
                      {f.resolved && (
                        <Badge
                          variant="outline"
                          className="bg-emerald-50 text-emerald-700 border-emerald-200 font-normal"
                        >
                          <CheckCircle2 className="size-3" /> 회신 완료
                        </Badge>
                      )}
                    </div>
                  </div>
                  <p className="text-muted-foreground text-sm mt-1">{f.text}</p>

                  {f.reply && (
                    <div className="mt-2 rounded-md border border-border bg-muted/40 p-2 text-sm">
                      <span className="text-muted-foreground text-xs">내 회신 · </span>
                      <span className="text-foreground">{f.reply}</span>
                    </div>
                  )}

                  {replyFor === f.id ? (
                    <div className="mt-3 space-y-2">
                      <Textarea
                        value={reply}
                        onChange={(e) => setReply(e.target.value)}
                        placeholder="회신 내용을 입력하세요."
                        rows={3}
                      />
                      <div className="flex gap-2">
                        <Button size="sm" onClick={() => sendReply(f.id)}>
                          회신 전송
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => {
                            setReplyFor(null);
                            setReply("");
                          }}
                        >
                          취소
                        </Button>
                      </div>
                    </div>
                  ) : (
                    !f.resolved && (
                      <Button
                        size="sm"
                        variant="outline"
                        className="mt-3"
                        onClick={() => {
                          setReplyFor(f.id);
                          setReply("");
                        }}
                      >
                        회신하기
                      </Button>
                    )
                  )}
                </div>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
