import { useCallback, useEffect, useMemo, useState } from "react";
import {
  RefreshCw,
  LogOut,
  Hash,
  Lock,
  MessageCircle,
  Users,
  Search,
  MessageSquareText,
  Loader2,
  AlertCircle,
  ExternalLink,
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
import { Avatar, AvatarFallback, AvatarImage } from "@/app/components/ui/avatar";
import { cn } from "@/app/components/ui/utils";
import { SlackIcon } from "@/app/components/common/SlackIcon";
import {
  slackApi,
  type SlackChannel,
  type SlackMe,
  type SlackMessage,
} from "@/app/api/slackApi";

function channelLabel(c: SlackChannel): string {
  if (c.is_im) return "다이렉트 메시지";
  if (c.is_mpim) return c.name ? c.name : "그룹 DM";
  return c.name ?? c.id;
}

function ChannelIcon({ c }: { c: SlackChannel }) {
  if (c.is_im) return <MessageCircle className="size-4" />;
  if (c.is_mpim) return <Users className="size-4" />;
  if (c.is_private) return <Lock className="size-4" />;
  return <Hash className="size-4" />;
}

function formatTs(ts: string): string {
  const sec = parseFloat(ts);
  if (isNaN(sec)) return "";
  const d = new Date(sec * 1000);
  return d.toLocaleString("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function SlackIntegration() {
  const [session, setSessionState] = useState<string | null>(() =>
    slackApi.getSession(),
  );
  const [me, setMe] = useState<SlackMe | null>(null);
  const [meLoading, setMeLoading] = useState(false);
  const [channels, setChannels] = useState<SlackChannel[]>([]);
  const [channelsLoading, setChannelsLoading] = useState(false);
  const [selected, setSelected] = useState<SlackChannel | null>(null);
  const [messages, setMessages] = useState<SlackMessage[]>([]);
  const [msgLoading, setMsgLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);

  // 세션이 있으면 내 정보 + 채널 로드
  const loadWorkspace = useCallback(async (sid: string) => {
    setMeLoading(true);
    setError(null);
    try {
      const info = await slackApi.me(sid);
      setMe(info);
      setChannelsLoading(true);
      const list = await slackApi.conversations(sid);
      setChannels(list);
    } catch (e) {
      // 세션 만료/무효 → 연결 화면으로
      slackApi.clearSession();
      setSessionState(null);
      setMe(null);
      setError(
        e instanceof Error
          ? `연동 정보를 불러오지 못했어요. (${e.message}) 백엔드(localhost:8080)가 실행 중인지 확인해 주세요.`
          : "연동 정보를 불러오지 못했어요.",
      );
    } finally {
      setMeLoading(false);
      setChannelsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (session) loadWorkspace(session);
  }, [session, loadWorkspace]);

  const openChannel = useCallback(
    async (c: SlackChannel) => {
      if (!session) return;
      setSelected(c);
      setMsgLoading(true);
      setMessages([]);
      try {
        const list = await slackApi.messages(session, c.id, 30);
        setMessages(list);
      } catch (e) {
        toast.error(
          e instanceof Error ? `메시지 로드 실패: ${e.message}` : "메시지 로드 실패",
        );
      } finally {
        setMsgLoading(false);
      }
    },
    [session],
  );

  const filteredChannels = useMemo(() => {
    const term = query.trim().toLowerCase();
    if (!term) return channels;
    return channels.filter((c) =>
      channelLabel(c).toLowerCase().includes(term),
    );
  }, [channels, query]);

  const connect = () => {
    window.location.href = slackApi.loginUrl();
  };

  const disconnect = () => {
    slackApi.clearSession();
    setSessionState(null);
    setMe(null);
    setChannels([]);
    setSelected(null);
    setMessages([]);
    toast("Slack 연동을 해제했어요.");
  };

  // ============ 연결 전 화면 ============
  if (!session) {
    return (
      <div className="mx-auto max-w-xl py-6">
        <Card>
          <CardContent className="flex flex-col items-center gap-4 py-12 text-center">
            <span className="flex size-16 items-center justify-center rounded-2xl bg-muted text-3xl">
              <SlackIcon />
            </span>
            <div>
              <h2 className="text-foreground text-lg">Slack 워크스페이스 연동</h2>
              <p className="text-muted-foreground text-sm mt-1">
                Slack을 연동하면 채널·대화·사용자 데이터를 이 화면에서 함께 볼 수 있어요.
              </p>
            </div>
            <Button size="lg" onClick={connect} className="mt-2">
              <SlackIcon className="text-base" /> Slack 연동하기
            </Button>
            {error && (
              <div className="mt-2 flex items-start gap-2 rounded-lg bg-red-50 px-3 py-2 text-left text-sm text-red-700">
                <AlertCircle className="mt-0.5 size-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}
            <p className="text-muted-foreground text-xs mt-2">
              연동 백엔드({slackApi.apiBase})가 실행 중이어야 합니다.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  // ============ 연결 후 화면 ============
  return (
    <div className="space-y-4">
      {/* 워크스페이스 헤더 */}
      <Card>
        <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
          <div className="flex items-center gap-3">
            <span className="flex size-10 items-center justify-center rounded-xl bg-muted text-xl">
              <SlackIcon />
            </span>
            <div className="leading-tight">
              <div className="text-foreground flex items-center gap-2">
                {meLoading ? "불러오는 중…" : me?.teamName || "Slack 워크스페이스"}
                <Badge
                  variant="outline"
                  className="border-emerald-200 bg-emerald-50 font-normal text-emerald-700"
                >
                  연동됨
                </Badge>
              </div>
              <div className="text-muted-foreground text-xs">
                {me?.userName ? `${me.userName} 님으로 연결됨` : ""}
              </div>
            </div>
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => session && loadWorkspace(session)}
            >
              <RefreshCw className="size-4" /> 새로고침
            </Button>
            <Button variant="outline" size="sm" onClick={disconnect}>
              <LogOut className="size-4" /> 연동 해제
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 채널 + 메시지 */}
      <div className="grid grid-cols-1 lg:grid-cols-[300px_minmax(0,1fr)] gap-4">
        {/* 채널 목록 */}
        <Card className="lg:h-[calc(100vh-13rem)] flex flex-col">
          <CardHeader className="pb-3">
            <CardTitle className="text-sm">채널 · 대화</CardTitle>
            <div className="relative mt-2">
              <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="채널 검색"
                className="h-9 pl-8"
              />
            </div>
          </CardHeader>
          <CardContent className="flex-1 space-y-0.5 overflow-y-auto">
            {channelsLoading && (
              <div className="flex items-center gap-2 px-2 py-6 text-muted-foreground text-sm">
                <Loader2 className="size-4 animate-spin" /> 채널 불러오는 중…
              </div>
            )}
            {!channelsLoading &&
              filteredChannels.map((c) => (
                <button
                  key={c.id}
                  onClick={() => openChannel(c)}
                  className={cn(
                    "flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-sm transition-colors",
                    selected?.id === c.id
                      ? "bg-accent text-accent-foreground"
                      : "text-muted-foreground hover:bg-muted/60 hover:text-foreground",
                  )}
                >
                  <ChannelIcon c={c} />
                  <span className="flex-1 truncate">{channelLabel(c)}</span>
                  {c.num_members != null && !c.is_im && (
                    <span className="text-muted-foreground text-xs">
                      {c.num_members}
                    </span>
                  )}
                </button>
              ))}
            {!channelsLoading && filteredChannels.length === 0 && (
              <p className="px-2 py-6 text-center text-muted-foreground text-sm">
                채널이 없습니다.
              </p>
            )}
          </CardContent>
        </Card>

        {/* 메시지 */}
        <Card className="lg:h-[calc(100vh-13rem)] flex flex-col">
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-sm">
              {selected ? (
                <>
                  <ChannelIcon c={selected} />
                  {channelLabel(selected)}
                </>
              ) : (
                "메시지"
              )}
            </CardTitle>
            {selected && (
              <CardDescription>최근 메시지 {messages.length}건</CardDescription>
            )}
          </CardHeader>
          <CardContent className="flex-1 overflow-y-auto">
            {!selected && (
              <div className="flex h-full flex-col items-center justify-center gap-2 py-12 text-center text-muted-foreground">
                <MessageSquareText className="size-8" />
                <p className="text-sm">왼쪽에서 채널을 선택하면 대화가 표시됩니다.</p>
              </div>
            )}
            {selected && msgLoading && (
              <div className="flex items-center gap-2 py-6 text-muted-foreground text-sm">
                <Loader2 className="size-4 animate-spin" /> 메시지 불러오는 중…
              </div>
            )}
            {selected && !msgLoading && messages.length === 0 && (
              <p className="py-8 text-center text-muted-foreground text-sm">
                메시지가 없습니다.
              </p>
            )}
            {selected && !msgLoading && (
              <div className="space-y-4">
                {messages.map((m) => (
                  <div key={m.ts} className="flex gap-3">
                    <Avatar className="size-9 rounded-md">
                      {m.authorImage ? (
                        <AvatarImage src={m.authorImage} alt={m.authorName} />
                      ) : null}
                      <AvatarFallback className="rounded-md text-xs">
                        {(m.authorName || "?").slice(0, 1)}
                      </AvatarFallback>
                    </Avatar>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-baseline gap-2">
                        <span className="text-foreground text-sm">
                          {m.authorName || "알 수 없음"}
                        </span>
                        <span className="text-muted-foreground text-xs">
                          {formatTs(m.ts)}
                        </span>
                      </div>
                      <p className="whitespace-pre-wrap break-words text-foreground text-sm">
                        {m.text}
                      </p>

                      {m.files.length > 0 && (
                        <div className="mt-1.5 flex flex-wrap gap-1.5">
                          {m.files.map((f) => (
                            <span
                              key={f.id}
                              className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-xs text-muted-foreground"
                            >
                              <ExternalLink className="size-3" /> {f.name}
                            </span>
                          ))}
                        </div>
                      )}

                      {(m.reactions.length > 0 || m.replyCount > 0) && (
                        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                          {m.reactions.map((r) => (
                            <span
                              key={r.name}
                              className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground"
                            >
                              :{r.name}: {r.count}
                            </span>
                          ))}
                          {m.replyCount > 0 && (
                            <span className="inline-flex items-center gap-1 text-xs text-blue-600">
                              <MessageCircle className="size-3" /> 답글 {m.replyCount}
                            </span>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
