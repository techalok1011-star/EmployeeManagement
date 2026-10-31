import type { Collection } from "@/lib/api";
import { DIRECTIONS, distKm, sectorOf, type Sector } from "@/lib/geo";

/** Colour-blind-safe categorical set (Okabe-Ito based) - one colour per collector. */
export const PALETTE = [
  "#4c8dff", "#f5a623", "#2ec4a0", "#e5484d", "#b07cff", "#2db7e0",
  "#f26fb0", "#9bcb3c", "#ff8a3d", "#17a398", "#7a86ff", "#e6c229",
];

export type Pt = Collection & { color: string };

export type Collector = { user: string; name: string; count: number; color: string };

/** Collectors ordered by number of located collections, each with a stable colour. */
export function collectorsOf(collections: Collection[]): Collector[] {
  const by = new Map<string, Collector>();
  for (const c of collections) {
    const hit = by.get(c.user);
    if (hit) hit.count++;
    else by.set(c.user, { user: c.user, name: c.by, count: 1, color: "" });
  }
  return [...by.values()]
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
    .map((c, i) => ({ ...c, color: PALETTE[i % PALETTE.length] }));
}

export type PartyRow = {
  name: string;
  count: number;
  total: number;
  lat: number;
  lng: number;
  /** metres from the party's average position to its farthest collection */
  spread: number;
  last: Pt;
};

export function groupParties(points: Pt[]): PartyRow[] {
  const groups = new Map<string, Pt[]>();
  for (const p of points) {
    const g = groups.get(p.party);
    if (g) g.push(p);
    else groups.set(p.party, [p]);
  }
  const rows: PartyRow[] = [];
  for (const [name, g] of groups) {
    let lat = 0, lng = 0, total = 0;
    for (const p of g) {
      lat += p.lat;
      lng += p.lng;
      total += p.amount;
    }
    lat /= g.length;
    lng /= g.length;
    let spread = 0;
    for (const p of g) spread = Math.max(spread, distKm([lng, lat], [p.lng, p.lat]) * 1000);
    const last = g.reduce((a, b) => ((a.date ?? "") >= (b.date ?? "") ? a : b));
    rows.push({ name, count: g.length, total, lat, lng, spread, last });
  }
  return rows.sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
}

export function bucketBySector(points: Pt[]): Record<Sector, Pt[]> {
  const out = { C: [] } as unknown as Record<Sector, Pt[]>;
  for (const d of DIRECTIONS) out[d] = [];
  for (const p of points) out[sectorOf(p)].push(p);
  return out;
}
