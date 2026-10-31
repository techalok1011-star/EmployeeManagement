/*
 * Global "something is happening" feedback, loaded on every page.
 *
 * On a phone with a slow connection (Render's free tier can take several seconds to answer) a tap that
 * shows nothing feels like a frozen app. This shows a full-screen spinner for:
 *   - any same-site link tap (Edit, Cancel, Back to list, filters, ...)
 *   - any form submit (Save Changes, Delete, Filter, ...) with "Saving..." / "Deleting..." text
 *   - form.submit() calls from inline onchange handlers (date / month pickers)
 *   - browser Back / Forward restoring a cached page (theme.js reloads it to get a fresh CSRF token)
 * After 7s the text changes to a "slow connection" note; after 45s it gives up, hides itself and says so,
 * so the page is never locked behind a spinner forever.
 * It also turns the server's success / error flash messages into a toast pinned to the top of the
 * screen, because on a long page the in-flow message is easily scrolled out of view.
 * Other scripts can use window.PayTrackLoading.show(text) / .hide() / .toast(text, kind).
 */
(function () {
    if (window.PayTrackLoading) return;

    var SLOW_MS = 7000, GIVE_UP_MS = 45000, UNLOAD_HIDE_MS = 8000, TOAST_MS = 7000;
    var el, textEl, slowTimer, giveUpTimer, unloadTimer, visible = false;

    function build() {
        if (el || !document.body) return;
        var css = document.createElement('style');
        css.textContent =
            '#ptLoader{display:none;position:fixed;inset:0;z-index:100000;background:var(--backdrop,rgba(15,23,42,.6));' +
            'backdrop-filter:blur(3px);-webkit-backdrop-filter:blur(3px);flex-direction:column;align-items:center;' +
            'justify-content:center;gap:14px;padding:24px;text-align:center}' +
            '#ptLoader.show{display:flex}' +
            '#ptLoader .pt-ld-spin{width:38px;height:38px;border:3px solid rgba(255,255,255,.25);' +
            'border-top-color:var(--accent,#3b5bdb);border-radius:50%;animation:ptldspin .7s linear infinite}' +
            '#ptLoader .pt-ld-text{font-family:\'Syne\',\'DM Sans\',sans-serif;font-size:14.5px;font-weight:600;color:#e2e8f0;max-width:300px}' +
            '@keyframes ptldspin{to{transform:rotate(360deg)}}' +
            '.pt-toast-wrap{position:fixed;top:10px;left:0;right:0;z-index:100001;display:flex;flex-direction:column;' +
            'align-items:center;gap:8px;padding:0 12px;pointer-events:none}' +
            '.pt-toast{pointer-events:auto;max-width:520px;width:100%;box-sizing:border-box;padding:12px 14px;border-radius:12px;' +
            'font-family:\'DM Sans\',sans-serif;font-size:14px;font-weight:500;line-height:1.35;color:#fff;' +
            'box-shadow:0 8px 24px rgba(0,0,0,.28);display:flex;gap:10px;align-items:flex-start;' +
            'animation:pttoastin .22s ease-out}' +
            '.pt-toast.ok{background:#15803d}.pt-toast.err{background:#b91c1c}.pt-toast.info{background:#1d4ed8}' +
            '.pt-toast .pt-x{margin-left:auto;opacity:.8;font-size:16px;line-height:1;padding:0 2px}' +
            '@keyframes pttoastin{from{transform:translateY(-12px);opacity:0}to{transform:none;opacity:1}}';
        document.head.appendChild(css);
        el = document.createElement('div');
        el.id = 'ptLoader';
        el.setAttribute('role', 'status');
        el.setAttribute('aria-live', 'polite');
        el.innerHTML = '<div class="pt-ld-spin"></div><div class="pt-ld-text"></div>';
        document.body.appendChild(el);
        textEl = el.querySelector('.pt-ld-text');
    }

    function clearTimers() {
        clearTimeout(slowTimer); clearTimeout(giveUpTimer); clearTimeout(unloadTimer);
    }

    function hide() {
        clearTimers();
        visible = false;
        if (el) el.classList.remove('show');
    }

    function show(text, opts) {
        build();
        if (!el) return;
        clearTimers();
        textEl.textContent = text || 'Loading…';
        el.classList.add('show');
        visible = true;
        if (opts && opts.autoHide) {
            // navigation we cannot see the end of (might be a file download that never unloads the page)
            unloadTimer = setTimeout(hide, opts.autoHide);
            return;
        }
        slowTimer = setTimeout(function () {
            if (textEl) textEl.textContent = 'Still working… slow connection, please wait';
        }, SLOW_MS);
        giveUpTimer = setTimeout(function () {
            hide();
            toast('This is taking longer than expected. Please check your internet connection and try again.', 'err');
        }, GIVE_UP_MS);
    }

    var wrap;
    function toast(text, kind) {
        if (!document.body) return;
        if (!wrap) {
            build();
            wrap = document.createElement('div');
            wrap.className = 'pt-toast-wrap';
            document.body.appendChild(wrap);
        }
        var t = document.createElement('div');
        t.className = 'pt-toast ' + (kind === 'err' ? 'err' : kind === 'info' ? 'info' : 'ok');
        t.setAttribute('role', kind === 'err' ? 'alert' : 'status');
        var icon = kind === 'err' ? '⚠' : kind === 'info' ? 'ℹ' : '✓';
        var span = document.createElement('span');
        span.textContent = text;
        var x = document.createElement('span');
        x.className = 'pt-x';
        x.textContent = '✕';
        t.appendChild(document.createTextNode(icon + ' '));
        t.appendChild(span);
        t.appendChild(x);
        var gone = false;
        function close() { if (gone) return; gone = true; if (t.parentNode) t.parentNode.removeChild(t); }
        t.addEventListener('click', close);
        wrap.appendChild(t);
        setTimeout(close, kind === 'err' ? TOAST_MS * 2 : TOAST_MS);
    }

    window.PayTrackLoading = {show: show, hide: hide, toast: toast};

    // ── links ───────────────────────────────────────────────────────────────
    document.addEventListener('click', function (e) {
        if (e.defaultPrevented || e.button > 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
        var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
        if (!a) return;
        if ((a.target && a.target !== '_self') || a.hasAttribute('download') || a.hasAttribute('data-no-loader')) return;
        var h = a.getAttribute('href');
        if (!h || h.charAt(0) === '#' || /^(javascript|mailto|tel|sms|whatsapp):/i.test(h)) return;
        var u;
        try { u = new URL(a.href, location.href); } catch (x) { return; }
        if (u.origin !== location.origin) return;
        if (u.pathname === location.pathname && u.search === location.search && u.hash) return; // in-page anchor
        if (/export|download|receipt/i.test(u.pathname)) return;                                  // file / new-tab responses
        show('Loading…');
    }, false);

    // ── forms ───────────────────────────────────────────────────────────────
    function labelFor(form) {
        var custom = form.getAttribute('data-loading-text');
        if (custom) return custom;
        var action = (form.getAttribute('action') || '').toLowerCase();
        var method = (form.getAttribute('method') || 'get').toLowerCase();
        if (/logout/.test(action) || form.id === 'logoutForm') return 'Signing out…';
        if (/delete|remove/.test(action)) return 'Deleting…';
        if (method === 'post') return 'Saving…';
        return 'Loading…';
    }

    // Bubble phase on document: runs after the page's own handlers, so a cancelled confirm() or a failed
    // validation (defaultPrevented) never leaves a spinner behind. Scripts that hold the submit back for an
    // async check (party-check.js, voucher-dup.js) show their own "Checking…" and re-submit afterwards.
    document.addEventListener('submit', function (e) {
        if (e.defaultPrevented) return;
        var f = e.target;
        if (!f || (f.target && f.target !== '_self') || f.hasAttribute('data-no-loader')) return;
        if (/export|download/i.test(f.getAttribute('action') || '')) return;
        show(labelFor(f));
    }, false);

    // form.submit() (used by onchange="this.form.submit()" on the date / month pickers) fires no submit event.
    var nativeSubmit = HTMLFormElement.prototype.submit;
    HTMLFormElement.prototype.submit = function () {
        if (!(this.target && this.target !== '_self') && !/export|download/i.test(this.getAttribute('action') || '')) {
            show(labelFor(this));
        }
        return nativeSubmit.call(this);
    };

    // ── history navigation & everything else ─────────────────────────────────
    window.addEventListener('pageshow', function (e) {
        if (e.persisted) {
            // Back/Forward restored a frozen copy; theme.js reloads it. Keep the spinner up meanwhile.
            // (timeout 0 so it also runs after any per-page pageshow handler that hides its own overlay)
            setTimeout(function () { show('Loading…'); }, 0);
        } else {
            hide();
        }
    });

    // Deliberately NO beforeunload hook: it also fires for file downloads (Export PDF/CSV/Excel), where the
    // page never unloads, and would leave a spinner over the page. Script-driven navigations that really
    // leave the page call PayTrackLoading.show() themselves (e.g. the voucher-audit lookup).

    // ── jump to + highlight the row that was just saved (#entry-12 / #invoice-7) ──
    function highlightHashRow() {
        if (!/^#(entry|invoice)-\d+$/.test(location.hash)) return;
        var row = document.getElementById(location.hash.slice(1));
        if (!row) return;
        build();
        var s = document.createElement('style');
        s.textContent = '@keyframes ptflash{0%,60%{background:rgba(34,197,94,.35)}100%{background:transparent}}' +
            'tr.pt-flash td{animation:ptflash 2.8s ease-out 1}';
        document.head.appendChild(s);
        row.classList.add('pt-flash');
        try { row.scrollIntoView({block: 'center'}); } catch (e) { row.scrollIntoView(); }
    }

    // ── flash messages -> toast ──────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        highlightHashRow();
        if (/^\/login\/?$/.test(location.pathname)) return;   // the login page lays its own messages out
        document.querySelectorAll('.alert-success, .alert-danger').forEach(function (n) {
            if (n.querySelector('a, button, form, input')) return;   // only plain text messages
            var msg = (n.textContent || '').replace(/^\s*[✓⚠✔✅❌]\s*/, '').replace(/\s+/g, ' ').trim();
            if (!msg) return;
            toast(msg, n.classList.contains('alert-danger') ? 'err' : 'ok');
            n.style.display = 'none';
        });
    });
})();
