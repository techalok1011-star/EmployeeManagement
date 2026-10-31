import { useCallback, useEffect, useState } from "react";

export type ThemeName = "light" | "dark";
const KEY = "paytrack-theme"; // the same key PayTrack's other pages use, so the choice carries over

function read(): ThemeName {
  return document.documentElement.classList.contains("dark") ? "dark" : "light";
}

export function useTheme() {
  const [theme, setTheme] = useState<ThemeName>(read);

  useEffect(() => {
    document.documentElement.classList.toggle("dark", theme === "dark");
    const meta = document.querySelector('meta[name="theme-color"]');
    meta?.setAttribute("content", theme === "dark" ? "#0a0e14" : "#eef1f8");
  }, [theme]);

  const toggle = useCallback(() => {
    setTheme((t) => {
      const next: ThemeName = t === "dark" ? "light" : "dark";
      try {
        localStorage.setItem(KEY, next);
      } catch {
        /* private mode: the choice just won't persist */
      }
      return next;
    });
  }, []);

  return { theme, toggle };
}
