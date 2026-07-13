import { useMemo, useState } from "react";
import {
  Sparkles,
  UploadCloud,
  Download,
  Bot,
  Search,
  SlidersHorizontal,
  RotateCcw,
  Send,
  Paperclip,
  X,
  ClipboardList,
  FileStack,
  MessagesSquare,
  Star,
} from "lucide-react";
import { toast } from "sonner";
import { Card, CardContent } from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Badge } from "@/app/components/ui/badge";
import { Checkbox } from "@/app/components/ui/checkbox";
import { Input } from "@/app/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/app/components/ui/table";
import { cn } from "@/app/components/ui/utils";
import { FileTypeIcon } from "@/app/components/common/FileTypeIcon";
import { projectRepository } from "@/app/api/projectRepository";
import type { ChatMessage, GenStatus } from "@/app/data/demoData";

function statusBadge(status: GenStatus) {
  if (status === "생성 완료")
    return "bg-emerald-50 text-emerald-700 border-emerald-200";
  if (status === "오늘 업데이트")
    return "bg-blue-50 text-blue-700 border-blue-200";
  return "bg-amber-50 text-amber-700 border-amber-200";
}

export function PmDocuments() {
  const {
    aiFiles,
    libraryFiles,
    planningAgents,
    reportAgents,
    artifacts,
    stats,
    chatHistory,
  } = projectRepository.getPmDocuments();

  // AI 생성 파일 선택
  const [aiSelected, setAiSelected] = useState<Set<string>>(new Set());
  // 자료실 연동 파일 선택 + 검색
  const [libSelected, setLibSelected] = useState<Set<string>>(
    new Set(["lf1", "lf2", "lf3"]),
  );
  const [libQuery, setLibQuery] = useState("");

  const [messages, setMessages] = useState<ChatMessage[]>(chatHistory);
  const [draft, setDraft] = useState("");

  const filteredLib = useMemo(() => {
    const q = libQuery.trim().toLowerCase();
    if (!q) return libraryFiles;
    return libraryFiles.filter(
      (f) =>
        f.name.toLowerCase().includes(q) ||
        f.category.toLowerCase().includes(q),
    );
  }, [libQuery, libraryFiles]);

  const selectedLibNames = libraryFiles
    .filter((f) => libSelected.has(f.id))
    .map((f) => f.name);

  const toggle = (set: Set<string>, id: string, setter: (s: Set<string>) => void) => {
    const next = new Set(set);
    next.has(id) ? next.delete(id) : next.add(id);
    setter(next);
  };

  const now = () =>
    new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });

  const sendMessage = (text: string) => {
    if (!text.trim()) return;
    const userMsg: ChatMessage = {
      id: `u${Date.now()}`,
      sender: "user",
      text,
      time: now(),
    };
    const aiMsg: ChatMessage = {
      id: `a${Date.now() + 1}`,
      sender: "ai",
      text: "요청을 반영해 문서를 분석하고 산출물을 정리했습니다.",
      bullets: ["요청 내용 파싱 완료", "관련 자료실 파일 참조", "산출물 초안 갱신"],
      time: now(),
    };
    setMessages((prev) => [...prev, userMsg, aiMsg]);
    setDraft("");
  };

  const runAgent = (name: string) => {
    toast.success(`'${name}' 에이전트를 실행했습니다.`);
    setMessages((prev) => [
      ...prev,
      {
        id: `ag${Date.now()}`,
        sender: "ai",
        text: `'${name}' 작업을 완료했습니다. 생성된 산출물을 확인하세요.`,
        time: now(),
      },
    ]);
  };

  return (
    <div className="grid grid-cols-1 xl:grid-cols-12 gap-6">
      {/* ============ 좌측: 파일 목록 ============ */}
      <div className="xl:col-span-5 space-y-6">
        {/* AI 생성 파일 */}
        <Card>
          <CardContent className="pt-5">
            <div className="flex items-start justify-between gap-3 mb-4">
              <div className="flex items-center gap-2">
                <span className="flex size-8 items-center justify-center rounded-md bg-blue-50 text-blue-600">
                  <Sparkles className="size-4" />
                </span>
                <div className="leading-tight">
                  <div className="text-foreground">AI 생성 파일</div>
                  <div className="text-muted-foreground text-xs">
                    AI가 생성한 문서 및 산출물입니다.
                  </div>
                </div>
              </div>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => toast.success("선택 파일을 자료실로 업로드했습니다.")}
                >
                  <UploadCloud className="size-4" /> 자료실로 업로드
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    if (aiSelected.size === 0)
                      return toast.error("다운로드할 파일을 선택하세요.");
                    toast.success(`${aiSelected.size}개 파일을 다운로드합니다.`);
                  }}
                >
                  <Download className="size-4" /> 선택 다운로드
                </Button>
              </div>
            </div>

            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-8"></TableHead>
                  <TableHead>파일명</TableHead>
                  <TableHead className="w-24">생성일시</TableHead>
                  <TableHead className="w-24">상태</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {aiFiles.map((f) => (
                  <TableRow
                    key={f.id}
                    className="cursor-pointer"
                    onClick={() => toggle(aiSelected, f.id, setAiSelected)}
                  >
                    <TableCell>
                      <Checkbox
                        checked={aiSelected.has(f.id)}
                        onCheckedChange={() => toggle(aiSelected, f.id, setAiSelected)}
                        onClick={(e) => e.stopPropagation()}
                      />
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <FileTypeIcon kind={f.kind} />
                        <span className="text-foreground text-sm">{f.name}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground text-xs">
                      {f.createdAt}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="outline"
                        className={cn("font-normal", statusBadge(f.status))}
                      >
                        {f.status}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        {/* 자료실 연동 파일 */}
        <Card>
          <CardContent className="pt-5">
            <div className="flex items-start justify-between gap-3 mb-3">
              <div className="flex items-center gap-2">
                <span className="flex size-8 items-center justify-center rounded-md bg-indigo-50 text-indigo-600">
                  <FileStack className="size-4" />
                </span>
                <div className="leading-tight">
                  <div className="text-foreground">자료실 연동 파일</div>
                  <div className="text-muted-foreground text-xs">
                    자료실 파일을 선택하여 챗봇으로 가져올 수 있습니다.
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <div className="relative">
                  <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={libQuery}
                    onChange={(e) => setLibQuery(e.target.value)}
                    placeholder="파일명 검색"
                    className="h-8 w-40 pl-8"
                  />
                </div>
                <Button variant="outline" size="icon" className="size-8" aria-label="필터">
                  <SlidersHorizontal className="size-4" />
                </Button>
              </div>
            </div>

            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-8"></TableHead>
                  <TableHead>파일명</TableHead>
                  <TableHead className="w-20">카테고리</TableHead>
                  <TableHead className="w-32">수정일시</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredLib.map((f) => (
                  <TableRow
                    key={f.id}
                    data-state={libSelected.has(f.id) ? "selected" : undefined}
                    className="cursor-pointer"
                    onClick={() => toggle(libSelected, f.id, setLibSelected)}
                  >
                    <TableCell>
                      <Checkbox
                        checked={libSelected.has(f.id)}
                        onCheckedChange={() => toggle(libSelected, f.id, setLibSelected)}
                        onClick={(e) => e.stopPropagation()}
                      />
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <FileTypeIcon kind={f.kind} />
                        <span className="text-foreground text-sm">{f.name}</span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary" className="font-normal">
                        {f.category}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground text-xs">
                      {f.updatedAt}
                    </TableCell>
                  </TableRow>
                ))}
                {filteredLib.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center text-muted-foreground py-6">
                      검색 결과가 없습니다.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>

            <div className="mt-4 flex items-center justify-between border-t border-border pt-4">
              <span className="text-primary text-sm">{libSelected.size}개 선택됨</span>
              <Button
                onClick={() => {
                  if (libSelected.size === 0)
                    return toast.error("가져올 파일을 선택하세요.");
                  toast.success(`${libSelected.size}개 파일을 챗봇으로 가져왔습니다.`);
                }}
              >
                <Bot className="size-4" /> 챗봇으로 가져오기
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* ============ 우측: AI 문서 관리 챗봇 ============ */}
      <div className="xl:col-span-7">
        <Card className="flex h-full flex-col">
          <CardContent className="flex flex-1 flex-col pt-5">
            {/* 헤더 */}
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <span className="flex size-8 items-center justify-center rounded-md bg-blue-600 text-white">
                  <Bot className="size-4" />
                </span>
                <span className="text-foreground">AI 문서 관리 챗봇</span>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  setMessages([]);
                  toast("대화를 초기화했습니다.");
                }}
              >
                <RotateCcw className="size-4" /> 대화 초기화
              </Button>
            </div>

            {/* 에이전트 버튼 */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
              <AgentGroup
                title="A. Planning Agent"
                titleClass="text-blue-600"
                agents={planningAgents}
                onRun={runAgent}
              />
              <AgentGroup
                title="B. Report Agent"
                titleClass="text-emerald-600"
                agents={reportAgents}
                onRun={runAgent}
              />
            </div>

            {/* 선택 파일 바 */}
            {selectedLibNames.length > 0 && (
              <div className="mb-4 flex items-start gap-2 rounded-lg border border-border bg-muted/40 px-3 py-2">
                <Paperclip className="mt-0.5 size-4 text-muted-foreground" />
                <div className="flex-1 text-sm">
                  <span className="text-muted-foreground">선택 파일: </span>
                  <span className="text-foreground">
                    {selectedLibNames.join(", ")}
                  </span>
                </div>
                <button
                  onClick={() => setLibSelected(new Set())}
                  className="text-muted-foreground hover:text-foreground"
                  aria-label="선택 해제"
                >
                  <X className="size-4" />
                </button>
              </div>
            )}

            {/* 대화 + 사이드 통계 */}
            <div className="grid flex-1 grid-cols-1 lg:grid-cols-[minmax(0,1fr)_180px] gap-4">
              <div className="space-y-4">
                {/* 메시지 */}
                <div className="space-y-4">
                  {messages.map((m) => (
                    <ChatBubble key={m.id} message={m} />
                  ))}
                  {messages.length === 0 && (
                    <p className="py-8 text-center text-muted-foreground text-sm">
                      대화가 비어 있습니다. 요청을 입력해 보세요.
                    </p>
                  )}
                </div>

                {/* 생성된 산출물 */}
                <div>
                  <div className="mb-2 text-muted-foreground text-xs">· 생성된 산출물</div>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                    {artifacts.map((a) => (
                      <button
                        key={a.id}
                        onClick={() => toast(`'${a.title}' 산출물을 다운로드합니다.`)}
                        className="group flex flex-col items-start gap-1 rounded-lg border border-border p-2 text-left transition-colors hover:bg-muted/60"
                      >
                        <FileTypeIcon kind={a.kind} />
                        <span className="text-foreground text-xs leading-tight">
                          {a.title}
                        </span>
                        <span className="text-muted-foreground text-[11px]">{a.meta}</span>
                        <Download className="size-3 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* 이번 분석 결과 */}
              <div className="space-y-2">
                <div className="text-foreground text-sm">· 이번 분석 결과</div>
                {stats.map((s, i) => (
                  <div
                    key={s.id}
                    className="flex items-center gap-2 rounded-lg border border-border p-2.5"
                  >
                    <span className="flex size-7 items-center justify-center rounded-md bg-blue-50 text-blue-600">
                      {[<ClipboardList key="a" className="size-4" />, <FileStack key="b" className="size-4" />, <MessagesSquare key="c" className="size-4" />, <Star key="d" className="size-4" />][i % 4]}
                    </span>
                    <div className="leading-tight">
                      <div className="text-muted-foreground text-[11px]">{s.label}</div>
                      <div className="text-foreground text-sm">{s.value}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* 빠른 액션 */}
            <div className="mt-4 flex flex-wrap gap-2">
              {["회의록 요약", "주간 보고서 생성", "결정사항 로그 생성", "RAG 질의"].map(
                (q) => (
                  <Button
                    key={q}
                    variant="outline"
                    size="sm"
                    className="rounded-full"
                    onClick={() => runAgent(q)}
                  >
                    {q}
                  </Button>
                ),
              )}
            </div>

            {/* 입력창 */}
            <form
              onSubmit={(e) => {
                e.preventDefault();
                sendMessage(draft);
              }}
              className="mt-3 flex items-center gap-2 rounded-lg border border-border px-3 py-2"
            >
              <Paperclip className="size-4 text-muted-foreground" />
              <input
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                placeholder="자료실 파일을 선택해 요청을 입력하세요"
                className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
              />
              <Button type="submit" size="icon" className="size-8" aria-label="전송">
                <Send className="size-4" />
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function AgentGroup({
  title,
  titleClass,
  agents,
  onRun,
}: {
  title: string;
  titleClass: string;
  agents: string[];
  onRun: (name: string) => void;
}) {
  return (
    <div className="rounded-lg border border-border p-3">
      <div className={cn("mb-2 text-sm", titleClass)}>{title}</div>
      <div className="grid grid-cols-3 gap-2">
        {agents.map((a) => (
          <button
            key={a}
            onClick={() => onRun(a)}
            className="flex flex-col items-center gap-1 rounded-md border border-border px-1 py-2 text-center transition-colors hover:bg-muted/60"
          >
            <span className="text-muted-foreground text-[11px] leading-tight">{a}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

function ChatBubble({ message }: { message: ChatMessage }) {
  const isUser = message.sender === "user";
  return (
    <div className={cn("flex gap-2", isUser ? "flex-row-reverse" : "")}>
      <span
        className={cn(
          "flex size-8 shrink-0 items-center justify-center rounded-full",
          isUser ? "bg-muted text-foreground" : "bg-blue-600 text-white",
        )}
      >
        {isUser ? <span className="text-xs">나</span> : <Bot className="size-4" />}
      </span>
      <div className={cn("max-w-[80%]", isUser ? "text-right" : "")}>
        <div
          className={cn(
            "inline-block rounded-lg px-3 py-2 text-left text-sm",
            isUser ? "bg-blue-50 text-foreground" : "bg-muted/60 text-foreground",
          )}
        >
          <p>{message.text}</p>
          {message.bullets && (
            <ul className="mt-2 space-y-1">
              {message.bullets.map((b) => (
                <li key={b} className="flex items-start gap-2">
                  <span className="mt-1.5 size-1 shrink-0 rounded-full bg-blue-500" />
                  <span>{b}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="mt-1 text-muted-foreground text-[11px]">{message.time}</div>
      </div>
    </div>
  );
}
