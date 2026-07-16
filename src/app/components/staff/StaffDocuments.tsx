import { useMemo, useState } from "react";
import {
  FolderInput,
  Download,
  Search,
  SlidersHorizontal,
  Figma,
  Plus,
  History,
  MoreVertical,
  Bot,
  MessageSquare,
  Heart,
  MapPin,
  MessageCircle,
  Image as ImageIcon,
  Grid3x3,
  LayoutGrid,
  Bell,
  Home,
  FileText,
  Tag,
  Star,
} from "lucide-react";
import { toast } from "sonner";
import { Card, CardContent } from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { Badge } from "@/app/components/ui/badge";
import { Checkbox } from "@/app/components/ui/checkbox";
import { Input } from "@/app/components/ui/input";
import { Avatar, AvatarFallback } from "@/app/components/ui/avatar";
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
import type { ReviewStatus } from "@/app/data/demoData";

const ICON_SET = [
  Heart, MapPin, MessageSquare, ImageIcon, Grid3x3, LayoutGrid,
  Bell, Home, MessageCircle, FileText, Tag, Star,
];

function reviewBadge(s: ReviewStatus) {
  const map: Record<ReviewStatus, string> = {
    "수정 중": "bg-orange-50 text-orange-700 border-orange-200",
    "검토 요청": "bg-orange-50 text-orange-700 border-orange-200",
    "승인 대기": "bg-emerald-50 text-emerald-700 border-emerald-200",
    "승인 완료": "bg-emerald-50 text-emerald-700 border-emerald-200",
  };
  return map[s];
}

export function StaffDocuments() {
  const { sharedDocs, myDocs, reviewActivity } =
    projectRepository.getStaffDocuments();

  const [sharedSel, setSharedSel] = useState<Set<string>>(new Set());
  const [mySel, setMySel] = useState<Set<string>>(new Set());
  const [myQuery, setMyQuery] = useState("");

  const filteredMy = useMemo(() => {
    const q = myQuery.trim().toLowerCase();
    if (!q) return myDocs;
    return myDocs.filter(
      (d) => d.name.toLowerCase().includes(q) || d.category.toLowerCase().includes(q),
    );
  }, [myQuery, myDocs]);

  const toggle = (set: Set<string>, id: string, setter: (s: Set<string>) => void) => {
    const next = new Set(set);
    next.has(id) ? next.delete(id) : next.add(id);
    setter(next);
  };

  return (
    <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
      {/* 동일 부서 팀원들과의 문서 공유 */}
      <Card>
        <CardContent className="pt-5">
          <div className="flex items-center justify-between mb-4">
            <span className="text-foreground">[동일 부서 팀원들과의 문서 공유]</span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={() => toast("자료실로 이동합니다.")}>
                <FolderInput className="size-4" /> 자료실로 이동
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  if (sharedSel.size === 0) return toast.error("파일을 선택하세요.");
                  toast.success(`${sharedSel.size}개 파일 다운로드`);
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
                <TableHead className="w-24">소유자</TableHead>
                <TableHead className="w-24">공유일</TableHead>
                <TableHead className="w-8"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sharedDocs.map((d) => (
                <TableRow
                  key={d.id}
                  className="cursor-pointer"
                  onClick={() => toggle(sharedSel, d.id, setSharedSel)}
                >
                  <TableCell>
                    <Checkbox
                      checked={sharedSel.has(d.id)}
                      onCheckedChange={() => toggle(sharedSel, d.id, setSharedSel)}
                      onClick={(e) => e.stopPropagation()}
                    />
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <FileTypeIcon kind={d.kind} />
                      <span className="text-foreground text-sm">{d.name}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1.5">
                      <Avatar className="size-6">
                        <AvatarFallback className="text-[10px]">{d.owner.slice(0, 1)}</AvatarFallback>
                      </Avatar>
                      <span className="text-muted-foreground text-sm">{d.owner}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-muted-foreground text-xs">{d.sharedAt}</TableCell>
                  <TableCell>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        toast(`'${d.name}' 다운로드`);
                      }}
                      className="text-muted-foreground hover:text-foreground"
                      aria-label="다운로드"
                    >
                      <Download className="size-4" />
                    </button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {/* 디자인 시스템 에셋 라이브러리 */}
      <Card>
        <CardContent className="pt-5">
          <div className="flex items-start justify-between gap-3 mb-4">
            <div className="leading-tight">
              <div className="text-foreground">디자인 시스템 에셋 라이브러리</div>
              <div className="text-muted-foreground text-xs">
                최근 사용률 또는 유용한 항목을 빠르게 찾아보세요.
              </div>
            </div>
            <div className="flex items-center gap-2">
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input placeholder="자료실 검색" className="h-8 w-36 pl-8" />
              </div>
              <Button variant="outline" size="icon" className="size-8" aria-label="필터">
                <SlidersHorizontal className="size-4" />
              </Button>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {/* Figma 링크 */}
            <button
              onClick={() => toast("Figma 링크를 엽니다.")}
              className="rounded-lg border border-border p-3 text-left transition-colors hover:bg-muted/60"
            >
              <div className="flex items-center gap-2 mb-2">
                <Figma className="size-5 text-purple-600" />
                <span className="text-foreground text-sm">Figma 링크</span>
              </div>
              <Badge variant="secondary" className="mb-2 font-normal">자사 디자인 시스템</Badge>
              <div className="text-muted-foreground text-xs">Figma 파일</div>
            </button>

            {/* 아이콘 세트 */}
            <div className="rounded-lg border border-border p-3">
              <div className="grid grid-cols-6 gap-1.5 mb-2">
                {ICON_SET.map((Icon, i) => (
                  <span key={i} className="flex size-5 items-center justify-center text-muted-foreground">
                    <Icon className="size-3.5" />
                  </span>
                ))}
              </div>
              <div className="text-foreground text-sm">아이콘 세트</div>
              <div className="text-muted-foreground text-xs">아이콘 모음</div>
            </div>

            {/* 다이콘 스타일 라이브러리 */}
            <div className="rounded-lg border border-border p-3">
              <div className="flex flex-wrap gap-1 mb-2">
                {["bg-blue-500", "bg-red-500", "bg-emerald-500", "bg-purple-500", "bg-amber-500", "bg-indigo-500"].map(
                  (c, i) => (
                    <span key={i} className={cn("size-4 rounded", c)} />
                  ),
                )}
              </div>
              <div className="text-foreground text-sm">다이콘 스타일 라이브러리</div>
              <div className="text-muted-foreground text-xs">아이콘 패키지</div>
            </div>
          </div>

          <div className="mt-3 grid grid-cols-3 gap-3">
            <div className="flex items-center justify-center gap-1 rounded-lg border border-border p-3">
              {[FileText, Grid3x3, Figma, LayoutGrid].map((Icon, i) => (
                <Icon key={i} className="size-4 text-muted-foreground" />
              ))}
            </div>
            <div className="rounded-lg border border-border bg-muted/40 p-3" />
            <button
              onClick={() => toast.success("새 에셋 추가")}
              className="flex flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-border p-3 text-muted-foreground transition-colors hover:bg-muted/60"
            >
              <Plus className="size-5" />
              <span className="text-xs">새 에셋 추가</span>
            </button>
          </div>
        </CardContent>
      </Card>

      {/* 내가 작성한 문서 */}
      <Card>
        <CardContent className="pt-5">
          <div className="flex items-center justify-between mb-4">
            <span className="text-foreground">[내가 작성한 문서]</span>
            <div className="flex items-center gap-2">
              <div className="relative">
                <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  value={myQuery}
                  onChange={(e) => setMyQuery(e.target.value)}
                  placeholder="문서 검색"
                  className="h-8 w-36 pl-8"
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
                <TableHead className="w-28">수정일시</TableHead>
                <TableHead className="w-24"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredMy.map((d) => (
                <TableRow key={d.id}>
                  <TableCell>
                    <Checkbox
                      checked={mySel.has(d.id)}
                      onCheckedChange={() => toggle(mySel, d.id, setMySel)}
                    />
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <FileTypeIcon kind={d.kind} />
                      <span className="text-foreground text-sm">{d.name}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="secondary" className="font-normal">{d.category}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground text-xs">{d.updatedAt}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-1 text-muted-foreground">
                      <button onClick={() => toast(`'${d.name}' 다운로드`)} className="hover:text-foreground" aria-label="다운로드">
                        <Download className="size-4" />
                      </button>
                      <button onClick={() => toast(`'${d.name}' 버전 기록`)} className="hover:text-foreground" aria-label="기록">
                        <History className="size-4" />
                      </button>
                      <button onClick={() => toast("더보기")} className="hover:text-foreground" aria-label="더보기">
                        <MoreVertical className="size-4" />
                      </button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
              {filteredMy.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="text-center text-muted-foreground py-6">
                    검색 결과가 없습니다.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
          <div className="mt-4 flex justify-end border-t border-border pt-4">
            <Button onClick={() => toast.success("챗봇으로 가져왔습니다.")}>
              <Bot className="size-4" /> 챗봇으로 가져오기
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 문서 리뷰 피드백 & 진행 현황 */}
      <Card>
        <CardContent className="pt-5">
          <div className="flex items-start justify-between gap-3 mb-4">
            <div className="leading-tight">
              <div className="text-foreground">문서 리뷰 피드백 & 진행 현황</div>
              <div className="text-muted-foreground text-xs">
                최근 리뷰 및 피드백을 확인하고 진행 상황을 관리하세요.
              </div>
            </div>
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input placeholder="RFP_선규제" className="h-8 w-36 pl-8" />
            </div>
          </div>
          <div className="mb-2 flex items-center justify-between text-muted-foreground text-xs">
            <span>최근 활동</span>
            <span>상태</span>
          </div>
          <div className="divide-y divide-border">
            {reviewActivity.map((r) => (
              <div key={r.id} className="flex items-start justify-between gap-3 py-3">
                <div className="flex items-start gap-2">
                  <Avatar className="size-7">
                    <AvatarFallback className="text-[10px]">{r.author.slice(0, 1)}</AvatarFallback>
                  </Avatar>
                  <div className="leading-tight">
                    <div className="text-foreground text-sm">{r.text}</div>
                    <div className="text-muted-foreground text-xs">{r.sub}</div>
                  </div>
                </div>
                <Badge variant="outline" className={cn("shrink-0 font-normal", reviewBadge(r.status))}>
                  {r.status}
                </Badge>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
