import { Moon, Sun } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { useTheme } from "@/app/hooks/useTheme";
import { cn } from "@/app/components/ui/utils";

interface ThemeToggleProps {
  className?: string;
  variant?: "ghost" | "outline";
}

export function ThemeToggle({ className, variant = "ghost" }: ThemeToggleProps) {
  const { theme, toggle } = useTheme();
  const isDark = theme === "dark";
  return (
    <Button
      variant={variant}
      size="icon"
      onClick={toggle}
      aria-label={isDark ? "라이트 모드로 전환" : "다크 모드로 전환"}
      title={isDark ? "라이트 모드" : "다크 모드"}
      className={cn(className)}
    >
      {isDark ? <Sun className="size-4" /> : <Moon className="size-4" />}
    </Button>
  );
}
