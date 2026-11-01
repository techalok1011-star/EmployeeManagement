import type { StyleSpecification } from "maplibre-gl";
import type { ThemeName } from "@/hooks/useTheme";

export type Basemap = "streets" | "dark" | "light";

export const BASEMAPS: { id: Basemap; label: string; hint: string; swatch: string }[] = [
  { id: "streets", label: "Street map", hint: "OpenStreetMap: roads, shops, landmarks", swatch: "linear-gradient(135deg,#f2efe9 0 45%,#aad3df 45% 60%,#fbd47b 60% 70%,#c8e6a9 70%)" },
  { id: "dark", label: "Dark", hint: "Quiet dark background", swatch: "linear-gradient(135deg,#0e1117,#232b3a)" },
  { id: "light", label: "Light", hint: "Quiet light background", swatch: "linear-gradient(135deg,#f5f6f8,#d9dde5)" },
];

const KEY = "paytrack-map-style"; // remembered per browser; if never chosen the map follows the page theme

export function readChosen(): Basemap | null {
  try {
    const v = localStorage.getItem(KEY);
    return v === "streets" || v === "dark" || v === "light" ? v : null;
  } catch {
    return null;
  }
}

export function saveChosen(v: Basemap) {
  try {
    localStorage.setItem(KEY, v);
  } catch {
    /* private mode: the choice just won't persist */
  }
}

/** Standard OpenStreetMap raster tiles. Light, attributed use only (see the OSM tile usage policy). */
export const OSM_STYLE: StyleSpecification = {
  version: 8,
  sources: {
    osm: {
      type: "raster",
      tiles: ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      tileSize: 256,
      maxzoom: 19,
      attribution: "© OpenStreetMap contributors",
    },
  },
  layers: [{ id: "osm", type: "raster", source: "osm" }],
};

/** Which theme the basemap itself should use (the street map ignores it). */
export function mapThemeFor(basemap: Basemap, pageTheme: ThemeName): ThemeName {
  return basemap === "dark" ? "dark" : basemap === "light" ? "light" : pageTheme;
}
