import { ArrowLeft, Globe2, Moon, Sun } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Segmented } from "@/components/ui/segmented";
import { PERIODS, type Period } from "@/lib/api";
import type { ThemeName } from "@/hooks/useTheme";
import { cn } from "@/lib/utils";

export function TopBar({
  theme,
  onToggleTheme,
  period,
  onPeriod,
  onReplay,
}: {
  theme: ThemeName;
  onToggleTheme: () => void;
  period: Period;
  onPeriod: (p: Period) => void;
  onReplay: () => void;
}) {
  return (
    <header className="pointer-events-none absolute inset-x-0 top-0 z-30 flex flex-wrap items-center gap-2 px-3 pt-[max(0.75rem,env(safe-area-inset-top))] md:flex-nowrap md:px-4">
      <a
        href="/admin/dashboard"
        aria-label="Back to the PayTrack dashboard"
        className={cn(buttonVariants({ variant: "glass", size: "icon" }), "pointer-events-auto")}
      >
        <ArrowLeft />
      </a>

      <div className="glass pointer-events-auto flex h-11 min-w-0 items-center gap-2.5 rounded-full px-4">
        <span className="size-2 shrink-0 rounded-full bg-hub" aria-hidden />
        <h1 className="truncate font-display text-[15px] font-bold tracking-tight">Collection map</h1>
        <span className="eyebrow hidden sm:inline">Azamgarh · Purvanchal</span>
      </div>

      <div className="ml-auto flex items-center gap-2">
        <Button variant="glass" size="icon" className="pointer-events-auto" onClick={onReplay} aria-label="Replay the globe intro" title="Replay intro">
          <Globe2 />
        </Button>
        <Button
          variant="glass"
          size="icon"
          className="pointer-events-auto"
          onClick={onToggleTheme}
          aria-label={theme === "dark" ? "Switch to light theme" : "Switch to dark theme"}
          title="Toggle theme"
        >
          {theme === "dark" ? <Sun /> : <Moon />}
        </Button>
      </div>

      <div className="glass pointer-events-auto order-last max-w-full overflow-x-auto rounded-full p-0.5 md:order-none md:absolute md:left-1/2 md:top-[max(0.75rem,env(safe-area-inset-top))] md:-translate-x-1/2">
        <Segmented options={PERIODS} value={period} onChange={onPeriod} label="Show collections from" className="bg-transparent" />
      </div>
    </header>
  );
}
