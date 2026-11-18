// Porchlight's service worker: offline play.
// Network first, so a new version arrives as soon as it's online; the cache answers when it isn't.
// Every file the game loads (page, scripts, wasm, fonts, icons) is kept as it passes through.
const CACHE = 'porchlight-v1';
const SHELL = ['./', 'index.html', 'porchlight.js', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/apple-touch-icon.png'];

self.addEventListener('install', (event) => {
    event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)).catch(() => {}));
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
            .then(() => self.clients.claim()),
    );
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) return;
    event.respondWith(
        fetch(request)
            .then((response) => {
                if (response.ok) {
                    const copy = response.clone();
                    caches.open(CACHE).then((cache) => cache.put(request, copy));
                }
                return response;
            })
            .catch(() => caches.match(request, { ignoreSearch: true })
                .then((hit) => hit || (request.mode === 'navigate' ? caches.match('index.html') : undefined))
                .then((hit) => hit || Response.error())),
    );
});
