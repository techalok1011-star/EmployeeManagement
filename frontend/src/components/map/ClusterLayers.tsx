import { useEffect, useMemo, useState } from "react";
import Supercluster from "supercluster";
import { MapMarker, MarkerContent, MarkerTooltip, useMap } from "@/components/ui/mapcn-marker-content";
import { cn } from "@/lib/utils";
import { fmtDate, inr } from "@/lib/format";
import type { Login } from "@/lib/api";
import type { Pt } from "@/lib/points";

type Bbox = [number, number, number, number];

/**
 * Clusters points with supercluster and returns only what is inside the current viewport, so thousands of
 * collections stay cheap: the map never holds more than a few dozen DOM markers at once.
 */
function useClusters<T extends { lng: number; lat: number }>(points: T[], radius = 46) {
  const { map } = useMap();
  const [view, setView] = useState<{ bbox: Bbox; zoom: number } | null>(null);

  const index = useMemo(() => {
    const sc = new Supercluster<{ i: number }>({ radius, maxZoom: 15 });
    sc.load(
      points.map((p, i) => ({
        type: "Feature" as const,
        properties: { i },
        geometry: { type: "Point" as const, coordinates: [p.lng, p.lat] },
      })),
    );
    return sc;
  }, [points, radius]);

  useEffect(() => {
    if (!map) return;
    const update = () => {
      const b = map.getBounds();
      const clamp = (n: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, n));
      const zoom = Math.floor(map.getZoom());
      const bbox: Bbox =
        zoom < 3
          ? [-180, -85, 180, 85]
          : [clamp(b.getWest(), -180, 180), clamp(b.getSouth(), -85, 85), clamp(b.getEast(), -180, 180), clamp(b.getNorth(), -85, 85)];
      setView({ bbox, zoom });
    };
    update();
    map.on("moveend", update);
    map.on("load", update);
    return () => {
      map.off("moveend", update);
      map.off("load", update);
    };
  }, [map]);

  const items = useMemo(() => (view ? index.getClusters(view.bbox, view.zoom) : []), [index, view]);
  return { items, index };
}

function ClusterBubble({ count, tone }: { count: number; tone: "primary" | "muted" }) {
  const size = count < 10 ? 38 : count < 50 ? 46 : count < 200 ? 54 : 62;
  return (
    <span
      className={cn(
        "grid place-items-center rounded-full border-2 font-mono text-[13px] font-medium shadow-lg transition-transform hover:scale-110",
        tone === "primary"
          ? "border-white/90 bg-primary/90 text-primary-foreground"
          : "border-white/60 bg-muted-foreground/70 text-background",
      )}
      style={{ width: size, height: size }}
    >
      {count}
    </span>
  );
}

export function CollectionLayer({
  points,
  selectedId,
  onSelect,
}: {
  points: Pt[];
  selectedId: number | null;
  onSelect: (p: Pt) => void;
}) {
  const { map } = useMap();
  const { items, index } = useClusters(points);

  return (
    <>
      {items.map((f) => {
        const [lng, lat] = f.geometry.coordinates;
        const props = f.properties as { cluster?: boolean; cluster_id?: number; point_count?: number; i?: number };
        if (props.cluster) {
          return (
            <MapMarker
              key={`c${props.cluster_id}`}
              longitude={lng}
              latitude={lat}
              onClick={() => {
                const z = index.getClusterExpansionZoom(props.cluster_id as number);
                map?.easeTo({ center: [lng, lat], zoom: Math.min(z + 0.4, 17), duration: 700 });
              }}
            >
              <MarkerContent>
                <ClusterBubble count={props.point_count ?? 0} tone="primary" />
              </MarkerContent>
            </MapMarker>
          );
        }
        const p = points[props.i as number];
        if (!p) return null;
        const selected = p.id === selectedId;
        return (
          <MapMarker key={`p${p.id}`} longitude={p.lng} latitude={p.lat} onClick={() => onSelect(p)}>
            <MarkerContent>
              <span className="grid size-11 place-items-center" data-pin={p.party}>
                {selected && <span className="pin-pulse absolute size-5 rounded-full bg-hub/60" />}
                <span
                  className={cn(
                    "relative block rounded-full border-2 border-white shadow-lg transition-transform hover:scale-125",
                    selected ? "size-5 ring-4 ring-hub" : "size-3.5",
                  )}
                  style={{ background: p.color }}
                />
              </span>
            </MarkerContent>
            <MarkerTooltip offset={14}>
              <span className="block max-w-56 truncate font-medium">{p.party}</span>
              <span className="num block font-mono text-[11px] opacity-80">
                {inr(p.amount)} · {fmtDate(p.date)}
              </span>
            </MarkerTooltip>
          </MapMarker>
        );
      })}
    </>
  );
}

export function LoginLayer({ logins }: { logins: Login[] }) {
  const { map } = useMap();
  const pts = useMemo(() => logins.map((l) => ({ ...l })), [logins]);
  const { items, index } = useClusters(pts, 34);

  return (
    <>
      {items.map((f) => {
        const [lng, lat] = f.geometry.coordinates;
        const props = f.properties as { cluster?: boolean; cluster_id?: number; point_count?: number; i?: number };
        if (props.cluster) {
          return (
            <MapMarker
              key={`lc${props.cluster_id}`}
              longitude={lng}
              latitude={lat}
              onClick={() => {
                const z = index.getClusterExpansionZoom(props.cluster_id as number);
                map?.easeTo({ center: [lng, lat], zoom: Math.min(z + 0.4, 17), duration: 700 });
              }}
            >
              <MarkerContent>
                <ClusterBubble count={props.point_count ?? 0} tone="muted" />
              </MarkerContent>
            </MapMarker>
          );
        }
        const l = pts[props.i as number];
        if (!l) return null;
        return (
          <MapMarker key={`l${props.i}`} longitude={l.lng} latitude={l.lat}>
            <MarkerContent>
              <span className="grid size-8 place-items-center">
                {/* fixed colours (not theme tokens) so the ring stays visible on dark, light AND street basemaps */}
                <span className="block size-3.5 rounded-full border-2 border-slate-700 bg-white/80 shadow-[0_0_0_1.5px_rgba(255,255,255,0.7)]" />
              </span>
            </MarkerContent>
            <MarkerTooltip offset={12}>
              <span className="block font-medium">{l.by}</span>
              <span className="block font-mono text-[11px] opacity-80">
                Signed in · {l.role.toLowerCase()} · {fmtDate(l.at)}
              </span>
            </MarkerTooltip>
          </MapMarker>
        );
      })}
    </>
  );
}
