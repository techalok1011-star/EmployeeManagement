import { useEffect } from "react";
import MapLibreGL from "maplibre-gl";
import { MapMarker, MarkerContent, useMap } from "@/components/ui/mapcn-marker-content";
import { AZAMGARH } from "@/lib/geo";
import type { ThemeName } from "@/hooks/useTheme";

/** Globe projection + atmosphere. Re-applied whenever the basemap style reloads (theme switch). */
export function GlobeProjection({ theme }: { theme: ThemeName }) {
  const { map } = useMap();
  useEffect(() => {
    if (!map) return;
    const apply = () => {
      try {
        map.setProjection({ type: "globe" });
      } catch {
        /* engine without globe support: stays a flat map */
      }
      try {
        map.setSky(
          theme === "dark"
            ? {
                "sky-color": "#0b1630",
                "horizon-color": "#3b5bdb",
                "fog-color": "#0a0e14",
                "sky-horizon-blend": 0.6,
                "horizon-fog-blend": 0.6,
                "fog-ground-blend": 0.4,
                "atmosphere-blend": ["interpolate", ["linear"], ["zoom"], 0, 1, 5, 1, 7, 0],
              }
            : {
                "sky-color": "#a9c4ff",
                "horizon-color": "#ffffff",
                "fog-color": "#eef1f8",
                "sky-horizon-blend": 0.5,
                "horizon-fog-blend": 0.5,
                "fog-ground-blend": 0.3,
                "atmosphere-blend": ["interpolate", ["linear"], ["zoom"], 0, 1, 5, 1, 7, 0],
              },
        );
      } catch {
        /* sky not supported: plain globe */
      }
    };
    if (map.isStyleLoaded()) apply();
    map.on("style.load", apply);
    return () => {
      map.off("style.load", apply);
    };
  }, [map, theme]);
  return null;
}

/** Zoom / compass / scale, restyled to the tokens in index.css. */
export function MapControls() {
  const { map } = useMap();
  useEffect(() => {
    if (!map) return;
    const nav = new MapLibreGL.NavigationControl({ visualizePitch: true });
    const scale = new MapLibreGL.ScaleControl({ unit: "metric" });
    map.addControl(nav, "top-right");
    map.addControl(scale, "bottom-right");
    return () => {
      map.removeControl(nav);
      map.removeControl(scale);
    };
  }, [map]);
  return null;
}

/** Azamgarh: the one warm point on the map. */
export function HubMarker() {
  return (
    <MapMarker longitude={AZAMGARH[0]} latitude={AZAMGARH[1]} anchor="left" offset={[-10, 0]}>
      <MarkerContent className="pointer-events-none">
        <span className="flex items-center gap-2">
          <span className="relative grid size-5 place-items-center">
            <span className="pin-pulse absolute size-5 rounded-full bg-hub/50" />
            <span className="relative size-3 rounded-full border-2 border-white bg-hub shadow" />
          </span>
          <span className="rounded-full bg-card/90 px-2.5 py-0.5 font-display text-[11px] font-semibold text-foreground shadow ring-1 ring-border backdrop-blur">
            Azamgarh
          </span>
        </span>
      </MarkerContent>
    </MapMarker>
  );
}
