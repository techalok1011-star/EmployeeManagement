/*
 * Keeps the hidden latitude/longitude on the "add collection" form fresh.
 *
 * The dashboard asks for the phone's location once when it loads, but the app often stays open (it is
 * installed on the home screen) while the person drives from the office to a party. A location taken at
 * page load would then pin the collection at the office. So the position is requested again as soon as
 * the person starts filling the form, and whenever the app comes back to the foreground.
 * Silent and optional: if location is denied or slow, the form still saves exactly as before.
 */
(function () {
    var form = document.querySelector('form[action$="/entries/add"]');
    if (!form || !navigator.geolocation) return;
    var lat = form.querySelector('input[name="latitude"]');
    var lng = form.querySelector('input[name="longitude"]');
    if (!lat || !lng) return;

    var last = 0, busy = false;
    function refresh(force) {
        var now = Date.now();
        if (busy || (!force && now - last < 45000)) return;
        busy = true;
        navigator.geolocation.getCurrentPosition(function (pos) {
            lat.value = pos.coords.latitude;
            lng.value = pos.coords.longitude;
            last = Date.now();
            busy = false;
        }, function () { busy = false; }, {enableHighAccuracy: true, timeout: 10000, maximumAge: 20000});
    }

    form.addEventListener('focusin', function () { refresh(false); });
    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'visible') refresh(true);
    });
})();
