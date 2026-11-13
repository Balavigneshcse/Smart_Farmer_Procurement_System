/*
 * Shared helpers for logged-in farmer pages (dashboard, book, booking).
 * Load after ui.js, i18n.js, auth.js and api.js.
 */
const Farmer = (() => {
    const ACTIVE_STATUSES = ['BOOKED', 'CHECKED_IN', 'CALLED'];
    const STATUS_BADGE = {
        BOOKED: 'badge-blue', CHECKED_IN: 'badge-amber', CALLED: 'badge-amber',
        COMPLETED: '', REJECTED: 'badge-red', CANCELLED: 'badge-grey', NO_SHOW: 'badge-grey',
    };
    const STATUS_ICON = {
        BOOKED: 'calendar-check', CHECKED_IN: 'map-pin', CALLED: 'bell-ringing',
        COMPLETED: 'check-circle', REJECTED: 'x-circle', CANCELLED: 'x', NO_SHOW: 'warning-circle',
    };
    const PAYMENT_ICON = { PAID: 'hand-coins', PENDING: 'hourglass', FAILED: 'warning-circle' };

    /**
     * Checks the farmer is logged in, shows the page in their language and wires
     * the language buttons and logout. Returns the farmer, or null if redirecting to login.
     */
    async function initPage() {
        const me = Auth.require('FARMER');
        if (!me) return null;
        document.getElementById('logout-btn')?.addEventListener('click', Auth.logout);
        await I18N.init({ onUserChange: saveLanguage });
        // the registered language, unless the farmer already clicked another one while the page was loading
        if (me.language && me.language !== I18N.lang && !I18N.chosenByUser) {
            await I18N.setLang(me.language);
        }
        return me;
    }

    let lastSave = Promise.resolve();

    /** A language button was clicked: save it so the farmer's emails use it too. */
    async function saveLanguage(language, shown) {
        // saves go one after another, so after quick clicks the server also ends with the last one
        const save = lastSave.then(() => Api.put('/api/farmers/me/language', { language }));
        lastSave = save.catch(() => {});
        try {
            await save;
            Auth.updateUser({ language });
            await shown;   // so the "saved" message appears in the new language
            if (language !== I18N.lang) return;   // the farmer has clicked yet another language since
            document.dispatchEvent(new CustomEvent('languagesaved', { detail: { language } }));
        } catch (error) {
            document.dispatchEvent(new CustomEvent('languagesavefailed', { detail: { error } }));
        }
    }

    /** Escapes text before putting it into HTML, quotes too, so it is also safe inside attribute values. */
    function esc(value) {
        const replacements = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
        return String(value ?? '').replace(/[&<>"']/g, char => replacements[char]);
    }

    function cropName(crop) {
        return { en: crop.nameEn, ta: crop.nameTa, hi: crop.nameHi }[I18N.lang] || crop.nameEn;
    }

    function isActive(status) {
        return ACTIVE_STATUSES.includes(status);
    }

    function statusBadge(status) {
        return `<span class="badge ${STATUS_BADGE[status] ?? ''}">${UI.icon(STATUS_ICON[status] ?? 'info')}${esc(I18N.t(`status.${status}`))}</span>`;
    }

    /** "08:00:00", "08:30:00" -> "8:00 am to 8:30 am" (worded per language) */
    function slotTime(start, end) {
        return I18N.t('common.timeRange', { start: I18N.formatTime(start), end: I18N.formatTime(end) });
    }

    function isoDate(date) {
        return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    }

    /** "Today", "Tomorrow" or the weekday name. */
    function dayName(iso) {
        const today = new Date();
        const tomorrow = new Date(today.getFullYear(), today.getMonth(), today.getDate() + 1);
        if (iso === isoDate(today)) return I18N.t('common.today');
        if (iso === isoDate(tomorrow)) return I18N.t('common.tomorrow');
        return I18N.formatDate(iso, { weekday: 'long' });
    }

    /** "Today, 24 Sep" */
    function dayLabel(iso) {
        return `${dayName(iso)}, ${I18N.formatDate(iso, { day: 'numeric', month: 'short' })}`;
    }

    function quantity(n) {
        const number = new Intl.NumberFormat(I18N.locale(), { maximumFractionDigits: 2 }).format(n);
        return I18N.t('booking.qtlValue', { n: number });
    }

    /** Draws a QR code (needs qrcode.min.js). If the library didn't load, the token text is still shown. */
    function renderQr(element, text) {
        element.replaceChildren();
        if (typeof QRCode === 'undefined') {
            element.classList.add('hidden');
            return;
        }
        element.classList.remove('hidden');
        new QRCode(element, { text, width: 168, height: 168, correctLevel: QRCode.CorrectLevel.M });
    }

    function todayIso() {
        return isoDate(new Date());
    }

    /** "Paid · ₹58,040" (green) or "Payment pending · ₹58,040" (amber). */
    function paymentBadge(payment) {
        const style = payment.status === 'PAID' ? '' : payment.status === 'FAILED' ? 'badge-red' : 'badge-amber';
        return `<span class="badge ${style}">${UI.icon(PAYMENT_ICON[payment.status] ?? 'info')}`
            + `${esc(I18N.t(`payment.${payment.status}`))} · ${esc(I18N.formatMoney(payment.amount))}</span>`;
    }

    return {
        initPage, esc, cropName, isActive, statusBadge, paymentBadge, slotTime, dayName, dayLabel, quantity, renderQr,
        todayIso,
    };
})();
