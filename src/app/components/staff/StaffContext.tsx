import { useMemo, useState } from "react";
import { BookOpen, Search } from "lucide-react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  CardDescription,
} from "@/app/components/ui/card";
import { Badge } from "@/app/components/ui/badge";
import { Input } from "@/app/components/ui/input";
import { cn } from "@/app/components/ui/utils";
import { projectRepository } from "@/app/api/projectRepository";

function priorityVariant(p: string) {
  if (p === "높음") return "destructive" as const;
  if (p === "중간") return "secondary" as const;
  return "outline" as const;
}

export function StaffContext() {
  const { requirements } = projectRepository.getStaffContext();
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState<string>("전체");

  const categories = useMemo(
    () => ["전체", ...Array.from(new Set(requirements.map((r) => r.category)))],
    [requirements],
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return requirements.filter((r) => {
      const matchCat = category === "전체" || r.category === category;
      const matchQ = !q || r.text.toLowerCase().includes(q);
      return matchCat && matchQ;
    });
  }, [requirements, query, category]);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <BookOpen className="size-4" /> RFP 맥락
          </CardTitle>
          <CardDescription>
            내 업무와 연결된 RFP 요구사항의 맥락을 확인하세요.
          </CardDescription>
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
            <div className="relative ml-auto">
              <Search className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="요구사항 검색"
                className="h-8 w-44 pl-8"
              />
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {filtered.map((r) => (
              <div key={r.id} className="rounded-lg border border-border p-4">
                <div className="mb-2 flex items-center gap-2">
                  <Badge variant="outline">{r.category}</Badge>
                  <Badge variant={priorityVariant(r.priority)}>{r.priority}</Badge>
                  <span className="ml-auto text-muted-foreground text-xs">
                    난이도 {r.difficulty}
                  </span>
                </div>
                <p className="text-foreground text-sm">{r.text}</p>
                <div className="mt-3 border-t border-border pt-2 text-muted-foreground text-xs">
                  추천 담당자 · {r.recommendedOwner} · 상태 {r.status}
                </div>
              </div>
            ))}
            {filtered.length === 0 && (
              <p className="col-span-full py-8 text-center text-muted-foreground text-sm">
                조건에 맞는 요구사항이 없습니다.
              </p>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
