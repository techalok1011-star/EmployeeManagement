/*
 * Shared popup used by the voucher-number and unknown-party warnings.
 * Mobile first: on narrow screens it is a bottom sheet with full-width, thumb-sized buttons (min 52px
 * tall, 16px text so iOS doesn't zoom); on wider screens it is a centred card.
 *
 *   PayTrackModal.show({
 *     icon: '⚠️', title: '...', tone: 'warn' | 'danger',
 *     message: 'text', message2: 'text',
 *     rows:  [{title: '...', sub: '...'}],                    // read-only info blocks
 *     items: {label: 'Did you mean...', list: [{text: '...', onClick: fn}]},   // tappable choices
 *     actions: [{label: '...', kind: 'primary' | 'ghost', onClick: fn}]
 *   })  -> returns {close: fn}. Every action closes the popup first, then runs onClick.
 */
(function () {
    if (window.PayTrackModal) return;

    var css = '' +
        '.ptm-overlay{position:fixed;left:0;top:0;right:0;bottom:0;background:var(--backdrop,rgba(15,23,42,.6));z-index:100000;display:flex;align-items:flex-end;justify-content:center;-webkit-tap-highlight-color:transparent}' +
        '.ptm-box{font-family:DM Sans,sans-serif;background:var(--card,#fff);color:var(--text,#1a2233);border:1px solid var(--border,#dde3ee);border-bottom:none;border-radius:20px 20px 0 0;' +
        'padding:22px 18px calc(18px + env(safe-area-inset-bottom,0px));width:100%;max-width:520px;max-height:88vh;overflow-y:auto;-webkit-overflow-scrolling:touch;box-shadow:0 -8px 40px rgba(0,0,0,.28);animation:ptmUp .18s ease-out}' +
        '@keyframes ptmUp{from{transform:translateY(24px);opacity:.4}to{transform:none;opacity:1}}' +
        '@media (prefers-reduced-motion:reduce){.ptm-box{animation:none}}' +
        '.ptm-title{font-family:Syne,sans-serif;font-size:19px;font-weight:700;margin:0 0 8px;line-height:1.3}' +
        '.ptm-title.warn{color:var(--orange,#d9540a)}.ptm-title.danger{color:var(--red,#dc2626)}.ptm-title.info{color:var(--accent,#3b5bdb)}' +
        '.ptm-msg{font-size:15px;line-height:1.5;margin:0 0 12px;word-break:break-word}' +
        '.ptm-row{background:var(--overlay-2,rgba(15,23,42,.05));border:1px solid var(--border,#dde3ee);border-left:3px solid var(--orange,#d9540a);border-radius:10px;padding:11px 13px;margin-bottom:8px;line-height:1.45;word-break:break-word}' +
        '.ptm-row b{display:block;font-size:15px;font-weight:600}.ptm-row span{display:block;font-size:13px;color:var(--muted,#5b6577)}' +
        '.ptm-label{font-size:13px;color:var(--muted,#5b6577);margin:14px 0 7px}' +
        '.ptm-item{display:block;width:100%;text-align:left;min-height:48px;padding:11px 13px;margin-bottom:7px;border-radius:12px;border:1px solid var(--border,#dde3ee);background:var(--overlay-2,rgba(15,23,42,.05));color:var(--text,#1a2233);font-size:15px;line-height:1.35;cursor:pointer;font-family:inherit;word-break:break-word}' +
        '.ptm-item:hover{background:var(--overlay-5,rgba(15,23,42,.09))}' +
        '.ptm-actions{display:flex;flex-direction:column;gap:10px;margin-top:16px}' +
        '.ptm-btn{width:100%;min-height:52px;padding:13px 16px;border-radius:13px;font-size:16px;font-weight:600;cursor:pointer;font-family:inherit;border:1px solid transparent}' +
        '.ptm-btn.primary{background:linear-gradient(135deg,var(--accent,#3b5bdb),var(--accent2,#7c4fe0));color:#fff}' +
        '.ptm-btn.ghost{background:var(--overlay-3,rgba(15,23,42,.06));color:var(--text,#1a2233);border-color:var(--border,#dde3ee)}' +
        '.ptm-btn:focus-visible,.ptm-item:focus-visible{outline:3px solid var(--accent,#3b5bdb);outline-offset:2px}' +
        '@media (min-width:600px){.ptm-overlay{align-items:center;padding:16px}.ptm-box{border-radius:18px;border-bottom:1px solid var(--border,#dde3ee);padding:24px}' +
        '.ptm-actions{flex-direction:row-reverse}.ptm-btn{flex:1}}';

    var styleEl = document.createElement('style');
    styleEl.textContent = css;
    document.head.appendChild(styleEl);

    function el(tag, cls, text) {
        var e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text != null) e.textContent = text;
        return e;
    }

    window.PayTrackModal = {
        show: function (o) {
            var overlay = el('div', 'ptm-overlay');
            overlay.setAttribute('role', 'dialog');
            overlay.setAttribute('aria-modal', 'true');
            var box = el('div', 'ptm-box');
            var prevOverflow = document.body.style.overflow;
            document.body.style.overflow = 'hidden';

            function close() {
                if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
                document.body.style.overflow = prevOverflow;
            }

            box.appendChild(el('div', 'ptm-title ' + (o.tone || 'warn'), (o.icon ? o.icon + ' ' : '') + o.title));
            if (o.message) box.appendChild(el('div', 'ptm-msg', o.message));
            if (o.message2) box.appendChild(el('div', 'ptm-msg', o.message2));
            (o.rows || []).forEach(function (r) {
                var row = el('div', 'ptm-row');
                row.appendChild(el('b', null, r.title));
                if (r.sub) row.appendChild(el('span', null, r.sub));
                box.appendChild(row);
            });
            var itemsHolder = el('div');
            box.appendChild(itemsHolder);

            var actions = el('div', 'ptm-actions');
            (o.actions || []).forEach(function (a) {
                var b = el('button', 'ptm-btn ' + (a.kind || 'ghost'), a.label);
                b.type = 'button';
                b.onclick = function () { close(); if (a.onClick) a.onClick(); };
                actions.appendChild(b);
            });
            box.appendChild(actions);
            overlay.appendChild(box);
            document.body.appendChild(overlay);

            var api = {
                close: close,
                // lets callers fill in tappable choices after an async lookup
                setItems: function (label, list) {
                    if (!overlay.parentNode || !list.length) return;
                    itemsHolder.appendChild(el('div', 'ptm-label', label));
                    list.forEach(function (it) {
                        var b = el('button', 'ptm-item', it.text);
                        b.type = 'button';
                        b.onclick = function () { close(); if (it.onClick) it.onClick(); };
                        itemsHolder.appendChild(b);
                    });
                }
            };
            if (o.items) api.setItems(o.items.label, o.items.list);
            return api;
        }
    };
})();
