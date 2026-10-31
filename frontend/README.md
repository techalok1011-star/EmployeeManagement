# PayTrack Collection Map (React)

The admin **Collection Map** (`/admin/collection-map`) is a React + TypeScript + Tailwind v4 app (shadcn-style
`components/ui`, MapLibre via the `mapcn` component, the `route-globe` canvas globe). Everything else in PayTrack
stays server-rendered Thymeleaf; this is a self-contained "island" that talks to Spring Boot over JSON:

| Piece | Where |
|---|---|
| Data API (ADMIN only) | `CollectionMapController` -> `GET /admin/collection-map/data?from=YYYY-MM-DD` |
| Page shell | generated `src/main/resources/templates/admin/collection-map.html` |
| Compiled app | `src/main/resources/static/js/map-app/` (served from `/js/map-app/`) |

## The compiled output is committed

Render builds with Maven only (`Dockerfile`), so **Node is never needed on the server**. After changing anything
in `frontend/src`, rebuild and commit the result:

```bash
cd frontend
npm install        # first time only
npm run build      # type-checks, bundles into ../src/main/resources/static/js/map-app,
                   # and rewrites templates/admin/collection-map.html with the new asset names
```

Then restart the Spring app (Thymeleaf caches templates) and commit `frontend/`, the `static/js/map-app/` folder
and the regenerated template together.

## Layout

```
src/
  App.tsx                      stage machine: globe intro -> fly-in -> map, filters, selection
  components/ui/               shadcn-style primitives + the two provided components
    mapcn-marker-content.tsx   Map, MapMarker, MarkerContent, MarkerTooltip (MapLibre)
    route-globe.tsx            canvas globe  (+ route-globe-utils/globe.ts, global.css)
  components/map/              ClusterLayers, DirectionRose, Panel, TopBar, IntroGlobe, MapChrome
  lib/                         api (types + fetch), geo (distance/bearing), points, format
```

Theme is shared with the rest of PayTrack through `localStorage["paytrack-theme"]`.
