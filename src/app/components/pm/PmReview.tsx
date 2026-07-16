import { useMemo, useState } from "react";
import {
  ClipboardCheck,
  Check,
  X,
  Search,
  Paperclip,
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
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/app/components/ui/table";
import { cn } from "@/app/components/ui/utils";
import { projectRepository } from "@/app/api/projectRepository";
import type {
  ProjectSummary,
  ReviewState,
  ReviewSubmission,
} from "@/app/data/demoData";

type Filter = "전체" | ReviewState;

function stateClass(s: ReviewState) {
  if (s === "승인") return "bg-emerald-50 text-emerald-700 border-emerald-200";
  if (s === "반려") return "bg-red-50 text-red-700 border-red-200";
  return "bg-amber-50 text-amber-700 border-amber-200";
}

export function PmReview({ project }: { project: ProjectSummary }) {
  const base = projectRepository.getPmReview();
  const feedback = base.feedback;
  const submissions =
    project.status === "진행중" ? base.submissions : base.submissions.slice(0, 1);
  const [items, setItems] = useState<ReviewSubmission[]>(submissions);
  const [filter, setFilter] = useState<Filter>("전체");
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<string>(submissions[0]?.id ?? "");
  const [memo, setMemo] = useState("");

  const counts = useMemo(
    () => ({
      전체: items.length,
      "검토 대기": items.filter((i) => i.state === "검토 대기").length,
      승인: items.filter((i) => i.state === "승인").length,
      반려: items.filter((i) => i.state === "반려").length,
    }),
    [items],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return items.filter((i) => {
      const matchState = filter === "전체" || i.state === filter;
      const matchQuery =
        !q ||
        i.title.toLowerCase().includes(q) ||
        i.author.toLowerCase().includes(q) ||
        i.relatedReq.toLowerCase().includes(q);
      return matchState && matchQuery;
    });
  }, [items, filter, query]);

  const selected = items.find((i) => i.id === selectedId) ?? null;

  const setState = async (id: string, state: ReviewState) => {
    await projectRepository.requestReview({ taskId: id });
    setItems((prev) => prev.map((i) => (i.id === id ? { ...i, state } : i)));
    toast.success(
      state === "승인"
        ? "산출물을 승인 처리했습니다."
        : "반려하고 수정 요청을 보냈습니다.",
    );
  };

  const sendMemo = async () => {
    if (!selected) return toast.error("검토할 산출물을 선택하세요.");
    if (!memo.trim()) return toast.error("피드백 내용을 입력하세요.");
    await projectRepository.addComment({ taskId: selected.id, text: memo });
    toast.success(`"${selected.title}"에 피드백을 남겼습니다.`);
    setMemo("");
  };

  return (
    <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
      <div className="xl:col-span-2 space-y-6">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <ClipboardCheck className="size-4" /> 산출물 검토
            </CardTitle>
            <CardDescription>
              제출된 산출물을 확인하고 승인 또는 반려할 수 있습니다.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              {(["전체", "검토 대기", "승인", "반려"] as Filter[]).map((f) => (
                <button
                  key={f}
                  onClick={() => setFilter(f)}
                  className={cn(
                    "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-sm transition-colors",
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
                  placeholder="제목·담당자 검색"
                  className="h-8 w-44 pl-8"
                />
              </div>
            </div>

            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>산출물</TableHead>
                  <TableHead className="w-20">담당자</TableHead>
                  <TableHead className="w-24">상태</TableHead>
                  <TableHead className="w-32 text-right">검토</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filtered.map((i) => (
                  <TableRow
                    key={i.id}
                    data-state={selectedId === i.id ? "selected" : undefined}
                    className="cursor-pointer"
                    onClick={() => setSelectedId(i.id)}
                  >
                    <TableCell>
                      <div className="text-foreground text-sm">{i.title}</div>
                      <div className="flex items-center gap-1 text-muted-foreground text-xs">
                        <Paperclip className="size-3" />
                        {i.attachment}
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground text-sm">{i.author}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={cn("font-normal", stateClass(i.state))}>
                        {i.state}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-1">
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-7 px-2 text-emerald-700"
                          onClick={(e) => {
                            e.stopPropagation();
                            setState(i.id, "승인");
                          }}
                        >
                          <Check className="size-3.5" /> 승인
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-7 px-2 text-red-700"
                          onClick={(e) => {
                            e.stopPropagation();
                            setState(i.id, "반려");
                          }}
                        >
                          <X className="size-3.5" /> 반려
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
                {filtered.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                      해당하는 산출물이 없습니다.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>

      <div className="xl:col-span-1 space-y-6">
        <Card className="sticky top-6">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <MessageSquare className="size-4" /> 검토 피드백
            </CardTitle>
            <CardDescription>선택한 산출물에 피드백을 남깁니다.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="rounded-md border border-border bg-muted/40 p-3 text-sm">
              {selected ? (
                <>
                  <div className="text-foreground">{selected.title}</div>
                  <div className="text-muted-foreground text-xs mt-0.5">
                    {selected.author} · {selected.submittedAt}
                  </div>
                </>
              ) : (
                <span className="text-muted-foreground">산출물을 선택하세요.</span>
              )}
            </div>
            <Textarea
              value={memo}
              onChange={(e) => setMemo(e.target.value)}
              placeholder="피드백 내용을 입력하세요."
              rows={4}
            />
            <Button className="w-full" onClick={sendMemo}>
              피드백 전송
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>최근 검토 피드백</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {feedback.map((f) => (
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
