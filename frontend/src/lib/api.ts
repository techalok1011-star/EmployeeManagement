export type Collection = {
  id: number;
  party: string;
  amount: number;
  mode: string | null;
  date: string | null;
  vch: string | null;
  by: string;
  user: string;
  lat: number;
  lng: number;
  receipt: boolean;
};

export type Login = {
  by: string;
  user: string;
  role: string;
  at: string | null;
  lat: number;
  lng: number;
};

export type MapData = {
  collections: Collection[];
  logins: Login[];
  totalCollections: number;
};

export type Period = "month" | "30" | "fy" | "all";

export const PERIODS: { id: Period; label: string }[] = [
  { id: "month", label: "This month" },
  { id: "30", label: "30 days" },
  { id: "fy", label: "This FY" },
  { id: "all", label: "All time" },
];

const pad = (n: number) => String(n).padStart(2, "0");
const iso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

export function rangeFor(period: Period): { from?: string } {
  const now = new Date();
  if (period === "month") return { from: iso(new Date(now.getFullYear(), now.getMonth(), 1)) };
  if (period === "30") return { from: iso(new Date(now.getTime() - 29 * 864e5)) };
  if (period === "fy") {
    // Indian financial year starts 1 April
    const y = now.getMonth() >= 3 ? now.getFullYear() : now.getFullYear() - 1;
    return { from: iso(new Date(y, 3, 1)) };
  }
  return {};
}

export async function fetchMapData(period: Period, signal?: AbortSignal): Promise<MapData> {
  const { from } = rangeFor(period);
  const res = await fetch(`/admin/collection-map/data${from ? `?from=${from}` : ""}`, {
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    signal,
  });
  const type = res.headers.get("content-type") ?? "";
  // An expired session answers with a redirect to the sign-in page, which fetch follows and hands back as HTML.
  if (res.status === 401 || res.status === 403 || res.redirected || (res.ok && !type.includes("json"))) {
    throw new Error("Your session has ended. Sign in again to see the map.");
  }
  if (!res.ok) throw new Error(`The server answered ${res.status}.`);
  const d = await res.json();
  return {
    totalCollections: Number(d.totalCollections) || 0,
    collections: (d.collections ?? []).map((c: Record<string, unknown>) => ({
      ...c,
      amount: Number(c.amount),
      lat: Number(c.lat),
      lng: Number(c.lng),
    })) as Collection[],
    logins: (d.logins ?? []).map((l: Record<string, unknown>) => ({
      ...l,
      lat: Number(l.lat),
      lng: Number(l.lng),
    })) as Login[],
  };
}
