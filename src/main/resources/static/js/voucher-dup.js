/*
 * Receipt Vch No. warning popup. Included on every collection add/edit form.
 * Warns the person entering the data - never blocks - when the number is
 *   - DUPLICATE: already used this financial year (shows each earlier entry: party, date, amount), or
 *   - SUSPECT: far above the highest number used so far, so probably a typo.
 * They choose "Edit number" or "Save anyway". If the check itself fails the form just saves.
 * Uses the same rules as the admin's Voucher Audit, so both sides see the same problem.
 */
(function () {
    var input = document.querySelector('input[name="receiptVchNo"]');
    if (!input || !window.PayTrackModal) return;
    var form = input.closest('form');
    if (!form) return;

    var m = location.pathname.match(/\/entries\/(\d+)\/edit/);
    var excludeId = m ? m[1] : '';
    var initial = input.value.trim();   // on edit pages an unchanged number isn't re-questioned
    var confirmedValue = null;          // value the user chose to keep
    var cache = {};

    function check(value) {
        if (cache[value]) return Promise.resolve(cache[value]);
        var url = '/api/vouchers/receipt/check?vch=' + encodeURIComponent(value)
            + (excludeId ? '&excludeId=' + excludeId : '');
        return fetch(url, {credentials: 'same-origin', headers: {'Accept': 'application/json'}})
            .then(function (r) { if (!r.ok) throw new Error('check failed'); return r.json(); })
            .then(function (j) { cache[value] = j; return j; });
    }

    function money(a) {
        var n = Number(a);
        return isNaN(n) ? String(a) : '₹' + n.toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2});
    }

    // keepLabel/onKeep differ: on blur "Keep it" just remembers the choice, on submit "Save anyway" saves.
    function warn(value, res, keepLabel, onKeep) {
        var num = res.number != null ? res.number : value;
        var opts = {
            actions: [
                {label: '✏️ Edit number', kind: 'primary', onClick: function () { input.focus(); input.select(); }},
                {label: keepLabel, kind: 'ghost', onClick: onKeep}
            ]
        };
        if (res.type === 'DUPLICATE') {
            opts.icon = '⚠️';
            opts.title = 'Duplicate voucher number';
            opts.message = 'Receipt Vch No. ' + num + ' has already been entered:';
            opts.rows = res.matches.map(function (mt) {
                return {title: mt.party, sub: mt.date + '  ·  ' + money(mt.amount)};
            });
        } else {
            opts.icon = '⚠️';
            opts.title = 'Voucher number looks wrong';
            opts.message = 'Receipt Vch No. ' + num + ' is far outside this year’s normal range'
                + (res.highest ? ' (highest used so far is ' + Number(res.highest).toLocaleString('en-IN') + ')' : '') + '.';
            opts.message2 = 'It may be a typo. Please check the paper receipt.';
        }
        window.PayTrackModal.show(opts);
    }

    function needsCheck(v) {
        return v && v !== confirmedValue && !(excludeId && v === initial);
    }

    // 1) Warn as soon as the user leaves the field.
    input.addEventListener('change', function () {
        var v = input.value.trim();
        if (!v) { confirmedValue = null; return; }
        if (!needsCheck(v)) return;
        check(v).then(function (res) {
            if (res.type === 'DUPLICATE' || res.type === 'SUSPECT') {
                warn(v, res, 'Keep it', function () { confirmedValue = v; });
            }
        }).catch(function () { /* never block on a failed check */ });
    });

    // 2) Re-check on submit (covers typing then pressing Save without leaving the field).
    form.addEventListener('submit', function (e) {
        var v = input.value.trim();
        if (!needsCheck(v)) return;
        e.preventDefault();
        e.stopImmediatePropagation();
        function proceed() { confirmedValue = v; form.requestSubmit ? form.requestSubmit() : form.submit(); }
        check(v).then(function (res) {
            if (res.type === 'DUPLICATE' || res.type === 'SUSPECT') warn(v, res, 'Save anyway', proceed);
            else proceed();
        }).catch(proceed);
    }, true);
})();
