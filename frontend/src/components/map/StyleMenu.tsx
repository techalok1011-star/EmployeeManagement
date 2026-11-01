import { useEffect, useRef, useState } from "react";
import { Check, Layers } from "lucide-react";
import { Button } from "@/components/ui/button";
import { BASEMAPS, type Basemap } from "@/lib/basemap";
import { cn } from "@/lib/utils";

/** "Map style" button + small menu: Street map (OpenStreetMap), Dark, Light. */
export function StyleMenu({ value, onChange }: { value: Basemap; onChange: (b: Basemap) => void }) {
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("pointerdown", onDown);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onDown);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div ref={box} className="pointer-events-auto relative">
      <Button
        variant="glass"
        size="icon"
        onClick={() => setOpen((o) => !o)}
        aria-label="Map style"
        aria-haspopup="menu"
        aria-expanded={open}
        title="Map style"
      >
        <Layers />
      </Button>
      {open && (
        <div role="menu" aria-label="Map style" className="glass absolute right-0 top-[calc(100%+8px)] z-50 w-64 rounded-2xl p-1.5 animate-in fade-in-0 zoom-in-95">
          {BASEMAPS.map((b) => {
            const on = b.id === value;
            return (
              <button
                key={b.id}
                type="button"
                role="menuitemradio"
                aria-checked={on}
                onClick={() => {
                  onChange(b.id);
                  setOpen(false);
                }}
                className={cn(
                  "flex min-h-14 w-full items-center gap-3 rounded-xl px-2.5 text-left outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring",
                  on && "bg-muted",
                )}
              >
                <span className="size-9 shrink-0 rounded-lg ring-1 ring-border" style={{ background: b.swatch }} aria-hidden />
                <span className="min-w-0 flex-1">
                  <span className="block text-[14px] font-semibold leading-tight">{b.label}</span>
                  <span className="block text-xs leading-snug text-muted-foreground">{b.hint}</span>
                </span>
                {on && <Check className="size-4 text-primary" aria-hidden />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
