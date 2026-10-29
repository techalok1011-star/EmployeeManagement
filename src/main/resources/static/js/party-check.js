/*
 * "This party does not exist" popup for every entry/invoice form with a Party Name field.
 *
 * On submit it asks the server whether the typed party exists. If not, it offers to (a) use a
 * similar existing party, or (b) create a new one: the user's half-filled form is saved in
 * sessionStorage, they are sent to /parties/new, and after saving they land back on this same page
 * with their entries restored and the new party name filled in. If the check itself fails for any
 * reason the form just submits (the server enforces the rule anyway and shows its own message).
 */
(function () {
    if (!window.PayTrackModal) return;
    var DRAFT_KEY = 'pc:' + location.pathname;
    var isEditPage = /\/edit\/?$/.test(location.pathname);
    var forms = Array.prototype.slice.call(document.querySelectorAll('form'));

    function partyInputOf(form) {
        return form.querySelector('input[name="partyName"]:not([type="hidden"])');
    }

    // ── restore a draft after coming back from the add-party page ──────────────
    (function restore() {
        var params = new URLSearchParams(location.search);
        var created = params.get('createdParty');
        var raw = null;
        try { raw = sessionStorage.getItem(DRAFT_KEY); } catch (e) { /* storage unavailable */ }
        if (raw) {
            try {
                var draft = JSON.parse(raw);
                if (draft.tab && typeof window.switchTab === 'function') {
                    window.switchTab(draft.tab.replace(/^tab-/, ''));
                }
                var form = forms[draft.formIndex];
                if (form) {
                    Object.keys(draft.fields).forEach(function (name) {
                        var els = form.querySelectorAll('[name="' + name + '"]');
                        els.forEach(function (el) {
                            if (el.type === 'checkbox' || el.type === 'radio') {
                                el.checked = (el.value === draft.fields[name]);
                            } else if (el.type !== 'file') {
                                el.value = draft.fields[name];
                            }
                        });
                    });
                    var pi = partyInputOf(form);
                    if (pi && created) pi.value = created;
                }
            } catch (e) { /* bad draft - ignore */ }
            try { sessionStorage.removeItem(DRAFT_KEY); } catch (e) { }
        }
        if (created && window.history && history.replaceState) {
            params.delete('createdParty');
            var qs = params.toString();
            history.replaceState(null, '', location.pathname + (qs ? '?' + qs : '') + location.hash);
        }
    })();

    function saveDraft(form, formIndex) {
        var fields = {};
        Array.prototype.forEach.call(form.elements, function (el) {
            if (!el.name || el.name.indexOf('_csrf') === 0 || el.type === 'file' || el.type === 'password' || el.type === 'submit' || el.type === 'button') return;
            if (el.type === 'checkbox' || el.type === 'radio') {
                if (el.checked) fields[el.name] = el.value;
            } else {
                fields[el.name] = el.value;
            }
        });
        var panel = form.closest('.ledger-panel');
        try {
            sessionStorage.setItem(DRAFT_KEY, JSON.stringify({
                formIndex: formIndex, fields: fields, tab: panel ? panel.id : null
            }));
        } catch (e) { /* storage unavailable - user just re-types */ }
    }

    function suggestions(q) {
        function get(term) {
            return fetch('/api/parties/suggest?limit=5&q=' + encodeURIComponent(term),
                {credentials: 'same-origin', headers: {'Accept': 'application/json'}})
                .then(function (r) { return r.ok ? r.json() : []; });
        }
        return get(q).then(function (list) {
            if (list.length) return list;
            var first = q.split(/[\s,_]+/).filter(function (w) { return w.length >= 3; })[0];
            return first ? get(first) : [];
        }).catch(function () { return []; });
    }

    function showPopup(form, formIndex, input, value) {
        var modal = window.PayTrackModal.show({
            icon: '⚠️',
            title: 'Party does not exist',
            message: '“' + value + '” is not in the party list.',
            message2: 'Do you want to create a new party?',
            actions: [
                {label: '➕ Create new party', kind: 'primary', onClick: function () {
                    saveDraft(form, formIndex);
                    var back = location.pathname + location.search;
                    location.href = '/parties/new?name=' + encodeURIComponent(value) + '&returnTo=' + encodeURIComponent(back);
                }},
                {label: 'Go back', kind: 'ghost', onClick: function () { input.focus(); input.select(); }}
            ]
        });
        suggestions(value).then(function (list) {
            modal.setItems('Or did you mean one of these existing parties?', list.map(function (p) {
                return {text: p.combined + (p.trailingNumber ? '   [' + p.trailingNumber + ']' : ''),
                        onClick: function () { input.value = p.combined; input.focus(); }};
            }));
        });
    }

    forms.forEach(function (form, formIndex) {
        var input = partyInputOf(form);
        if (!input) return;
        var initial = input.value;       // on edit pages an unchanged (even legacy) name is left alone
        var okValue = null;

        function resubmit(v) {
            okValue = v;
            if (form.requestSubmit) form.requestSubmit(); else form.submit();
        }

        form.addEventListener('submit', function (e) {
            var v = input.value.trim();
            if (!v || v === okValue) return;
            if (isEditPage && input.value === initial) return;
            e.preventDefault();
            e.stopImmediatePropagation();
            fetch('/api/parties/exists?name=' + encodeURIComponent(v),
                {credentials: 'same-origin', headers: {'Accept': 'application/json'}})
                .then(function (r) { if (!r.ok) throw new Error('check failed'); return r.json(); })
                .then(function (res) {
                    if (res.exists) resubmit(v); else showPopup(form, formIndex, input, v);
                })
                .catch(function () { resubmit(v); });
        }, true);
    });
})();
