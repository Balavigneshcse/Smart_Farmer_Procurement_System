/*
 * Shared helpers for logged-in farmer pages (dashboard, book, booking).
 * Load after i18n.js, auth.js and api.js.
 */
const Farmer = (() => {
    const ACTIVE_STATUSES = ['BOOKED', 'CHECKED_IN', 'CALLED'];
    const STATUS_BADGE = {
        BOOKED: 'badge-blue', CHECKED_IN: 'badge-amber', CALLED: 'badge-amber',
        COMPLETED: '', REJECTED: 'badge-red', CANCELLED: 'badge-grey', NO_SHOW: 'badge-grey',
    };

    /**
     * Checks the farmer is logged in, shows the page in their language and wires
     * the language buttons and logout. Returns the farmer, or null if redirecting to login.
     */
    async function initPage() {
        const me = Auth.require('FARMER');
        if (!me) return null;
        document.getElementById('logout-btn')?.addEventListener('click', Auth.logout);
        await I18N.init({ onUserChange: saveLanguage });
        if (me.language && me.language !== I18N.lang) {
            await I18N.setLang(me.language);
        }
        return me;
    }

    /** A language button was clicked: save it so the farmer's SMS use it too. */
    async function saveLanguage(language) {
        try {
            await Api.put('/api/farmers/me/language', { language });
            Auth.updateUser({ language });
            document.dispatchEvent(new CustomEvent('languagesaved', { detail: { language } }));
        } catch (error) {
            document.dispatchEvent(new CustomEvent('languagesavefailed', { detail: { error } }));
        }
    }

    /** Escapes text before putting it into HTML. */
    function esc(value) {
        const div = document.createElement('div');
        div.textContent = value ?? '';
        return div.innerHTML;
    }

    function cropName(crop) {
        return { en: crop.nameEn, ta: crop.nameTa, hi: crop.nameHi }[I18N.lang] || crop.nameEn;
    }

    function isActive(status) {
        return ACTIVE_STATUSES.includes(status);
    }

    function statusBadge(status) {
        return `<span class="badge ${STATUS_BADGE[status] ?? ''}">${esc(I18N.t(`status.${status}`))}</span>`;
    }

    /** "08:00:00", "08:30:00" -> "8:00 am – 8:30 am" */
    function slotTime(start, end) {
        return `${I18N.formatTime(start)} – ${I18N.formatTime(end)}`;
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

    /** "Paid ₹58,040" (green) or "Payment pending ₹58,040" (amber). */
    function paymentBadge(payment) {
        const style = payment.status === 'PAID' ? '' : payment.status === 'FAILED' ? 'badge-red' : 'badge-amber';
        return `<span class="badge ${style}">${esc(I18N.t(`payment.${payment.status}`))} · ${esc(I18N.formatMoney(payment.amount))}</span>`;
    }

    return {
        initPage, esc, cropName, isActive, statusBadge, paymentBadge, slotTime, dayName, dayLabel, quantity, renderQr,
        todayIso,
    };
})();
