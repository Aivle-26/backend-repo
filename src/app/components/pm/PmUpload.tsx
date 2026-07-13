import { useRef, useState } from "react";
import { UploadCloud, RefreshCw, Download, Trash2, FileText } from "lucide-react";
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
import type { UploadedRfp } from "@/app/data/demoData";

function statusClass(s: UploadedRfp["status"]) {
  if (s === "분석 완료") return "bg-emerald-50 text-emerald-700 border-emerald-200";
  if (s === "분석 중") return "bg-blue-50 text-blue-700 border-blue-200";
  return "bg-amber-50 text-amber-700 border-amber-200";
}

export function PmUpload() {
  const { uploaded } = projectRepository.getPmUpload();
  const [files, setFiles] = useState<UploadedRfp[]>(uploaded);
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const addFile = async (name: string) => {
    await projectRepository.uploadRfp();
    const now = new Date().toLocaleString("ko-KR", {
      hour: "2-digit",
      minute: "2-digit",
    });
    setFiles((prev) => [
      {
        id: `rfp${Date.now()}`,
        name,
        size: "—",
        uploadedAt: `방금 · ${now}`,
        status: "분석 중",
        requirementCount: 0,
      },
      ...prev,
    ]);
    toast.success(`"${name}" 업로드 완료. AI 분석을 시작합니다.`);
    // 분석 완료 시뮬레이션
    setTimeout(() => {
      setFiles((prev) =>
        prev.map((f) =>
          f.name === name && f.status === "분석 중"
            ? { ...f, status: "분석 완료", requirementCount: 21 }
            : f,
        ),
      );
    }, 1500);
  };

  const handlePick = () => inputRef.current?.click();

  const onSelected = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) addFile(f.name);
    e.target.value = "";
  };

  const reanalyze = async (id: string) => {
    await projectRepository.reanalyzeRfp();
    setFiles((prev) =>
      prev.map((f) => (f.id === id ? { ...f, status: "분석 중" } : f)),
    );
    toast("AI 재분석을 시작했습니다.");
    setTimeout(() => {
      setFiles((prev) =>
        prev.map((f) =>
          f.id === id ? { ...f, status: "분석 완료" } : f,
        ),
      );
    }, 1200);
  };

  const remove = (id: string) => {
    setFiles((prev) => prev.filter((f) => f.id !== id));
    toast("파일을 목록에서 제거했습니다.");
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>RFP 공고문 업로드</CardTitle>
          <CardDescription>
            PDF 형식의 공고문을 업로드하면 AI가 자동으로 요구사항을 추출합니다.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <input
            ref={inputRef}
            type="file"
            accept="application/pdf"
            className="hidden"
            onChange={onSelected}
          />
          <div
            role="button"
            tabIndex={0}
            onClick={handlePick}
            onKeyDown={(e) => (e.key === "Enter" || e.key === " ") && handlePick()}
            onDragOver={(e) => {
              e.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragging(false);
              const f = e.dataTransfer.files?.[0];
              addFile(f ? f.name : "새-공고문.pdf");
            }}
            className={cn(
              "flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-12 text-center transition-colors",
              dragging
                ? "border-primary bg-accent"
                : "border-border bg-muted/40 hover:bg-muted",
            )}
          >
            <UploadCloud className="size-7 text-muted-foreground" />
            <div className="text-foreground">파일을 끌어다 놓거나 클릭하여 업로드</div>
            <div className="text-muted-foreground text-xs">PDF · 최대 50MB</div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>업로드된 공고문</CardTitle>
          <CardDescription>업로드한 RFP 목록과 분석 상태입니다.</CardDescription>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>파일명</TableHead>
                <TableHead className="w-24">용량</TableHead>
                <TableHead className="w-40">업로드</TableHead>
                <TableHead className="w-24">추출 요구사항</TableHead>
                <TableHead className="w-24">상태</TableHead>
                <TableHead className="w-32 text-right">작업</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {files.map((f) => (
                <TableRow key={f.id}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <span className="flex size-8 items-center justify-center rounded-md bg-red-50 text-red-600">
                        <FileText className="size-4" />
                      </span>
                      <span className="text-foreground text-sm">{f.name}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-muted-foreground text-xs">{f.size}</TableCell>
                  <TableCell className="text-muted-foreground text-xs">{f.uploadedAt}</TableCell>
                  <TableCell className="text-muted-foreground text-sm">
                    {f.requirementCount > 0 ? `${f.requirementCount}건` : "—"}
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline" className={cn("font-normal", statusClass(f.status))}>
                      {f.status}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center justify-end gap-1 text-muted-foreground">
                      <button
                        onClick={() => reanalyze(f.id)}
                        className="rounded p-1 hover:bg-muted hover:text-foreground"
                        aria-label="다시 분석"
                        title="다시 분석"
                      >
                        <RefreshCw className="size-4" />
                      </button>
                      <button
                        onClick={() => toast(`"${f.name}" 다운로드`)}
                        className="rounded p-1 hover:bg-muted hover:text-foreground"
                        aria-label="다운로드"
                        title="다운로드"
                      >
                        <Download className="size-4" />
                      </button>
                      <button
                        onClick={() => remove(f.id)}
                        className="rounded p-1 hover:bg-muted hover:text-destructive"
                        aria-label="삭제"
                        title="삭제"
                      >
                        <Trash2 className="size-4" />
                      </button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
              {files.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    업로드된 공고문이 없습니다.
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
