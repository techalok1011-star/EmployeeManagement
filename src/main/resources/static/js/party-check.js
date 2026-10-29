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

    // Words that appear in most party names and say nothing about which party it is.
    var NOISE = ['BUILDING', 'MATERIAL', 'MATERIALS', 'TRADERS', 'TRADER', 'STORE', 'STORES', 'AGENCY', 'ENTERPRISE', 'ENTERPRISES', 'SUPPLY', 'CEMENT', 'AND', 'THE'];

    function tokensOf(text) {
        return (text.toUpperCase().match(/[A-Z0-9]+/g) || []).filter(function (w) {
            return w.length >= 3 && NOISE.indexOf(w) === -1;
        });
    }

    // A typed word matches a party name if the name contains it, or (to forgive a typo near the end
    // of a long word, e.g. CHAUHN for CHAUHAN) contains the word minus its last two letters.
    function wordMatches(word, nameUpper) {
        if (nameUpper.indexOf(word) !== -1) return true;
        return word.length >= 5 && nameUpper.indexOf(word.slice(0, word.length - 2)) !== -1;
    }

    // Only suggest parties that share most of the distinctive words typed. A single shared word
    // (e.g. SHREE) is not enough when several were typed - that is how wrong suggestions appear.
    function rankSuggestions(typed, candidates) {
        var words = tokensOf(typed);
        if (!words.length) return [];
        var need = words.length === 1 ? 1 : Math.max(2, Math.ceil(words.length * 0.6));
        return candidates.map(function (c) {
            var upper = (c.combined || '').toUpperCase();
            var score = words.filter(function (w) { return wordMatches(w, upper); }).length;
            return {p: c, score: score};
        }).filter(function (x) { return x.score >= need; })
          .sort(function (x, y) { return y.score - x.score; })
          .slice(0, 5)
          .map(function (x) { return x.p; });
    }

    function suggestions(q) {
        var words = tokensOf(q);
        if (!words.length) return Promise.resolve([]);
        return Promise.all(words.map(function (word) {
            return fetch('/api/parties/suggest?limit=50&q=' + encodeURIComponent(word),
                {credentials: 'same-origin', headers: {'Accept': 'application/json'}})
                .then(function (r) { return r.ok ? r.json() : []; })
                .catch(function () { return []; });
        })).then(function (lists) {
            var seen = {}, all = [];
            lists.forEach(function (list) {
                list.forEach(function (c) { if (!seen[c.combined]) { seen[c.combined] = 1; all.push(c); } });
            });
            return rankSuggestions(q, all);
        });
    }

    function showPopup(form, formIndex, input, value) {
        var modal = window.PayTrackModal.show({
            icon: '⚠️',
            title: 'Party does not exist',
            message: '“' + value + '” is not in the party list.',
            message2: 'Do you want to add it as a new party?',
            actions: [
                {label: 'Add new party', kind: 'primary', onClick: function () {
                    saveDraft(form, formIndex);
                    var back = location.pathname + location.search;
                    location.href = '/parties/new?name=' + encodeURIComponent(value) + '&returnTo=' + encodeURIComponent(back);
                }},
                {label: 'Go back', kind: 'ghost', onClick: function () { input.focus(); input.select(); }}
            ]
        });
        suggestions(value).then(function (list) {
            modal.setItems('Did you mean one of these saved parties?', list.map(function (p) {
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
