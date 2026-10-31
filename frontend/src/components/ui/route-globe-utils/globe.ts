/**
 * Helpers for <RouteGlobe />: unit-sphere maths and a dotted land mask.
 *
 * Convention (x right, y up, z towards the viewer):
 *   toVec(lon, lat)  -> [cos(lat) sin(lon), sin(lat), cos(lat) cos(lon)]
 *   rotate(v, yaw, tilt) turns the globe so longitude `yaw` faces the viewer, then leans the north pole
 *   towards the viewer by `tilt`. A point is on the near side when the returned z > 0.
 */
import { geoEquirectangular, geoPath } from "d3-geo";
import { feature } from "topojson-client";
import land110 from "world-atlas/land-110m.json";

export type Vec3 = [number, number, number];

const RAD = Math.PI / 180;

export function toVec(lon: number, lat: number): Vec3 {
  const lo = lon * RAD,
    la = lat * RAD;
  return [Math.cos(la) * Math.sin(lo), Math.sin(la), Math.cos(la) * Math.cos(lo)];
}

export function rotate(v: Vec3, yaw: number, tilt: number): Vec3 {
  const cy = Math.cos(yaw),
    sy = Math.sin(yaw);
  const x1 = v[0] * cy - v[2] * sy;
  const z1 = v[0] * sy + v[2] * cy;
  const ct = Math.cos(tilt),
    st = Math.sin(tilt);
  return [x1, v[1] * ct - z1 * st, v[1] * st + z1 * ct];
}

/** Spherical interpolation along the great circle from a to b (t in 0..1). */
export function slerp(a: Vec3, b: Vec3, t: number): Vec3 {
  const d = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
  const om = Math.acos(d);
  if (om < 1e-6) return a;
  const s = Math.sin(om);
  const k1 = Math.sin((1 - t) * om) / s,
    k2 = Math.sin(t * om) / s;
  return [k1 * a[0] + k2 * b[0], k1 * a[1] + k2 * b[1], k1 * a[2] + k2 * b[2]];
}

// ── land mask ────────────────────────────────────────────────────────────
const MASK_W = 1440,
  MASK_H = 720;
let mask: Uint8ClampedArray | null = null;

function landMask(): Uint8ClampedArray {
  if (mask) return mask;
  const cv = document.createElement("canvas");
  cv.width = MASK_W;
  cv.height = MASK_H;
  const ctx = cv.getContext("2d", { willReadFrequently: true })!;
  const projection = geoEquirectangular().fitSize([MASK_W, MASK_H], { type: "Sphere" });
  const path = geoPath(projection, ctx);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const topo = land110 as any;
  const land = feature(topo, topo.objects.land);
  ctx.fillStyle = "#fff";
  ctx.beginPath();
  path(land as never);
  ctx.fill();
  const px = ctx.getImageData(0, 0, MASK_W, MASK_H).data;
  const m = new Uint8ClampedArray(MASK_W * MASK_H);
  for (let i = 0; i < m.length; i++) m[i] = px[i * 4 + 3];
  mask = m;
  return m;
}

/**
 * ~`count` evenly spaced points on the sphere, kept only where there is land.
 * Returned as a flat [x, y, z, x, y, z, ...] Float32Array of unit vectors.
 */
export function landDots(count: number): Float32Array {
  const m = landMask();
  const out: number[] = [];
  const golden = Math.PI * (3 - Math.sqrt(5));
  for (let i = 0; i < count; i++) {
    const y = 1 - (2 * (i + 0.5)) / count; // 1 .. -1
    const rr = Math.sqrt(1 - y * y);
    const theta = golden * i;
    const x = Math.sin(theta) * rr,
      z = Math.cos(theta) * rr;
    const lat = Math.asin(y) / RAD;
    const lon = Math.atan2(x, z) / RAD;
    const px = Math.min(MASK_W - 1, Math.max(0, Math.floor(((lon + 180) / 360) * MASK_W)));
    const py = Math.min(MASK_H - 1, Math.max(0, Math.floor(((90 - lat) / 180) * MASK_H)));
    if (m[py * MASK_W + px] > 127) out.push(x, y, z);
  }
  return new Float32Array(out);
}

export type GlobeView = { cx: number; cy: number; r: number; yaw: number; tilt: number };

/** Draws the glow, the sphere and the land dots that face the viewer. */
export function drawGlobe(
  ctx: CanvasRenderingContext2D,
  dots: Float32Array,
  view: GlobeView,
  landColor: string,
  dotSize: number,
  glow: string,
) {
  const { cx, cy, r, yaw, tilt } = view;

  // soft atmosphere
  const halo = ctx.createRadialGradient(cx, cy, r * 0.92, cx, cy, r * 1.45);
  halo.addColorStop(0, glow);
  halo.addColorStop(1, "rgb(0 0 0 / 0)");
  ctx.globalAlpha = 1;
  ctx.fillStyle = halo;
  ctx.beginPath();
  ctx.arc(cx, cy, r * 1.45, 0, Math.PI * 2);
  ctx.fill();

  // the sphere itself: a faint disc and a rim so the dark side still reads as a ball
  const body = ctx.createRadialGradient(cx - r * 0.3, cy - r * 0.35, r * 0.1, cx, cy, r);
  body.addColorStop(0, "rgb(255 255 255 / 0.05)");
  body.addColorStop(1, "rgb(255 255 255 / 0.012)");
  ctx.fillStyle = body;
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = "rgb(255 255 255 / 0.1)";
  ctx.lineWidth = 1;
  ctx.stroke();

  // land dots, brighter towards the centre of the disc
  ctx.fillStyle = landColor;
  const cyw = Math.cos(yaw),
    syw = Math.sin(yaw),
    ct = Math.cos(tilt),
    st = Math.sin(tilt);
  const half = dotSize / 2;
  for (let i = 0; i < dots.length; i += 3) {
    const x = dots[i],
      y = dots[i + 1],
      z = dots[i + 2];
    const x1 = x * cyw - z * syw;
    const z1 = x * syw + z * cyw;
    const z2 = y * st + z1 * ct;
    if (z2 <= 0) continue;
    const y2 = y * ct - z1 * st;
    ctx.globalAlpha = 0.25 + 0.75 * z2;
    ctx.fillRect(cx + x1 * r - half, cy - y2 * r - half, dotSize, dotSize);
  }
  ctx.globalAlpha = 1;
}
