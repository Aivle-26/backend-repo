import { useMemo, useState } from "react";
import { MessagesSquare, Send } from "lucide-react";
import { toast } from "sonner";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Textarea } from "@/app/components/ui/textarea";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
import { Separator } from "@/app/components/ui/separator";
import { projectRepository } from "@/app/api/projectRepository";

interface Comment {
  id: string;
  author: string;
  date: string;
  text: string;
  mine?: boolean;
}

const INITIAL: Comment[] = [
  { id: "cm1", author: "PM 정하늘", date: "2026-06-30 09:12", text: "환경영향 완화 단락의 근거 데이터를 RFP 3.2 기준으로 보강해 주세요." },
  { id: "cm2", author: "나", date: "2026-06-30 10:05", text: "대기질 완화 단락 초안 작성 중입니다. 오늘 중 공유드릴게요.", mine: true },
  { id: "cm3", author: "이서연", date: "2026-06-30 11:20", text: "폐수 처리 기준표는 제가 정리한 자료 참고하시면 됩니다." },
];

export function StaffComments() {
  const [comments, setComments] = useState<Comment[]>(INITIAL);
  const [draft, setDraft] = useState("");

  const sorted = useMemo(() => comments, [comments]);

  const add = async () => {
    if (!draft.trim()) return;
    await projectRepository.addComment({ text: draft });
    const now = new Date().toLocaleString("ko-KR", {
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    });
    setComments((prev) => [
      ...prev,
      { id: `cm${prev.length + 1}`, author: "나", date: `2026-${now}`, text: draft, mine: true },
    ]);
    setDraft("");
    toast.success("댓글을 등록했습니다.");
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <MessagesSquare className="size-4" /> 댓글
          </CardTitle>
          <CardDescription>
            업무 관련 논의를 남기고 팀과 소통하세요.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-4">
            {sorted.map((c) => (
              <div key={c.id} className="flex gap-3">
                <Avatar className="size-8">
                  <AvatarFallback>{c.author.slice(-1)}</AvatarFallback>
                </Avatar>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-foreground text-sm">{c.author}</span>
                    <span className="text-muted-foreground text-xs">{c.date}</span>
                  </div>
                  <div
                    className={
                      c.mine
                        ? "mt-1 inline-block rounded-lg bg-accent px-3 py-2 text-sm text-foreground"
                        : "mt-1 inline-block rounded-lg bg-muted/60 px-3 py-2 text-sm text-foreground"
                    }
                  >
                    {c.text}
                  </div>
                </div>
              </div>
            ))}
          </div>

          <Separator />

          <div className="space-y-2">
            <Textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if ((e.metaKey || e.ctrlKey) && e.key === "Enter") add();
              }}
              placeholder="댓글을 입력하세요. (Ctrl/Cmd + Enter 로 등록)"
              rows={3}
            />
            <div className="flex justify-end">
              <Button onClick={add}>
                <Send className="size-4" /> 댓글 등록
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
