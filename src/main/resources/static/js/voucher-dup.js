/*
 * Receipt Vch No. popup. Included on every collection add/edit form.
 * Never blocks a save - the person entering the data chooses. It reacts to three cases:
 *   - DUPLICATE: the number is already used for another party/day (or the same payment mode again):
 *     shows each earlier entry (party, date, amount) with "Edit number" / "Save anyway".
 *   - SPLIT: the number is already used for THIS party on THIS day in a different payment mode.
 *     One paper receipt is often paid part cash, part net banking and written twice under one number,
 *     so this is a soft question - "Is this another part of the same receipt?" - not a warning.
 *   - SUSPECT: far above the highest number used so far, so probably a typo.
 * If the check itself fails for any reason the form just saves.
 * Uses the same rules as the admin's Voucher Audit, so both sides see the same thing.
 */
(function () {
    var input = document.querySelector('input[name="receiptVchNo"]');
    if (!input || !window.PayTrackModal) return;
    var form = input.closest('form');
    if (!form) return;

    var m = location.pathname.match(/\/entries\/(\d+)\/edit/);
    var excludeId = m ? m[1] : '';
    var initial = input.value.trim();   // on edit pages an unchanged number isn't re-questioned
    var confirmedKey = null;            // voucher + party + date + mode the user already chose to keep
    var cache = {};

    // What the server needs to tell a split receipt from a duplicate: who, which day, which payment mode.
    function ctx() {
        var p = form.querySelector('[name="partyName"]'), d = form.querySelector('[name="entryDate"]'), md = form.querySelector('[name="modeOfPayment"]');
        return {party: p ? p.value.trim() : '', date: d ? d.value : '', mode: md ? md.value : ''};
    }
    function keyOf(v, c) { return [v, c.party, c.date, c.mode].join('|'); }

    function check(value, c) {
        var key = keyOf(value, c);
        if (cache[key]) return Promise.resolve(cache[key]);
        var url = '/api/vouchers/receipt/check?vch=' + encodeURIComponent(value)
            + (excludeId ? '&excludeId=' + excludeId : '')
            + (c.party ? '&party=' + encodeURIComponent(c.party) : '')
            + (c.date ? '&date=' + encodeURIComponent(c.date) : '')
            + (c.mode ? '&mode=' + encodeURIComponent(c.mode) : '');
        return fetch(url, {credentials: 'same-origin', headers: {'Accept': 'application/json'}})
            .then(function (r) { if (!r.ok) throw new Error('check failed'); return r.json(); })
            .then(function (j) { cache[key] = j; return j; });
    }

    function money(a) {
        var n = Number(a);
        return isNaN(n) ? String(a) : '₹' + n.toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2});
    }

    function needsAsking(res) { return res.type === 'DUPLICATE' || res.type === 'SPLIT' || res.type === 'SUSPECT'; }

    // keepLabel/onKeep differ: on blur "Keep it" just remembers the choice, on submit "Save anyway" saves.
    function warn(value, res, keepLabel, onKeep) {
        var num = res.number != null ? res.number : value;
        var editBtn = {label: '✏️ Edit number', kind: 'primary', onClick: function () { input.focus(); input.select(); }};
        var opts = {};
        if (res.type === 'SPLIT') {
            var first = res.matches[0];
            opts.icon = 'ℹ️';
            opts.tone = 'info';
            opts.title = 'Same receipt, paid two ways?';
            opts.message = 'Receipt Vch No. ' + num + ' already has ' + (res.matches.length === 1 ? 'an entry' : 'entries')
                + ' for this party on ' + (first ? first.date : 'this day') + ':';
            opts.rows = res.matches.map(function (mt) {
                return {title: (mt.mode ? mt.mode + ' · ' : '') + money(mt.amount), sub: mt.party};
            });
            opts.message2 = 'Is this another part of the same receipt, for example the cash part and the net-banking part?';
            // for a split the expected answer is "yes", so that is the primary button
            opts.actions = [
                {label: 'Yes, same receipt', kind: 'primary', onClick: onKeep},
                {label: '✏️ Edit number', kind: 'ghost', onClick: editBtn.onClick}
            ];
        } else if (res.type === 'DUPLICATE') {
            opts.icon = '⚠️';
            opts.title = 'Duplicate voucher number';
            opts.message = 'Receipt Vch No. ' + num + ' has already been entered:';
            opts.rows = res.matches.map(function (mt) {
                return {title: mt.party, sub: mt.date + '  ·  ' + (mt.mode ? mt.mode + '  ·  ' : '') + money(mt.amount)};
            });
            opts.actions = [editBtn, {label: keepLabel, kind: 'ghost', onClick: onKeep}];
        } else {
            opts.icon = '⚠️';
            opts.title = 'Voucher number looks wrong';
            opts.message = 'Receipt Vch No. ' + num + ' is far outside this year’s normal range'
                + (res.highest ? ' (highest used so far is ' + Number(res.highest).toLocaleString('en-IN') + ')' : '') + '.';
            opts.message2 = 'It may be a typo. Please check the paper receipt.';
            opts.actions = [editBtn, {label: keepLabel, kind: 'ghost', onClick: onKeep}];
        }
        window.PayTrackModal.show(opts);
    }

    function needsCheck(v, c) {
        return v && confirmedKey !== keyOf(v, c) && !(excludeId && v === initial);
    }

    // 1) Warn as soon as the user leaves the field - but only once the party and payment mode are known,
    //    otherwise a legitimate split would be shown as a duplicate before the form is filled in.
    input.addEventListener('change', function () {
        var v = input.value.trim(), c = ctx();
        if (!v) { confirmedKey = null; return; }
        if (!c.party || !c.mode || !needsCheck(v, c)) return;
        check(v, c).then(function (res) {
            if (needsAsking(res)) warn(v, res, 'Keep it', function () { confirmedKey = keyOf(v, c); });
        }).catch(function () { /* never block on a failed check */ });
    });

    // 2) Re-check on submit (covers typing then pressing Save without leaving the field).
    form.addEventListener('submit', function (e) {
        var v = input.value.trim(), c = ctx();
        if (!needsCheck(v, c)) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        function proceed() { confirmedKey = keyOf(v, c); form.requestSubmit ? form.requestSubmit() : form.submit(); }
        // the check is a network call: show a spinner so a slow connection doesn't look like a dead Save button
        if (window.PayTrackLoading) window.PayTrackLoading.show('Checking voucher number…');
        check(v, c).then(function (res) {
            if (needsAsking(res)) {
                if (window.PayTrackLoading) window.PayTrackLoading.hide();
                warn(v, res, 'Save anyway', proceed);
            } else proceed();
        }).catch(proceed);
    }, true);
})();
