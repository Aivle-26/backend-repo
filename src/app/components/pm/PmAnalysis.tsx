import { useState } from "react";
import { FileText, RefreshCw, AlertTriangle } from "lucide-react";
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
import { Label } from "@/app/components/ui/label";
import { Textarea } from "@/app/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/app/components/ui/table";
import { REQUIREMENTS, RISKS, ASSIGNEES } from "@/app/data/mock";

function priorityVariant(p: string) {
  if (p === "높음") return "destructive" as const;
  if (p === "중간") return "secondary" as const;
  return "outline" as const;
}

export function PmAnalysis() {
  const [selectedId, setSelectedId] = useState<number | null>(REQUIREMENTS[0].id);
  const [assignee, setAssignee] = useState<string>("");
  const [due, setDue] = useState<string>("");
  const [memo, setMemo] = useState<string>("");

  const selected = REQUIREMENTS.find((r) => r.id === selectedId) ?? null;

  const handleAssign = () => {
    if (!selected) return toast.error("요구사항을 선택하세요.");
    if (!assignee) return toast.error("담당자를 지정하세요.");
    toast.success(`"${selected.text.slice(0, 16)}…" 업무를 ${assignee}에게 배정했습니다.`);
  };

  return (
    <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
      <div className="xl:col-span-2 space-y-6">
        {/* File info */}
        <Card>
          <CardContent className="pt-6 flex items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="flex size-10 items-center justify-center rounded-md bg-muted">
                <FileText className="size-5 text-muted-foreground" />
              </div>
              <div className="leading-tight">
                <div className="text-foreground">도시인프라-rfp-2024.pdf</div>
                <div className="text-muted-foreground text-xs">
                  4.2MB · 24개 요구사항 추출 완료
                </div>
              </div>
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => toast("다시 업로드 (mock)")}
              >
                다시 업로드
              </Button>
              <Button
                size="sm"
                onClick={() => toast.success("AI 재분석을 시작했습니다. (mock)")}
              >
                <RefreshCw className="size-4" /> 다시 분석
              </Button>
            </div>
          </CardContent>
        </Card>

        {/* Requirements table */}
        <Card>
          <CardHeader>
            <CardTitle>AI 추출 요구사항</CardTitle>
            <CardDescription>요구사항을 선택해 우측에서 업무로 배정하세요.</CardDescription>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10"></TableHead>
                  <TableHead className="w-8">#</TableHead>
                  <TableHead>요구사항</TableHead>
                  <TableHead className="w-20">분류</TableHead>
                  <TableHead className="w-20">우선순위</TableHead>
                  <TableHead className="w-16">난이도</TableHead>
                  <TableHead className="w-24">추천 담당자</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {REQUIREMENTS.map((r) => (
                  <TableRow
                    key={r.id}
                    data-state={selectedId === r.id ? "selected" : undefined}
                    className="cursor-pointer"
                    onClick={() => {
                      setSelectedId(r.id);
                      setAssignee(r.recommendedOwner);
                    }}
                  >
                    <TableCell>
                      <Checkbox
                        checked={selectedId === r.id}
                        onCheckedChange={() => {
                          setSelectedId(r.id);
                          setAssignee(r.recommendedOwner);
                        }}
                      />
                    </TableCell>
                    <TableCell className="text-muted-foreground">{r.id}</TableCell>
                    <TableCell>{r.text}</TableCell>
                    <TableCell>
                      <Badge variant="outline">{r.category}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={priorityVariant(r.priority)}>{r.priority}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{r.difficulty}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {r.recommendedOwner}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        {/* Risk cards */}
        <div>
          <div className="flex items-center gap-2 mb-3">
            <AlertTriangle className="size-4 text-destructive" />
            <span className="text-foreground">AI 리스크 분석</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {RISKS.map((risk) => (
              <Card key={risk.id}>
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <CardTitle>{risk.title}</CardTitle>
                    <Badge
                      variant={risk.level === "높음" ? "destructive" : "secondary"}
                    >
                      {risk.level}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent>
                  <p className="text-muted-foreground text-sm">{risk.description}</p>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </div>

      {/* Assignment panel */}
      <div className="xl:col-span-1">
        <Card className="sticky top-6">
          <CardHeader>
            <CardTitle>업무 배정</CardTitle>
            <CardDescription>선택한 요구사항을 담당자에게 배정합니다.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label>선택한 요구사항</Label>
              <div className="rounded-md border border-border bg-muted/40 p-3 text-sm text-foreground min-h-16">
                {selected ? selected.text : "요구사항을 선택하세요."}
              </div>
            </div>

            <div className="space-y-2">
              <Label>담당자 지정</Label>
              <Select value={assignee} onValueChange={setAssignee}>
                <SelectTrigger>
                  <SelectValue placeholder="담당자를 선택하세요" />
                </SelectTrigger>
                <SelectContent>
                  {ASSIGNEES.map((a) => (
                    <SelectItem key={a} value={a}>
                      {a}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="due">마감일 설정</Label>
              <Input
                id="due"
                type="date"
                value={due}
                onChange={(e) => setDue(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="memo">업무 메모</Label>
              <Textarea
                id="memo"
                placeholder="담당자에게 전달할 메모를 입력하세요."
                value={memo}
                onChange={(e) => setMemo(e.target.value)}
                rows={4}
              />
            </div>

            <div className="flex gap-2 pt-1">
              <Button className="flex-1" onClick={handleAssign}>
                업무 배정하기
              </Button>
              <Button
                variant="outline"
                onClick={() => toast.success("검토 요청을 보냈습니다. (mock)")}
              >
                검토 요청
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
