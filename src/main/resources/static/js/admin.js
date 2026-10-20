/*
 * Shared helpers for the admin pages (English only). Load after ui.js, auth.js and api.js.
 * The same pages serve the super admin (every centre) and a centre admin (their own centre).
 */
const Admin = (() => {
    const moneyFormat = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
    let noticeTimer = null;

    let me = null;

    /** Checks an admin is logged in, builds the menu for their role and wires the logout button. */
    function initPage() {
        me = Auth.require(['ADMIN', 'SUPER_ADMIN']);
        if (!me) return null;
        document.getElementById('user-name').textContent = me.name;
        document.getElementById('logout-btn').addEventListener('click', Auth.logout);
        renderHeader();
        return me;
    }

    function isSuper() {
        return me?.role === 'SUPER_ADMIN';
    }

    /** "Super admin" or the centre's name under the logo, and the menu with the current page marked. */
    function renderHeader() {
        const label = document.querySelector('.brand-text small');
        if (label) label.textContent = isSuper() ? 'Super admin' : (me.centreName || 'Centre admin');

        const items = [
            ['/admin/dashboard.html', 'chart-bar', 'Dashboard'],
            ['/admin/payments.html', 'credit-card', 'Payments'],
            ['/admin/settings.html', 'gear', isSuper() ? 'Centres & crops' : 'My centre'],
            ['/admin/staff.html', 'users-three', isSuper() ? 'Admins & staff' : 'Staff'],
        ];
        const nav = document.querySelector('.header-nav');
        if (nav) {
            nav.innerHTML = items.map(([href, icon, text]) =>
                `<a href="${href}"${location.pathname === href ? ' aria-current="page"' : ''}>${UI.icon(icon)}${esc(text)}</a>`).join('');
        }
    }

    /**
     * Escapes text before putting it into HTML. Quotes are escaped too, so text inside value="..."
     * (e.g. a centre name typed by a centre admin) cannot close the attribute and add attributes of its own.
     */
    function esc(value) {
        const replacements = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
        return String(value ?? '').replace(/[&<>"']/g, char => replacements[char]);
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

    return { initPage, isSuper, esc, money, number, time, notice };
})();
