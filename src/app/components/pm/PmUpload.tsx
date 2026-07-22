import { useMemo, useRef, useState, type ChangeEvent } from "react";
import { AlertTriangle, CheckCircle2, FileText, Loader2, UploadCloud } from "lucide-react";
import { toast } from "sonner";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/app/components/ui/card";
import { Button } from "@/app/components/ui/button";
import { cn } from "@/app/components/ui/utils";
import { projectRepository } from "@/app/api/projectRepository";
import type { ProjectSummary } from "@/app/data/demoData";

export function PmUpload({ project }: { project: ProjectSummary }) {
  const [selectedFiles, setSelectedFiles] = useState<File[]>([]);
  const [dragging, setDragging] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successPayload, setSuccessPayload] = useState<unknown | null>(null);
  const [errorPayload, setErrorPayload] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const relayProjectId = useMemo(() => {
    const numeric = Number.parseInt(project.id, 10);
    return Number.isFinite(numeric) ? numeric : 1;
  }, [project.id]);

  const addFiles = (incoming: FileList | null) => {
    if (!incoming || incoming.length === 0) return;
    setSelectedFiles((prev) => [...prev, ...Array.from(incoming)]);
    setErrorPayload(null);
  };

  const onSelected = (event: ChangeEvent<HTMLInputElement>) => {
    addFiles(event.target.files);
    event.target.value = "";
  };

  const removeFile = (index: number) => {
    setSelectedFiles((prev) => prev.filter((_, currentIndex) => currentIndex !== index));
  };

  const submitFiles = async () => {
    if (selectedFiles.length === 0) {
      toast.error("Select at least one file.");
      return;
    }

    setIsSubmitting(true);
    setErrorPayload(null);

    try {
      const payload = await projectRepository.extractProjectDocuments(relayProjectId, selectedFiles);
      setSuccessPayload(payload);
      toast.success("Files were relayed to the AI server.");
    } catch (error) {
      const message = error instanceof Error ? error.message : "Document relay request failed.";
      setErrorPayload(message);
      setSuccessPayload(null);
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Document Relay Test</CardTitle>
          <CardDescription>
            Upload files through Spring Boot and inspect the raw AI server response.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <input
            ref={inputRef}
            type="file"
            multiple
            accept=".pdf,.hwp,.hwpx,.docx,.txt,.md,.csv"
            className="hidden"
            onChange={onSelected}
          />

          <div
            role="button"
            tabIndex={0}
            onClick={() => inputRef.current?.click()}
            onKeyDown={(event) =>
              (event.key === "Enter" || event.key === " ") && inputRef.current?.click()
            }
            onDragOver={(event) => {
              event.preventDefault();
              setDragging(true);
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => {
              event.preventDefault();
              setDragging(false);
              addFiles(event.dataTransfer.files);
            }}
            className={cn(
              "flex cursor-pointer flex-col items-center justify-center gap-2 rounded-lg border border-dashed py-12 text-center transition-colors",
              dragging
                ? "border-primary bg-accent"
                : "border-border bg-muted/40 hover:bg-muted",
            )}
          >
            <UploadCloud className="size-7 text-muted-foreground" />
            <div className="text-foreground">Drop files here or click to select.</div>
            <div className="text-muted-foreground text-xs">
              Supported: PDF, HWP, HWPX, DOCX, TXT, MD, CSV
            </div>
          </div>

          <div className="rounded-lg border border-border">
            <div className="border-b border-border px-4 py-3 text-sm text-muted-foreground">
              Selected files: {selectedFiles.length}
            </div>
            <div className="space-y-2 p-4">
              {selectedFiles.length === 0 && (
                <div className="text-sm text-muted-foreground">No files selected.</div>
              )}
              {selectedFiles.map((file, index) => (
                <div
                  key={`${file.name}-${index}`}
                  className="flex items-center gap-3 rounded-md border border-border px-3 py-2"
                >
                  <FileText className="size-4 text-muted-foreground" />
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm text-foreground">{file.name}</div>
                    <div className="text-xs text-muted-foreground">
                      {(file.size / 1024 / 1024).toFixed(2)} MB
                    </div>
                  </div>
                  <Button variant="ghost" size="sm" onClick={() => removeFile(index)}>
                    Remove
                  </Button>
                </div>
              ))}
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Button onClick={submitFiles} disabled={isSubmitting || selectedFiles.length === 0}>
              {isSubmitting ? (
                <>
                  <Loader2 className="size-4 animate-spin" /> Uploading
                </>
              ) : (
                <>
                  <UploadCloud className="size-4" /> Upload
                </>
              )}
            </Button>
            <div className="text-xs text-muted-foreground">
              Relay project id: {relayProjectId}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Relay Result</CardTitle>
          <CardDescription>
            The backend returns the AI server JSON response as-is. Errors are shown below.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="rounded-lg border border-border p-4">
            <div className="mb-2 flex items-center gap-2 text-sm text-foreground">
              <CheckCircle2 className="size-4 text-emerald-600" />
              Success response
            </div>
            <pre className="overflow-auto rounded-md bg-muted p-3 text-xs text-foreground">
              {successPayload ? JSON.stringify(successPayload, null, 2) : "No successful response yet."}
            </pre>
          </div>

          <div className="rounded-lg border border-border p-4">
            <div className="mb-2 flex items-center gap-2 text-sm text-foreground">
              <AlertTriangle className="size-4 text-amber-600" />
              Error response
            </div>
            <pre className="overflow-auto rounded-md bg-muted p-3 text-xs text-foreground">
              {errorPayload ?? "No error response."}
            </pre>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
