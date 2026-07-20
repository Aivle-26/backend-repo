import { useRef } from "react";
import { UploadCloud, FileText, X } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import type { ProjectDoc, ProjectDocType } from "@/app/data/demoData";

export const DOC_TYPE_OPTIONS: ProjectDocType[] = ["RFP", "요구사항정의서", "제안서"];

/** 초기 문서 업로드 UI — 파일 선택 + 타입 지정 + 삭제 (여러 화면에서 재사용) */
export function DocPicker({
  docs,
  onChange,
}: {
  docs: ProjectDoc[];
  onChange: (docs: ProjectDoc[]) => void;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const addFiles = (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const added: ProjectDoc[] = Array.from(files).map((f) => ({
      name: f.name,
      type: "RFP",
    }));
    onChange([...docs, ...added]);
  };
  return (
    <div className="space-y-2">
      <input
        ref={inputRef}
        type="file"
        multiple
        className="hidden"
        onChange={(e) => {
          addFiles(e.target.files);
          e.target.value = "";
        }}
      />
      <div
        role="button"
        tabIndex={0}
        onClick={() => inputRef.current?.click()}
        onKeyDown={(e) =>
          (e.key === "Enter" || e.key === " ") && inputRef.current?.click()
        }
        onDragOver={(e) => e.preventDefault()}
        onDrop={(e) => {
          e.preventDefault();
          addFiles(e.dataTransfer.files);
        }}
        className="flex cursor-pointer flex-col items-center gap-1.5 rounded-lg border border-dashed border-border bg-muted/40 py-6 text-center transition-colors hover:bg-muted"
      >
        <UploadCloud className="size-6 text-muted-foreground" />
        <span className="text-foreground text-sm">문서를 끌어다 놓거나 클릭해 업로드</span>
        <span className="text-muted-foreground text-xs">
          RFP · 요구사항정의서 · 제안서 (PDF/DOCX)
        </span>
      </div>
      {docs.length > 0 && (
        <div className="space-y-1.5">
          {docs.map((d, i) => (
            <div
              key={`${d.name}-${i}`}
              className="flex items-center gap-2 rounded-md border border-border px-2.5 py-1.5"
            >
              <FileText className="size-4 shrink-0 text-muted-foreground" />
              <span className="flex-1 truncate text-sm text-foreground">{d.name}</span>
              <Select
                value={d.type}
                onValueChange={(v) =>
                  onChange(
                    docs.map((x, idx) =>
                      idx === i ? { ...x, type: v as ProjectDocType } : x,
                    ),
                  )
                }
              >
                <SelectTrigger className="h-7 w-32 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {DOC_TYPE_OPTIONS.map((t) => (
                    <SelectItem key={t} value={t}>
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <button
                onClick={() => onChange(docs.filter((_, idx) => idx !== i))}
                className="text-muted-foreground hover:text-foreground"
                aria-label="제거"
              >
                <X className="size-4" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
