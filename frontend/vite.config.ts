import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

// The compiled app is served by Spring Boot from /js/map-app/ (already permitted in SecurityConfig).
// `npm run build` writes it straight into Spring's static folder and turns index.html into the
// Thymeleaf template admin/collection-map.html (see scripts/finalize.mjs), so the Render/Docker build
// stays Maven-only - the compiled output is committed, Node is never needed on the server.
export default defineConfig({
  base: "/js/map-app/",
  plugins: [react(), tailwindcss()],
  resolve: { alias: { "@": path.resolve(__dirname, "src") } },
  build: {
    outDir: "../src/main/resources/static/js/map-app",
    emptyOutDir: true,
    chunkSizeWarningLimit: 2500,
    sourcemap: false,
  },
  server: { port: 5173 },
});
