import { useEffect, useMemo } from "react";
import { ArrowRight } from "lucide-react";
import { RouteGlobe, type GlobeTheme, type Place } from "@/components/ui/route-globe";
import { Button } from "@/components/ui/button";
import { AZAMGARH, distKm } from "@/lib/geo";
import type { ThemeName } from "@/hooks/useTheme";
import { cn } from "@/lib/utils";

const HUB: Place = { name: "Azamgarh", lon: AZAMGARH[0], lat: AZAMGARH[1] };

// Reference cities around the hub, so the arcs give a sense of scale. "note" is the real distance.
const AROUND: [string, number, number][] = [
  ["Varanasi", 82.99, 25.32],
  ["Gorakhpur", 83.37, 26.76],
  ["Lucknow", 80.95, 26.85],
  ["Prayagraj", 81.85, 25.43],
  ["Patna", 85.14, 25.59],
  ["Kathmandu", 85.32, 27.72],
  ["Delhi", 77.21, 28.61],
  ["Kolkata", 88.36, 22.57],
  ["Mumbai", 72.88, 19.08],
  ["Bengaluru", 77.59, 12.97],
];
const PLACES: Place[] = AROUND.map(([name, lon, lat]) => ({
  name,
  lon,
  lat,
  note: `${Math.round(distKm(AZAMGARH, [lon, lat])).toLocaleString("en-IN")} km`,
}));

const GLOBE: Record<ThemeName, GlobeTheme> = {
  dark: { ground: "#06080d", land: "#9fb4d6", arc: "#ffb547", glow: "rgb(90 140 255 / 0.20)", text: "#eef2fb", muted: "#7c8aa5" },
  light: { ground: "#eef1f8", land: "#2b3a67", arc: "#c26a0a", glow: "rgb(59 91 219 / 0.16)", text: "#10142b", muted: "#566080" },
};

export const INTRO_MS = 3600;

/**
 * Full-screen globe that sits on top of the (already mounted) map. After a few seconds - or when the person
 * taps "Open the map" - it scales up and fades away while the map below flies in to Azamgarh, so the
 * globe appears to be zoomed into rather than replaced.
 */
export function IntroGlobe({
  theme,
  leaving,
  located,
  loading,
  onEnter,
}: {
  theme: ThemeName;
  leaving: boolean;
  located: number;
  loading: boolean;
  onEnter: () => void;
}) {
  const globeTheme = GLOBE[theme];
  const reduced = useMemo(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches, []);
  const autoMs = reduced ? 900 : INTRO_MS;

  useEffect(() => {
    if (leaving) return;
    const t = window.setTimeout(onEnter, autoMs);
    return () => window.clearTimeout(t);
  }, [leaving, autoMs, onEnter]);

  return (
    <div
      className={cn(
        "absolute inset-0 z-40 origin-[50%_46%] bg-background transition-[opacity,transform] ease-in",
        leaving ? "pointer-events-none scale-[2.4] opacity-0 duration-[1400ms]" : "scale-100 opacity-100 duration-300",
      )}
      aria-hidden={leaving}
    >
      <RouteGlobe hub={HUB} places={PLACES} theme={globeTheme} motion={reduced ? "reduced" : "full"} style={{ position: "absolute", inset: 0 }} />

      <div className="pointer-events-none absolute inset-x-0 bottom-0 flex flex-col gap-5 px-5 pb-[max(1.75rem,env(safe-area-inset-bottom))] pt-24 md:px-10 md:pb-12">
        <div className="max-w-2xl">
          <p className="eyebrow text-hub">PayTrack · Field map</p>
          <h1 className="mt-3 max-w-[16ch] text-balance font-display text-[clamp(1.8rem,4.6vw,3.3rem)] font-extrabold leading-[1.05] tracking-tight sm:max-w-[18ch]">
            Where every receipt was collected.
          </h1>
          <p className="mt-3 max-w-md text-[15px] leading-relaxed text-muted-foreground" aria-live="polite">
            {loading
              ? "Reading the locations saved by your team…"
              : located > 0
                ? `${located} pinned ${located === 1 ? "collection" : "collections"} around Azamgarh and the Purvanchal belt.`
                : "Pins appear here as staff save collections from the field."}
          </p>
        </div>

        <div className="pointer-events-auto flex items-center gap-4">
          <Button size="default" className="h-12 px-6 text-[15px]" onClick={onEnter} autoFocus>
            Open the map <ArrowRight />
          </Button>
          <div className="hidden min-w-32 flex-col gap-1.5 sm:flex" aria-hidden>
            <span className="eyebrow">Opening automatically</span>
            <span className="h-px w-full overflow-hidden bg-border">
              {!leaving && <span key="bar" className="intro-bar block h-px bg-hub" style={{ animationDuration: `${autoMs}ms` }} />}
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
