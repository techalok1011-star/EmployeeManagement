import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Map as MapView, type MapRef } from "@/components/ui/mapcn-marker-content";
import { CollectionLayer, LoginLayer } from "@/components/map/ClusterLayers";
import { GlobeProjection, HubMarker, MapControls } from "@/components/map/MapChrome";
import { IntroGlobe } from "@/components/map/IntroGlobe";
import { Panel, type SheetState } from "@/components/map/Panel";
import { TopBar } from "@/components/map/TopBar";
import { useMapData } from "@/hooks/useMapData";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import { useTheme } from "@/hooks/useTheme";
import type { Period } from "@/lib/api";
import { OSM_STYLE, mapThemeFor, readChosen, saveChosen, type Basemap } from "@/lib/basemap";
import { AZAMGARH, boundsOf, inView, type Sector } from "@/lib/geo";
import { PALETTE, bucketBySector, collectorsOf, groupParties, type PartyRow, type Pt } from "@/lib/points";

type Stage = "globe" | "leaving" | "map";
const DISTRICT_ZOOM = 9.4;

export default function App() {
  const { theme, toggle: toggleTheme } = useTheme();
  const desktop = useMediaQuery("(min-width: 900px)");
  const reduced = useMemo(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches, []);

  // Map style: what the person picked (remembered), otherwise it follows the page theme
  const [chosen, setChosen] = useState<Basemap | null>(readChosen);
  const basemap: Basemap = chosen ?? theme;
  const mapTheme = mapThemeFor(basemap, theme);
  const mapStyles = useMemo(() => (basemap === "streets" ? { light: OSM_STYLE, dark: OSM_STYLE } : undefined), [basemap]);
  const pickBasemap = useCallback((b: Basemap) => {
    setChosen(b);
    saveChosen(b);
  }, []);

  const [period, setPeriod] = useState<Period>("fy");
  const { data, loading, error, reload } = useMapData(period);

  const mapRef = useRef<MapRef>(null);
  const [stage, setStage] = useState<Stage>(() => (new URLSearchParams(location.search).get("intro") === "0" ? "map" : "globe"));
  const [sheet, setSheet] = useState<SheetState>("peek");
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  // sign-in locations are on by default: until collections start carrying a location they are the data
  const [showLogins, setShowLogins] = useState(true);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [query, setQuery] = useState("");

  // ── derived data ──────────────────────────────────────────────────────────
  const collectors = useMemo(() => collectorsOf(data.collections), [data.collections]);
  const colorOf = useMemo(() => new Map(collectors.map((c) => [c.user, c.color])), [collectors]);
  const points: Pt[] = useMemo(
    () => data.collections.filter((c) => !hidden.has(c.user)).map((c) => ({ ...c, color: colorOf.get(c.user) ?? PALETTE[0] })),
    [data.collections, hidden, colorOf],
  );
  const near = useMemo(() => points.filter(inView), [points]);
  // what the map frames: the collections, plus the sign-ins around Azamgarh while that layer is on
  const framed = useMemo(
    () => (showLogins ? [...near, ...data.logins.filter(inView)] : near),
    [near, showLogins, data.logins],
  );
  const counts = useMemo(() => {
    const b = bucketBySector(near);
    return Object.fromEntries(Object.entries(b).map(([k, v]) => [k, v.length])) as Record<Sector, number>;
  }, [near]);
  const parties = useMemo(() => {
    const all = groupParties(near);
    const q = query.trim().toLowerCase();
    return q ? all.filter((r) => r.name.toLowerCase().includes(q)) : all;
  }, [near, query]);
  const selected = useMemo(() => points.find((p) => p.id === selectedId) ?? null, [points, selectedId]);

  // a new period brings a new set of collectors: start from a clean slate
  useEffect(() => {
    setHidden(new Set());
    setSelectedId(null);
  }, [data.collections]);

  // ── map actions ───────────────────────────────────────────────────────────
  /** Space the panel / top bar cover, so what we zoom to stays in the visible part of the map. */
  const viewPadding = useCallback(() => {
    const h = window.innerHeight;
    return desktop
      ? { top: 96, left: 430, right: 64, bottom: 56 }
      : { top: 130, left: 36, right: 36, bottom: Math.round(h * 0.56) };
  }, [desktop]);

  const fitTo = useCallback(
    (list: { lng: number; lat: number }[], animate = true, maxZoom = 13) => {
      const m = mapRef.current;
      if (!m || list.length === 0) return;
      if (list.length === 1) {
        m.easeTo({ center: [list[0].lng, list[0].lat], zoom: Math.max(13, m.getZoom()), duration: animate ? 1000 : 0, padding: viewPadding() });
        return;
      }
      m.fitBounds(boundsOf(list), { padding: viewPadding(), maxZoom, duration: animate ? 1400 : 0 });
    },
    [viewPadding],
  );

  const enter = useCallback(
    (skip = false) => {
      const m = mapRef.current;
      if (!m) return setStage("map");
      if (skip || reduced) {
        m.jumpTo({ center: AZAMGARH, zoom: DISTRICT_ZOOM, pitch: 0, bearing: 0 });
        return setStage("map");
      }
      setStage("leaving");
      m.flyTo({ center: AZAMGARH, zoom: DISTRICT_ZOOM, pitch: 0, bearing: 0, duration: 6200, curve: 1.5, essential: true });
      m.once("moveend", () => setStage("map"));
    },
    [reduced],
  );

  const handleEnter = useCallback(() => enter(false), [enter]);

  const replay = useCallback(() => {
    const m = mapRef.current;
    m?.stop();
    m?.jumpTo({ center: AZAMGARH, zoom: 1.5, pitch: 0, bearing: 0 });
    setSelectedId(null);
    setStage("globe");
  }, []);

  // once we have landed on the district (and the data is in), frame the pins
  useEffect(() => {
    if (stage === "map" && !loading) fitTo(framed, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stage, loading, data.collections]);

  // arriving with ?intro=0 skips the globe entirely
  useEffect(() => {
    if (stage === "map" && mapRef.current && mapRef.current.getZoom() < 4) {
      mapRef.current.jumpTo({ center: AZAMGARH, zoom: DISTRICT_ZOOM });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const select = useCallback(
    (p: Pt) => {
      setSelectedId(p.id);
      if (!desktop) setSheet("half");
      const m = mapRef.current;
      m?.easeTo({ center: [p.lng, p.lat], zoom: Math.max(m.getZoom(), 14), duration: 700, padding: viewPadding() });
    },
    [desktop, viewPadding],
  );

  const pickParty = useCallback(
    (r: PartyRow) => {
      setSelectedId(r.last.id);
      if (!desktop) setSheet("half");
      mapRef.current?.flyTo({ center: [r.lng, r.lat], zoom: 15, duration: 1200, essential: true, padding: viewPadding() });
    },
    [desktop, viewPadding],
  );

  const pickSector = useCallback(
    (s: Sector) => {
      const list = bucketBySector(near)[s];
      if (!list.length) return;
      if (!desktop) setSheet("peek");
      fitTo(list, true, s === "C" ? 14 : 13);
    },
    [near, desktop, fitTo],
  );

  const toggleCollector = useCallback((user: string) => {
    setHidden((h) => {
      const n = new Set(h);
      if (n.has(user)) n.delete(user);
      else n.add(user);
      return n;
    });
  }, []);

  // Esc clears the selection
  useEffect(() => {
    const on = (e: KeyboardEvent) => e.key === "Escape" && setSelectedId(null);
    window.addEventListener("keydown", on);
    return () => window.removeEventListener("keydown", on);
  }, []);

  return (
    <div className="relative h-full w-full overflow-hidden bg-background">
      <div className="absolute inset-0">
        <MapView ref={mapRef} theme={mapTheme} styles={mapStyles} center={AZAMGARH} zoom={1.5} maxPitch={60}>
          <GlobeProjection theme={mapTheme} />
          <MapControls />
          <HubMarker />
          <CollectionLayer points={points} selectedId={selectedId} onSelect={select} />
          {showLogins && <LoginLayer logins={data.logins} />}
        </MapView>
      </div>

      <TopBar theme={theme} onToggleTheme={toggleTheme} period={period} onPeriod={setPeriod} onReplay={replay} basemap={basemap} onBasemap={pickBasemap} />

      <Panel
        desktop={desktop}
        loading={loading}
        error={error}
        onRetry={reload}
        located={data.collections.length}
        total={data.totalCollections}
        outside={points.length - near.length}
        collectors={collectors}
        hidden={hidden}
        onToggleCollector={toggleCollector}
        counts={counts}
        onPickSector={pickSector}
        parties={parties}
        query={query}
        onQuery={setQuery}
        onPickParty={pickParty}
        selected={selected}
        onClearSelected={() => setSelectedId(null)}
        showLogins={showLogins}
        onShowLogins={setShowLogins}
        loginCount={data.logins.length}
        sheet={sheet}
        onSheet={setSheet}
      />

      {stage !== "map" && (
        <IntroGlobe theme={theme} leaving={stage === "leaving"} located={data.collections.length} loading={loading} onEnter={handleEnter} />
      )}
    </div>
  );
}
