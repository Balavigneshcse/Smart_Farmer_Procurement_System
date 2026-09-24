/*
 * Shared helpers for the admin pages (English only). Load after auth.js and api.js.
 */
const Admin = (() => {
    const moneyFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
    let noticeTimer = null;

    /** Checks the admin is logged in, shows their name and wires the logout button. */
    function initPage() {
        const me = Auth.require('ADMIN');
        if (!me) return null;
        document.getElementById('user-name').textContent = me.name;
        document.getElementById('logout-btn').addEventListener('click', Auth.logout);
        return me;
    }

    /** Escapes text before putting it into HTML. */
    function esc(value) {
        const div = document.createElement('div');
        div.textContent = value ?? '';
        return div.innerHTML;
    }

    /** 806238.3 -> "₹8,06,238" (Indian grouping) */
    function money(amount) {
        return moneyFormat.format(amount ?? 0);
    }

    function number(value, digits = 0) {
        return new Intl.NumberFormat('en-IN', { maximumFractionDigits: digits }).format(value ?? 0);
    }

    /** "08:30:00" -> "8:30 am" */
    function time(hhmmss) {
        const [h, m] = hhmmss.split(':').map(Number);
        return new Date(2000, 0, 1, h, m).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
    }

    /** Shows a message in the page's #notice box; it hides itself after a few seconds unless stay = true. */
    function notice(text, type = 'success', stay = false) {
        const box = document.getElementById('notice');
        box.textContent = text;
        box.className = `alert alert-${type}`;
        clearTimeout(noticeTimer);
        if (!stay) noticeTimer = setTimeout(() => { box.className = 'alert hidden'; }, 8000);
        box.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }

    return { initPage, esc, money, number, time, notice };
})();
