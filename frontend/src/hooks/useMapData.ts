import { useCallback, useEffect, useRef, useState } from "react";
import { fetchMapData, type MapData, type Period } from "@/lib/api";

const EMPTY: MapData = { collections: [], logins: [], totalCollections: 0 };

/** Loads the collection / login locations for a period; keeps the previous data on screen while reloading. */
export function useMapData(period: Period) {
  const [data, setData] = useState<MapData>(EMPTY);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const abort = useRef<AbortController | null>(null);

  const load = useCallback(() => {
    abort.current?.abort();
    const ctl = new AbortController();
    abort.current = ctl;
    setLoading(true);
    setError(null);
    fetchMapData(period, ctl.signal)
      .then((d) => {
        if (!ctl.signal.aborted) setData(d);
      })
      .catch((e: unknown) => {
        if (ctl.signal.aborted) return;
        setError(e instanceof Error ? e.message : "The map data could not be loaded.");
      })
      .finally(() => {
        if (!ctl.signal.aborted) setLoading(false);
      });
  }, [period]);

  useEffect(() => {
    load();
    return () => abort.current?.abort();
  }, [load]);

  return { data, loading, error, reload: load };
}
