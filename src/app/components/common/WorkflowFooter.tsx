import { ArrowRight } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { projectRepository } from "@/app/api/projectRepository";

export function WorkflowFooter() {
  const workflowSteps = projectRepository.getWorkflowSteps();

  return (
    <Card>
      <CardHeader>
        <CardTitle>협업 흐름</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-wrap items-center gap-2">
          {workflowSteps.map((step, i) => (
            <div key={step} className="flex items-center gap-2">
              <div className="flex items-center gap-2 rounded-md border border-border bg-muted px-3 py-2">
                <span className="flex size-5 items-center justify-center rounded-full bg-primary text-primary-foreground text-xs">
                  {i + 1}
                </span>
                <span className="text-foreground text-sm">{step}</span>
              </div>
              {i < workflowSteps.length - 1 && (
                <ArrowRight className="size-4 text-muted-foreground" />
              )}
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
