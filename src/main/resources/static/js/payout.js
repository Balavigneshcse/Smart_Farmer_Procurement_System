/*
 * The "how should we pay you" form part: bank account or UPI ID.
 * Used on the registration page and on the farmer dashboard. Load after i18n.js.
 *
 *   Payout.render(element)   draws the fields into the element
 *   Payout.read()            returns { method, ... } for the server, or null after showing what is wrong
 */
const Payout = (() => {
    const ACCOUNT = /^\d{9,18}$/;
    const IFSC = /^[A-Z]{4}0[A-Z0-9]{6}$/;
    const UPI = /^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,31}$/;
    const $ = id => document.getElementById(id);

    function field(id, labelKey, attributes, hintKey) {
        return `
            <div class="field">
                <label for="${id}" data-i18n="${labelKey}"></label>
                <input id="${id}" ${attributes} aria-describedby="${hintKey ? `${id}-hint ` : ''}${id}-error">
                ${hintKey ? `<span class="hint" id="${id}-hint" data-i18n="${hintKey}"></span>` : ''}
                <p class="field-error" id="${id}-error"></p>
            </div>`;
    }

    function render(container) {
        container.innerHTML = `
            <fieldset class="field">
                <legend data-i18n="register.payout.title"></legend>
                <span class="hint payout-hint" data-i18n="register.payout.hint"></span>
                <div class="choice-group two">
                    <label class="choice"><input type="radio" name="payout-method" value="BANK" checked>
                        <span>${UI.icon('bank')}<span data-i18n="register.payout.bank"></span></span></label>
                    <label class="choice"><input type="radio" name="payout-method" value="UPI">
                        <span>${UI.icon('qr-code')}<span data-i18n="register.payout.upi"></span></span></label>
                </div>
            </fieldset>
            <div id="pay-bank">
                ${field('pay-holder', 'register.payout.holder', 'maxlength="100" autocomplete="name" data-i18n-placeholder="register.payout.holder.placeholder"')}
                ${field('pay-account', 'register.payout.account', 'inputmode="numeric" maxlength="18" autocomplete="off"')}
                ${field('pay-account2', 'register.payout.account2', 'inputmode="numeric" maxlength="18" autocomplete="off"')}
                ${field('pay-ifsc', 'register.payout.ifsc', 'maxlength="11" autocomplete="off" autocapitalize="characters" spellcheck="false" class="mono-input"', 'register.payout.ifsc.hint')}
                ${field('pay-bank-name', 'register.payout.bankName', 'maxlength="100" autocomplete="off"')}
            </div>
            <div id="pay-upi" class="hidden">
                ${field('pay-upi-id', 'register.payout.upiId', 'maxlength="100" autocomplete="off" autocapitalize="none" spellcheck="false" inputmode="email"', 'register.payout.upiId.hint')}
            </div>`;
        I18N.apply(container);

        container.querySelectorAll('input[name="payout-method"]').forEach(radio =>
            radio.addEventListener('change', showMethod));
        ['pay-account', 'pay-account2'].forEach(id => $(id).addEventListener('input', event => {
            event.target.value = event.target.value.replace(/\D/g, '');
        }));
        $('pay-ifsc').addEventListener('input', event => {
            event.target.value = event.target.value.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
        });
    }

    function method() {
        return document.querySelector('input[name="payout-method"]:checked').value;
    }

    function showMethod() {
        $('pay-bank').classList.toggle('hidden', method() !== 'BANK');
        $('pay-upi').classList.toggle('hidden', method() !== 'UPI');
    }

    /** Pre-selects the farmer's current method (the account number itself is never sent back to the browser). */
    function fill(profile) {
        const current = profile?.payoutMethod === 'UPI' ? 'UPI' : 'BANK';
        document.querySelector(`input[name="payout-method"][value="${current}"]`).checked = true;
        $('pay-holder').value = profile?.accountHolder ?? '';
        $('pay-ifsc').value = profile?.ifsc ?? '';
        $('pay-bank-name').value = profile?.bankName ?? '';
        $('pay-upi-id').value = profile?.upiId ?? '';
        $('pay-account').value = '';
        $('pay-account2').value = '';
        clearErrors();
        showMethod();
    }

    function clearErrors() {
        ['pay-holder', 'pay-account', 'pay-account2', 'pay-ifsc', 'pay-bank-name', 'pay-upi-id'].forEach(id => {
            $(id).removeAttribute('aria-invalid');
            $(`${id}-error`).textContent = '';
        });
    }

    function read() {
        clearErrors();
        const invalid = [];
        let values;
        if (method() === 'BANK') {
            values = {
                method: 'BANK',
                accountHolder: $('pay-holder').value.trim(),
                accountNumber: $('pay-account').value.trim(),
                ifsc: $('pay-ifsc').value.trim().toUpperCase(),
                bankName: $('pay-bank-name').value.trim(),
            };
            if (values.accountHolder.length < 2) invalid.push(['pay-holder', 'validation.holder']);
            if (!ACCOUNT.test(values.accountNumber)) invalid.push(['pay-account', 'validation.account']);
            else if ($('pay-account2').value.trim() !== values.accountNumber) invalid.push(['pay-account2', 'validation.accountMatch']);
            if (!IFSC.test(values.ifsc)) invalid.push(['pay-ifsc', 'validation.ifsc']);
            if (values.bankName.length < 2) invalid.push(['pay-bank-name', 'validation.bankName']);
        } else {
            values = { method: 'UPI', upiId: $('pay-upi-id').value.trim() };
            if (!UPI.test(values.upiId)) invalid.push(['pay-upi-id', 'validation.upi']);
        }
        invalid.forEach(([id, key]) => {
            $(id).setAttribute('aria-invalid', 'true');
            $(`${id}-error`).textContent = I18N.t(key);
        });
        if (invalid.length) {
            $(invalid[0][0]).focus();
            return null;
        }
        return values;
    }

    /** "UPI: ravi@okaxis" or the bank with the masked account number, as lines of text for a details list. */
    function summaryLines(profile) {
        if (profile.payoutMethod === 'UPI') return [I18N.t('register.payout.upi'), profile.upiId];
        if (profile.payoutMethod === 'BANK') {
            return [profile.bankName, profile.accountNumberMasked, `IFSC ${profile.ifsc}`, profile.accountHolder];
        }
        return null;
    }

    return { render, read, fill, summaryLines };
})();
