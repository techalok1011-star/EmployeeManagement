// Minimal service worker - exists mainly to satisfy PWA installability requirements.
// Deliberately does NOT cache HTML pages or API responses: this app shows live
// financial data (invoices, payments, outstanding balances), so serving a stale
// cached page instead of hitting the network would be actively misleading.
// Only the static app-shell assets (icons, manifest) are safe to cache.

const CACHE_NAME = 'paytrack-shell-v1';
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
