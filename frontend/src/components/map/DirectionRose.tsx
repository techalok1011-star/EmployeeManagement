import { useState } from "react";
import { DIRECTIONS, DIRECTION_NAMES, type Direction, type Sector } from "@/lib/geo";
import { cn } from "@/lib/utils";

const CX = 120, CY = 120;
const polar = (deg: number, r: number): [number, number] => {
  const a = ((deg - 90) * Math.PI) / 180; // 0deg = north, clockwise
  return [CX + r * Math.cos(a), CY + r * Math.sin(a)];
};

/** One petal: a rounded wedge from the centre out to `len` along `deg`. */
function petal(deg: number, len: number): string {
  const [tx, ty] = polar(deg, len);
  const [lx, ly] = polar(deg - 17, len * 0.52);
  const [rx, ry] = polar(deg + 17, len * 0.52);
  const [c1x, c1y] = polar(deg - 12, len * 0.9);
  const [c2x, c2y] = polar(deg + 12, len * 0.9);
  return `M${CX},${CY} L${lx},${ly} Q${c1x},${c1y} ${tx},${ty} Q${c2x},${c2y} ${rx},${ry} Z`;
}

/**
 * The direction rose: eight petals, one per compass direction from Azamgarh, each as long as that direction
 * has collections (square-root scaled so a few big directions don't flatten the rest). Tap a petal to zoom
 * the map to those pins; the middle disc is the city centre (within 3 km).
 */
export function DirectionRose({
  counts,
  onPick,
}: {
  counts: Record<Sector, number>;
  onPick: (s: Sector) => void;
}) {
  const [hot, setHot] = useState<Sector | null>(null);
  const max = Math.max(1, ...DIRECTIONS.map((d) => counts[d]));
  const total = DIRECTIONS.reduce((s, d) => s + counts[d], 0) + counts.C;
  const lenOf = (n: number) => (n === 0 ? 17 : 27 + 50 * Math.sqrt(n / max));

  return (
    <svg viewBox="0 0 240 240" className="mx-auto block w-full max-w-[300px] select-none" role="group" aria-label="Collections by direction from Azamgarh">
      {/* scale rings */}
      {[30, 54, 78].map((r) => (
        <circle key={r} cx={CX} cy={CY} r={r} fill="none" stroke="var(--border)" strokeDasharray="2 5" strokeWidth={1} />
      ))}
      {/* cardinal hairlines */}
      {[0, 90].map((a) => {
        const [x1, y1] = polar(a, 92);
        const [x2, y2] = polar(a + 180, 92);
        return <line key={a} x1={x1} y1={y1} x2={x2} y2={y2} stroke="var(--border)" strokeWidth={1} />;
      })}

      {DIRECTIONS.map((d: Direction, i) => {
        const deg = i * 45;
        const n = counts[d];
        const len = lenOf(n);
        const active = hot === d;
        const [lx, ly] = polar(deg, 111);
        const [nx, ny] = polar(deg, Math.min(len + 11, 92));
        const label = `${DIRECTION_NAMES[d]}: ${n} ${n === 1 ? "collection" : "collections"}`;
        return (
          <g
            key={d}
            role="button"
            tabIndex={n ? 0 : -1}
            aria-label={label}
            aria-disabled={!n}
            className={cn("outline-none", n ? "cursor-pointer" : "cursor-default")}
            onMouseEnter={() => n && setHot(d)}
            onMouseLeave={() => setHot(null)}
            onFocus={() => n && setHot(d)}
            onBlur={() => setHot(null)}
            onClick={() => n && onPick(d)}
            onKeyDown={(e) => {
              if (n && (e.key === "Enter" || e.key === " ")) {
                e.preventDefault();
                onPick(d);
              }
            }}
          >
            <path
              d={petal(deg, len)}
              fill={active ? "var(--hub)" : "var(--primary)"}
              fillOpacity={n ? (active ? 0.9 : 0.28 + 0.6 * Math.sqrt(n / max)) : 0.08}
              stroke={active ? "var(--hub)" : "var(--primary)"}
              strokeOpacity={n ? 0.9 : 0.25}
              strokeWidth={n ? 1.4 : 1}
              strokeLinejoin="round"
              style={{ transition: "fill-opacity .18s, fill .18s" }}
            />
            {/* generous invisible hit area, for fingers */}
            <path d={petal(deg, 92)} fill="transparent" />
            {n > 0 && (
              <text x={nx} y={ny} textAnchor="middle" dominantBaseline="central" className="num fill-foreground" fontSize="11" fontFamily="var(--font-mono)" fontWeight={500}>
                {n}
              </text>
            )}
            <text
              x={lx}
              y={ly}
              textAnchor="middle"
              dominantBaseline="central"
              fontSize={i % 2 === 0 ? 12 : 10}
              fontFamily="var(--font-display)"
              fontWeight={700}
              className={active ? "fill-hub" : "fill-muted-foreground"}
            >
              {d}
            </text>
          </g>
        );
      })}

      {/* city centre */}
      <g
        role="button"
        tabIndex={counts.C ? 0 : -1}
        aria-label={`City centre: ${counts.C} ${counts.C === 1 ? "collection" : "collections"}`}
        aria-disabled={!counts.C}
        className={cn("outline-none", counts.C ? "cursor-pointer" : "cursor-default")}
        onClick={() => counts.C && onPick("C")}
        onKeyDown={(e) => {
          if (counts.C && (e.key === "Enter" || e.key === " ")) {
            e.preventDefault();
            onPick("C");
          }
        }}
      >
        <circle cx={CX} cy={CY} r={17} className="fill-card" stroke="var(--hub)" strokeWidth={2} />
        <text x={CX} y={CY} textAnchor="middle" dominantBaseline="central" fontSize="12" fontFamily="var(--font-mono)" fontWeight={500} className="num fill-foreground">
          {counts.C}
        </text>
      </g>
      <title>{`${total} collections around Azamgarh`}</title>
    </svg>
  );
}
