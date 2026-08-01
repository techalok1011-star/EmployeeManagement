(function () {
    var KEY = 'paytrack-theme';
    var saved = localStorage.getItem(KEY);
    // Light is the CSS default (:root in theme.css); dark is an opt-in
    // override, applied only once a user has explicitly toggled to it.
    if (saved === 'dark') {
        document.documentElement.setAttribute('data-theme', 'dark');
    }

    // Full reload on toggle (not a live swap): a handful of pages draw Chart.js
    // canvases and other JS that reads colors once at render time, so a reload
    // is the simplest way to guarantee everything repaints in the new theme.
    window.toggleTheme = function () {
        var isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        if (isDark) {
            document.documentElement.removeAttribute('data-theme');
            localStorage.setItem(KEY, 'light');
        } else {
            document.documentElement.setAttribute('data-theme', 'dark');
            localStorage.setItem(KEY, 'dark');
        }
        location.reload();
    };

    document.addEventListener('DOMContentLoaded', function () {
        var isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        var icon = isDark ? '☀️' : '🌙';
        document.querySelectorAll('.theme-toggle-btn').forEach(function (btn) {
            btn.textContent = icon;
        });
    });

    // Modern mobile browsers (iOS and Android) can restore a killed installed-app
    // process from the back/forward cache instead of re-fetching from the network —
    // `Cache-Control: no-store` (which Spring Security sends on every response) no
    // longer reliably prevents this. A bfcache-restored page keeps whatever CSRF
    // token was baked into its HTML at original render time; if that page sits
    // closed for a while, the session (and its CSRF token) expires server-side, and
    // submitting the stale page's form gets rejected with a bare 403. Forcing a
    // reload on restore guarantees a fresh page — and a fresh, valid CSRF token —
    // every time, on every page, since this script is loaded everywhere.
    window.addEventListener('pageshow', function (e) {
        if (e.persisted) location.reload();
    });
})();
