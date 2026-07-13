import { useEffect, useRef, useState } from "react";

interface CountUpProps {
  /** "8건", "+4일", "62%", "윤다영 · 3건" 처럼 숫자가 포함된 문자열/숫자 */
  value: string | number;
  durationMs?: number;
  className?: string;
}

const prefersReduced = () =>
  typeof window !== "undefined" &&
  window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

/**
 * 문자열 안의 첫 숫자를 0 → 목표값으로 부드럽게 카운트업.
 * 접두/접미(부호·단위·텍스트)는 그대로 유지한다.
 */
export function CountUp({ value, durationMs = 900, className }: CountUpProps) {
  const str = String(value);
  const match = str.match(/-?\d[\d,]*\.?\d*/);
  const target = match ? parseFloat(match[0].replace(/,/g, "")) : NaN;
  const decimals =
    match && match[0].includes(".") ? match[0].split(".")[1]?.length ?? 0 : 0;

  const [display, setDisplay] = useState<number>(() =>
    isNaN(target) || prefersReduced() ? target : 0,
  );
  const rafRef = useRef<number | null>(null);

  useEffect(() => {
    if (isNaN(target) || prefersReduced()) {
      setDisplay(target);
      return;
    }
    const start = performance.now();
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs);
      const eased = 1 - Math.pow(1 - t, 3); // easeOutCubic
      setDisplay(target * eased);
      if (t < 1) rafRef.current = requestAnimationFrame(tick);
      else setDisplay(target);
    };
    rafRef.current = requestAnimationFrame(tick);
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [target, durationMs]);

  if (isNaN(target) || !match) return <span className={className}>{str}</span>;

  const shown = display.toLocaleString("ko-KR", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
  return <span className={className}>{str.replace(match[0], shown)}</span>;
}
