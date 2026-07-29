/* Service worker для Gym Tracker.
   Нужен, чтобы таймер отдыха показывался всплывающим уведомлением на Android
   (там new Notification() не работает) и чтобы приложение открывалось оффлайн. */
const CACHE = "gym-tracker-v1";
const ASSETS = ["./", "./index.html", "./manifest.json"];

self.addEventListener("install", event => {
	event.waitUntil(
		caches.open(CACHE).then(c => c.addAll(ASSETS).catch(() => {})).then(() => self.skipWaiting())
	);
});

self.addEventListener("activate", event => {
	event.waitUntil(
		caches.keys()
			.then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
			.then(() => self.clients.claim())
	);
});

self.addEventListener("fetch", event => {
	if (event.request.method !== "GET") return;
	event.respondWith(
		fetch(event.request)
			.then(res => {
				const copy = res.clone();
				caches.open(CACHE).then(c => c.put(event.request, copy)).catch(() => {});
				return res;
			})
			.catch(() => caches.match(event.request).then(hit => hit || caches.match("./index.html")))
	);
});

/* Тап по уведомлению — вернуться в тренировку */
self.addEventListener("notificationclick", event => {
	event.notification.close();
	event.waitUntil(
		self.clients.matchAll({type:"window", includeUncontrolled:true}).then(list => {
			for (const client of list){
				if ("focus" in client) return client.focus();
			}
			return self.clients.openWindow("./");
		})
	);
});
