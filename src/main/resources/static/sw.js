// Minimal service worker - exists mainly to satisfy PWA installability requirements.
// Deliberately does NOT cache HTML pages or API responses: this app shows live
// financial data (invoices, payments, outstanding balances), so serving a stale
// cached page instead of hitting the network would be actively misleading.
// Only the static app-shell assets (icons, manifest) are safe to cache.

// Bumped to v2: manifest.json's start_url changed to /login (was /employee/dashboard) -
// browsers only re-check a service worker for updates when its own bytes change, so without
// bumping this, a browser that already cached the old manifest.json would keep serving it
// forever, even after "Add to Home Screen" is redone. Changing CACHE_NAME forces byte-diff
// detection, which reruns install/activate: install re-fetches manifest.json fresh, and the
// existing activate handler already deletes any cache not matching the current CACHE_NAME.
const CACHE_NAME = 'paytrack-shell-v2';
const SHELL_ASSETS = [
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/manifest.json'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL_ASSETS))
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((names) =>
      Promise.all(names.filter((n) => n !== CACHE_NAME).map((n) => caches.delete(n)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  const isShellAsset = SHELL_ASSETS.some((a) => url.pathname === a);

  if (isShellAsset) {
    event.respondWith(
      caches.match(event.request).then((cached) => cached || fetch(event.request))
    );
    return;
  }

  // Everything else (pages, forms, API calls) goes straight to the network -
  // no offline fallback, since showing stale financial data would be worse
  // than a normal "you're offline" browser error.
});
