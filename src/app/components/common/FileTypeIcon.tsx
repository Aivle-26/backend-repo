import {
  FileText,
  FileSpreadsheet,
  Presentation,
  Image as ImageIcon,
  Figma,
  File as FileIcon,
} from "lucide-react";
import type { FileKind } from "@/app/data/demoData";
import { cn } from "@/app/components/ui/utils";

const META: Record<
  FileKind,
  { icon: React.ComponentType<{ className?: string }>; className: string; label: string }
> = {
  pdf: { icon: FileText, className: "bg-red-50 text-red-600", label: "PDF" },
  word: { icon: FileText, className: "bg-blue-50 text-blue-600", label: "DOC" },
  excel: { icon: FileSpreadsheet, className: "bg-emerald-50 text-emerald-600", label: "XLS" },
  ppt: { icon: Presentation, className: "bg-orange-50 text-orange-600", label: "PPT" },
  image: { icon: ImageIcon, className: "bg-amber-50 text-amber-600", label: "IMG" },
  figma: { icon: Figma, className: "bg-purple-50 text-purple-600", label: "FIG" },
};

interface FileTypeIconProps {
  kind: FileKind;
  className?: string;
}

export function FileTypeIcon({ kind, className }: FileTypeIconProps) {
  const meta = META[kind] ?? { icon: FileIcon, className: "bg-muted text-muted-foreground", label: "" };
  const Icon = meta.icon;
  return (
    <span
      className={cn(
        "flex size-8 shrink-0 items-center justify-center rounded-md",
        meta.className,
        className,
      )}
    >
      <Icon className="size-4" />
    </span>
  );
}
