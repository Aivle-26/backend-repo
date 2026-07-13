import { useMemo, useState } from "react";
import { FileText, Search } from "lucide-react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/app/components/ui/card";
import { Badge } from "@/app/components/ui/badge";
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
import { projectRepository } from "@/app/api/projectRepository";
import type { ReqStatus } from "@/app/data/demoData";

function priorityVariant(p: string) {
  if (p === "높음") return "destructive" as const;
  if (p === "중간") return "secondary" as const;
  return "outline" as const;
}

function statusClass(s: ReqStatus) {
  const map: Record<ReqStatus, string> = {
    "미배정": "bg-muted text-muted-foreground",
    "배정됨": "bg-blue-50 text-blue-700 border-blue-200",
    "검토중": "bg-amber-50 text-amber-700 border-amber-200",
    "완료": "bg-emerald-50 text-emerald-700 border-emerald-200",
  };
  return map[s];
}

export function PmRequirements() {
  const { requirements } = projectRepository.getPmRequirements();
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("전체");
  const [status, setStatus] = useState<"전체" | ReqStatus>("전체");

  const categories = useMemo(
    () => ["전체", ...Array.from(new Set(requirements.map((r) => r.category)))],
    [requirements],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return requirements.filter((r) => {
      const byCat = category === "전체" || r.category === category;
      const bySt = status === "전체" || r.status === status;
      const byQ = !q || r.text.toLowerCase().includes(q);
      return byCat && bySt && byQ;
    });
  }, [requirements, query, category, status]);

  const total = requirements.length;
  const done = requirements.filter((r) => r.status === "완료").length;

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <CardTitle className="flex items-center gap-2">
                <FileText className="size-4" /> 요구사항 목록
              </CardTitle>
              <CardDescription>
                AI가 추출한 요구사항 {total}건 중 {done}건 완료
              </CardDescription>
            </div>
            <div className="relative">
              <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="요구사항 검색"
                className="h-9 w-56 pl-8"
              />
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            {categories.map((c) => (
              <button
                key={c}
                onClick={() => setCategory(c)}
                className={cn(
                  "rounded-full px-3 py-1 text-sm transition-colors",
                  category === c
                    ? "bg-primary text-primary-foreground"
                    : "bg-muted text-muted-foreground hover:bg-muted/70",
                )}
              >
                {c}
              </button>
            ))}
            <span className="mx-1 h-4 w-px bg-border" />
            {(["전체", "미배정", "배정됨", "검토중", "완료"] as const).map((s) => (
              <button
                key={s}
                onClick={() => setStatus(s)}
                className={cn(
                  "rounded-full px-3 py-1 text-sm transition-colors",
                  status === s
                    ? "bg-foreground text-background"
                    : "bg-muted text-muted-foreground hover:bg-muted/70",
                )}
              >
                {s}
              </button>
            ))}
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-8">#</TableHead>
                <TableHead>요구사항</TableHead>
                <TableHead className="w-24">분류</TableHead>
                <TableHead className="w-24">우선순위</TableHead>
                <TableHead className="w-16">난이도</TableHead>
                <TableHead className="w-24">추천 담당자</TableHead>
                <TableHead className="w-24">상태</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.map((r) => (
                <TableRow key={r.id}>
                  <TableCell className="text-muted-foreground">{r.id}</TableCell>
                  <TableCell className="text-foreground text-sm">{r.text}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className="font-normal">{r.category}</Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant={priorityVariant(r.priority)}>{r.priority}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{r.difficulty}</TableCell>
                  <TableCell className="text-muted-foreground text-sm">{r.recommendedOwner}</TableCell>
                  <TableCell>
                    <Badge variant="outline" className={cn("font-normal", statusClass(r.status))}>
                      {r.status}
                    </Badge>
                  </TableCell>
                </TableRow>
              ))}
              {filtered.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                    조건에 맞는 요구사항이 없습니다.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
