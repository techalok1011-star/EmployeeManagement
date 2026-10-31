const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** ₹1,23,456 - Indian digit grouping, matching the rest of PayTrack. */
export function inr(n: number): string {
  return "₹" + Number(n).toLocaleString("en-IN", { maximumFractionDigits: 2 });
}

/** "24 Oct 2026" from an ISO date or date-time. */
export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  const [y, m, d] = String(iso).slice(0, 10).split("-");
  return `${d} ${MONTHS[Number(m) - 1] ?? ""} ${y}`;
}

export function fmtMode(mode: string | null | undefined): string {
  if (!mode) return "—";
  return mode.replace(/_/g, " ").toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

export function fmtDistance(metres: number): string {
  return metres < 1000 ? `${Math.round(metres)} m` : `${(metres / 1000).toFixed(1)} km`;
}
