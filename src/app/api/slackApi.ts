/**
 * Slack 연동 백엔드(Spring Boot, 기본 http://localhost:8080) 호출 클라이언트.
 * 백엔드 규격:
 *  - OAuth 시작:  GET /api/auth/slack/login  (브라우저를 이 주소로 이동)
 *  - 콜백 후:     프론트로 /?session=... 리다이렉트
 *  - 내 정보:     GET /api/auth/slack/me?session=
 *  - 데이터:      GET /api/slack/conversations|users|channels/{id}/messages|threads
 * 사용자는 session 파라미터로 식별한다(데모 방식).
 */

const API_BASE: string =
  (import.meta as unknown as { env?: Record<string, string> }).env
    ?.VITE_SLACK_API || "http://localhost:8080";

const SESSION_KEY = "slack-session";

export interface SlackMe {
  ok: boolean;
  teamId: string;
  teamName: string;
  userId: string;
  userName: string;
}

export interface SlackChannel {
  id: string;
  name: string | null;
  is_private: boolean;
  is_im: boolean;
  is_mpim: boolean;
  is_member: boolean;
  num_members: number | null;
}

export interface SlackUserProfile {
  display_name?: string | null;
  real_name?: string | null;
  image_48?: string | null;
  image_72?: string | null;
}

export interface SlackUser {
  id: string;
  name: string;
  deleted: boolean;
  is_bot: boolean;
  profile: SlackUserProfile | null;
}

export interface SlackFile {
  id: string;
  name: string;
  url_private: string;
}

export interface SlackReaction {
  name: string;
  count: number;
}

export interface SlackMessage {
  ts: string;
  userId: string | null;
  authorName: string;
  authorImage: string;
  text: string;
  files: SlackFile[];
  reactions: SlackReaction[];
  replyCount: number;
  threadTs: string | null;
}

export interface SlackReply {
  ts: string;
  user: string;
  text: string;
}

export function getSession(): string | null {
  try {
    return localStorage.getItem(SESSION_KEY);
  } catch {
    return null;
  }
}

export function setSession(id: string): void {
  try {
    localStorage.setItem(SESSION_KEY, id);
  } catch {
    /* ignore */
  }
}

export function clearSession(): void {
  try {
    localStorage.removeItem(SESSION_KEY);
  } catch {
    /* ignore */
  }
}

/** OAuth 콜백으로 ?session= 이 붙어 돌아오면 저장 후 URL에서 제거. 캡처했으면 true. */
export function captureSessionFromUrl(): boolean {
  if (typeof window === "undefined") return false;
  const params = new URLSearchParams(window.location.search);
  const sid = params.get("session");
  if (!sid) return false;
  setSession(sid);
  const url = new URL(window.location.href);
  url.searchParams.delete("session");
  window.history.replaceState({}, "", url.pathname + url.search + url.hash);
  return true;
}

export function loginUrl(): string {
  return `${API_BASE}/api/auth/slack/login`;
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: { Accept: "application/json" },
  });
  if (!res.ok) {
    throw new Error(`요청 실패 (${res.status})`);
  }
  return (await res.json()) as T;
}

const q = (s: string) => encodeURIComponent(s);

export const slackApi = {
  apiBase: API_BASE,
  loginUrl,
  getSession,
  setSession,
  clearSession,
  captureSessionFromUrl,
  me: (session: string) => get<SlackMe>(`/api/auth/slack/me?session=${q(session)}`),
  conversations: (session: string) =>
    get<SlackChannel[]>(`/api/slack/conversations?session=${q(session)}`),
  users: (session: string) =>
    get<SlackUser[]>(`/api/slack/users?session=${q(session)}`),
  messages: (session: string, channelId: string, limit = 30) =>
    get<SlackMessage[]>(
      `/api/slack/channels/${q(channelId)}/messages?session=${q(session)}&limit=${limit}`,
    ),
  threadReplies: (session: string, channelId: string, threadTs: string) =>
    get<SlackReply[]>(
      `/api/slack/channels/${q(channelId)}/threads/${q(threadTs)}?session=${q(session)}`,
    ),
};
