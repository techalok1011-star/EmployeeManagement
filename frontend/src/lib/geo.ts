export type LngLat = [number, number];

/** Azamgarh city, [lng, lat] - the hub of the map. */
export const AZAMGARH: LngLat = [83.1859, 26.0739];
/** Points farther than this are outside the "Purvanchal view" the map frames. */
export const VIEW_RADIUS_KM = 120;
/** Closer than this counts as "city centre" in the direction rose. */
export const CENTRE_KM = 3;

const R = 6371;
const rad = (d: number) => (d * Math.PI) / 180;

export function distKm(a: LngLat, b: LngLat): number {
  const dLat = rad(b[1] - a[1]);
  const dLng = rad(b[0] - a[0]);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(rad(a[1])) * Math.cos(rad(b[1])) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Compass bearing from a to b in degrees, 0 = north, clockwise. */
export function bearing(a: LngLat, b: LngLat): number {
  const y = Math.sin(rad(b[0] - a[0])) * Math.cos(rad(b[1]));
  const x =
    Math.cos(rad(a[1])) * Math.sin(rad(b[1])) -
    Math.sin(rad(a[1])) * Math.cos(rad(b[1])) * Math.cos(rad(b[0] - a[0]));
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
}

export const DIRECTIONS = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"] as const;
export type Direction = (typeof DIRECTIONS)[number];
export type Sector = Direction | "C";

export const DIRECTION_NAMES: Record<Sector, string> = {
  N: "North",
  NE: "North-east",
  E: "East",
  SE: "South-east",
  S: "South",
  SW: "South-west",
  W: "West",
  NW: "North-west",
  C: "City centre",
};

export function sectorOf(p: { lng: number; lat: number }): Sector {
  const here: LngLat = [p.lng, p.lat];
  if (distKm(AZAMGARH, here) < CENTRE_KM) return "C";
  return DIRECTIONS[Math.round(bearing(AZAMGARH, here) / 45) % 8];
}

export function inView(p: { lng: number; lat: number }): boolean {
  return distKm(AZAMGARH, [p.lng, p.lat]) <= VIEW_RADIUS_KM;
}

export function boundsOf(points: { lng: number; lat: number }[]): [LngLat, LngLat] {
  let w = Infinity, s = Infinity, e = -Infinity, n = -Infinity;
  for (const p of points) {
    if (p.lng < w) w = p.lng;
    if (p.lng > e) e = p.lng;
    if (p.lat < s) s = p.lat;
    if (p.lat > n) n = p.lat;
  }
  return [[w, s], [e, n]];
}
