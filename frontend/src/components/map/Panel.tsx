import { useRef, useState, type ReactNode } from "react";
import { BookOpen, Camera, ChevronUp, ExternalLink, MapPinOff, Search, X } from "lucide-react";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DirectionRose } from "@/components/map/DirectionRose";
import { DIRECTION_NAMES, type Sector } from "@/lib/geo";
import { fmtDate, fmtDistance, fmtMode, inr } from "@/lib/format";
import type { Collector, PartyRow, Pt } from "@/lib/points";
import { cn } from "@/lib/utils";

export type SheetState = "peek" | "half" | "full";
const SHEET_ORDER: SheetState[] = ["peek", "half", "full"];
const SHEET_HEIGHT: Record<SheetState, string> = {
  peek: "168px",
  half: "56dvh",
  full: "calc(100dvh - 96px)",
};

export type PanelProps = {
  desktop: boolean;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  located: number;
  total: number;
  outside: number;
  collectors: Collector[];
  hidden: Set<string>;
  onToggleCollector: (user: string) => void;
  counts: Record<Sector, number>;
  onPickSector: (s: Sector) => void;
  parties: PartyRow[];
  query: string;
  onQuery: (q: string) => void;
  onPickParty: (r: PartyRow) => void;
  selected: Pt | null;
  onClearSelected: () => void;
  showLogins: boolean;
  onShowLogins: (v: boolean) => void;
  loginCount: number;
  sheet: SheetState;
  onSheet: (s: SheetState) => void;
};

function Section({ title, aside, children, className }: { title?: string; aside?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={cn("glass rounded-3xl p-4 md:p-5", className)}>
      {title && (
        <header className="mb-3 flex items-center justify-between gap-2">
          <h2 className="eyebrow">{title}</h2>
          {aside}
        </header>
      )}
      {children}
    </section>
  );
}

function CoverageCard({ p }: { p: PanelProps }) {
  const pct = p.total ? Math.min(100, Math.round((100 * p.located) / p.total)) : 0;
  return (
    <Section>
      <p className="eyebrow">Collections with a place on the map</p>
      {p.error ? (
        <div className="mt-2 space-y-3" role="alert">
          <p className="text-sm text-danger">{p.error}</p>
          <Button size="sm" variant="outline" onClick={p.onRetry}>Try again</Button>
        </div>
      ) : (
        <>
          <div className="mt-1.5 flex items-end gap-3" aria-live="polite">
            <span className={cn("num font-display text-5xl font-bold leading-none tracking-tight", p.loading && "opacity-40")}>
              {p.located}
            </span>
            <span className="pb-1 text-sm leading-tight text-muted-foreground">
              of <span className="num">{p.total}</span> collected
              <br />in this period
            </span>
          </div>
          <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-muted" role="presentation">
            <div className="h-full rounded-full bg-primary transition-[width] duration-500" style={{ width: `${Math.max(pct, p.located ? 2 : 0)}%` }} />
          </div>
          {p.outside > 0 && (
            <p className="mt-2.5 text-xs text-muted-foreground">
              <span className="num font-medium text-foreground">{p.outside}</span> more {p.outside === 1 ? "is" : "are"} outside the Purvanchal view.
            </p>
          )}
          {!p.loading && p.located === 0 && (
            <div className="mt-3 flex gap-3 rounded-2xl bg-muted/70 p-3 text-[13px] leading-snug text-muted-foreground">
              <MapPinOff className="mt-0.5 size-4 shrink-0" aria-hidden />
              <p>
                No collections have a place yet. The phone&apos;s location is saved each time staff enter a collection, so pins
                appear as new collections come in.
              </p>
            </div>
          )}
        </>
      )}
    </Section>
  );
}

function DetailCard({ c, onClose }: { c: Pt; onClose: () => void }) {
  return (
    <Section className="border-hub/50 ring-1 ring-hub/40">
      <header className="mb-2 flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="eyebrow flex items-center gap-1.5">
            <span className="size-2 rounded-full" style={{ background: c.color }} aria-hidden /> Selected collection
          </p>
          <h2 className="mt-1 break-words font-display text-[15px] font-bold leading-snug">{c.party}</h2>
        </div>
        <Button variant="ghost" size="icon" className="-mr-2 -mt-2 size-11" onClick={onClose} aria-label="Close details">
          <X />
        </Button>
      </header>
      <p className="num font-display text-3xl font-bold text-success">{inr(c.amount)}</p>
      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2.5 text-[13px]">
        <div><dt className="eyebrow">Date</dt><dd className="mt-0.5 font-medium">{fmtDate(c.date)}</dd></div>
        <div><dt className="eyebrow">Paid by</dt><dd className="mt-0.5 font-medium">{fmtMode(c.mode)}</dd></div>
        <div><dt className="eyebrow">Collected by</dt><dd className="mt-0.5 font-medium">{c.by}</dd></div>
        <div><dt className="eyebrow">Voucher</dt><dd className="num mt-0.5 font-mono">{c.vch || "—"}</dd></div>
        <div className="col-span-2">
          <dt className="eyebrow">Pinned at</dt>
          <dd className="num mt-0.5 font-mono text-xs text-muted-foreground">{c.lat.toFixed(5)}, {c.lng.toFixed(5)}</dd>
        </div>
      </dl>
      <div className="mt-4 flex flex-wrap gap-2">
        <a className={buttonVariants({ variant: "outline", size: "sm" })} href={`/admin/ledger?partyName=${encodeURIComponent(c.party)}`}>
          <BookOpen /> Party ledger
        </a>
        {c.receipt && (
          <a className={buttonVariants({ variant: "outline", size: "sm" })} href={`/admin/entries/${c.id}/receipt`} target="_blank" rel="noopener noreferrer">
            <Camera /> Receipt photo
          </a>
        )}
        <a className={buttonVariants({ variant: "outline", size: "sm" })} href={`https://www.google.com/maps?q=${c.lat},${c.lng}`} target="_blank" rel="noopener noreferrer">
          <ExternalLink /> Google Maps
        </a>
      </div>
    </Section>
  );
}

function Collectors({ p }: { p: PanelProps }) {
  if (!p.collectors.length) return null;
  return (
    <Section title="Collected by">
      <ul className="flex flex-wrap gap-2">
        {p.collectors.map((c) => {
          const off = p.hidden.has(c.user);
          return (
            <li key={c.user}>
              <button
                type="button"
                aria-pressed={!off}
                onClick={() => p.onToggleCollector(c.user)}
                className={cn(
                  "inline-flex min-h-11 items-center gap-2 rounded-full border px-3.5 text-[13px] font-medium transition-[opacity,background-color] outline-none focus-visible:ring-2 focus-visible:ring-ring",
                  off ? "border-border bg-transparent text-muted-foreground opacity-60" : "border-transparent bg-muted text-foreground",
                )}
              >
                <span className="size-2.5 rounded-full" style={{ background: off ? "transparent" : c.color, boxShadow: `inset 0 0 0 2px ${c.color}` }} aria-hidden />
                {c.name}
                <span className="num font-mono text-xs text-muted-foreground">{c.count}</span>
              </button>
            </li>
          );
        })}
      </ul>
      <label className="mt-3 flex min-h-11 cursor-pointer items-center gap-3 rounded-2xl bg-muted/50 px-3.5 text-[13px]">
        <input type="checkbox" className="size-4 accent-[var(--primary)]" checked={p.showLogins} onChange={(e) => p.onShowLogins(e.target.checked)} />
        <span className="flex-1">Show where staff signed in</span>
        <span className="num font-mono text-xs text-muted-foreground">{p.loginCount}</span>
      </label>
    </Section>
  );
}

function Rose({ p }: { p: PanelProps }) {
  const strongest = (Object.keys(p.counts) as Sector[]).reduce((a, b) => (p.counts[b] > p.counts[a] ? b : a), "C" as Sector);
  const any = Object.values(p.counts).some((n) => n > 0);
  return (
    <Section title="Around Azamgarh">
      <DirectionRose counts={p.counts} onPick={p.onPickSector} />
      <p className="mt-2 text-center text-xs text-muted-foreground">
        {any ? (
          <>
            Most collections are{" "}
            <span className="font-medium text-foreground">{strongest === "C" ? "in the city centre" : `to the ${DIRECTION_NAMES[strongest].toLowerCase()}`}</span>
            . Tap a petal to zoom there.
          </>
        ) : (
          "Petals grow as collections get pinned in each direction."
        )}
      </p>
    </Section>
  );
}

function Parties({ p }: { p: PanelProps }) {
  return (
    <Section title={`Parties · ${p.parties.length}`}>
      <div className="relative">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        <Input className="pl-10" placeholder="Search a party" aria-label="Search parties" value={p.query} onChange={(e) => p.onQuery(e.target.value)} />
      </div>
      {p.parties.length === 0 ? (
        <p className="mt-3 text-[13px] text-muted-foreground">{p.query ? "No party matches that search." : "No parties to show yet."}</p>
      ) : (
        <ul className="mt-2 max-h-80 overflow-y-auto thin-scroll -mx-1.5 pr-1">
          {p.parties.slice(0, 150).map((r) => (
            <li key={r.name}>
              <button
                type="button"
                onClick={() => p.onPickParty(r)}
                className="flex min-h-12 w-full flex-col items-start rounded-2xl px-3 py-2 text-left outline-none transition-colors hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring"
              >
                <span className="w-full break-words text-[13.5px] font-semibold leading-snug">{r.name}</span>
                <span className="num mt-0.5 text-xs text-muted-foreground">
                  {r.count} {r.count === 1 ? "collection" : "collections"}
                  {r.count > 1 && <> · within {fmtDistance(r.spread)}</>} · last {fmtDate(r.last.date)}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {p.parties.length > 0 && (
        <p className="mt-2 text-[11.5px] leading-snug text-muted-foreground">
          &ldquo;Within&rdquo; shows how far apart a party&apos;s collections were made: a small distance means a dependable location for that party.
        </p>
      )}
    </Section>
  );
}

function Body({ p }: { p: PanelProps }) {
  return (
    <>
      {p.selected && <DetailCard c={p.selected} onClose={p.onClearSelected} />}
      <CoverageCard p={p} />
      <Collectors p={p} />
      <Rose p={p} />
      <Parties p={p} />
    </>
  );
}

export function Panel(p: PanelProps) {
  const drag = useRef<number | null>(null);
  const [dragging, setDragging] = useState(false);

  if (p.desktop) {
    return (
      <aside
        aria-label="Collection details"
        className="thin-scroll absolute bottom-4 left-4 top-[84px] z-20 flex w-[392px] flex-col gap-3 overflow-y-auto overscroll-contain pb-1 pr-1.5"
      >
        <Body p={p} />
      </aside>
    );
  }

  const idx = SHEET_ORDER.indexOf(p.sheet);
  const step = (dir: 1 | -1) => p.onSheet(SHEET_ORDER[Math.max(0, Math.min(2, idx + dir))]);

  return (
    <aside
      aria-label="Collection details"
      className="glass fixed inset-x-0 bottom-0 z-30 flex flex-col rounded-t-[28px] transition-[height] duration-300 ease-out"
      style={{ height: SHEET_HEIGHT[p.sheet] }}
    >
      <button
        type="button"
        aria-label={p.sheet === "full" ? "Collapse panel" : "Expand panel"}
        aria-expanded={p.sheet !== "peek"}
        className="grid h-9 w-full shrink-0 touch-none place-items-center outline-none focus-visible:ring-2 focus-visible:ring-ring"
        onPointerDown={(e) => {
          drag.current = e.clientY;
          setDragging(true);
          e.currentTarget.setPointerCapture(e.pointerId);
        }}
        onPointerUp={(e) => {
          const dy = drag.current == null ? 0 : e.clientY - drag.current;
          drag.current = null;
          setDragging(false);
          if (Math.abs(dy) < 8) p.onSheet(SHEET_ORDER[(idx + 1) % 3]);
          else step(dy < 0 ? 1 : -1);
        }}
        onPointerCancel={() => {
          drag.current = null;
          setDragging(false);
        }}
      >
        <span className={cn("h-1.5 w-11 rounded-full bg-muted-foreground/40 transition-colors", dragging && "bg-primary")} />
        <ChevronUp className="sr-only" />
      </button>
      <div className="thin-scroll flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto overscroll-contain px-3 pb-[max(1rem,env(safe-area-inset-bottom))]">
        <Body p={p} />
      </div>
    </aside>
  );
}
