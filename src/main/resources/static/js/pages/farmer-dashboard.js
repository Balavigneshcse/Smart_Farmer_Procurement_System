/*
 * Farmer dashboard (/farmer/dashboard.html): profile, bookings, notifications and payment details.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const LANGUAGE_NAMES = { en: 'English', ta: 'தமிழ்', hi: 'हिन्दी' };
const $ = id => document.getElementById(id);
const { esc } = Farmer;
let me = null;
let profile = null;
let bookings = null;
let notifications = null;
let showAllNotifications = false;
let noticeTimer = null;
const NOTIFICATIONS_SHOWN = 3;

function showNotice(text, type = 'success') {
    $('notice').textContent = text;
    $('notice').className = `alert alert-${type}`;
    clearTimeout(noticeTimer);
    noticeTimer = setTimeout(() => { $('notice').className = 'alert hidden'; }, 5000);
}

function renderProfile() {
    if (!me) return;   // the page language can switch before the farmer is known
    $('welcome').textContent = I18N.t('dashboard.welcome', { name: profile?.name ?? me.name });
    if (!profile) return;
    $('p-email').textContent = profile.email;
    $('p-mobile').textContent = `+91 ${profile.mobile}`;
    $('p-address').textContent = profile.address;
    $('p-district').textContent = profile.district;
    $('p-aadhaar').textContent = `XXXX XXXX ${profile.aadhaarLast4}`;
    $('p-language').textContent = LANGUAGE_NAMES[profile.language];
    const lines = Payout.summaryLines(profile);
    $('p-payout').innerHTML = lines
        ? `<span class="payout-lines">${lines.map(line => `<span>${esc(line)}</span>`).join('')}</span>`
        : `<span class="badge badge-amber">${UI.icon('warning-circle')}${esc(I18N.t('dashboard.payout.none'))}</span>`;
    $('payout-btn-text').textContent = I18N.t(lines ? 'dashboard.payout.change' : 'dashboard.payout.add');
}

function bookingItem(b, featured = false) {
    return `
        <a class="card booking-item${featured ? ' featured' : ''}" href="/farmer/booking.html?id=${b.id}">
            <div class="booking-token">
                <div class="token-sm">${esc(b.token)}</div>
                ${Farmer.statusBadge(b.status)}
                ${b.payment ? Farmer.paymentBadge(b.payment) : ''}
            </div>
            <div class="booking-info">
                <strong>${esc(Farmer.dayLabel(b.date))}</strong>
                <span>${esc(Farmer.slotTime(b.startTime, b.endTime))}</span>
                <span class="muted">${esc(b.centre.name)}</span>
                <span class="muted">${esc(Farmer.cropName(b.crop))}, ${esc(Farmer.quantity(b.estQtyQtl))}</span>
            </div>
            <span class="booking-item-go">${UI.icon('caret-right')}</span>
        </a>`;
}

function renderBookings() {
    if (!bookings) return;
    $('bookings').removeAttribute('aria-busy');
    if (!bookings.length) {
        $('bookings').innerHTML = `
            <div class="empty">
                <span class="empty-icon">${UI.icon('tractor')}</span>
                <p>${esc(I18N.t('dashboard.bookings.empty'))}</p>
            </div>`;
        return;
    }
    // Current bookings first (soonest first), then earlier ones (newest first).
    const current = bookings.filter(b => Farmer.isActive(b.status))
        .sort((a, b) => (a.date + a.startTime).localeCompare(b.date + b.startTime));
    const earlier = bookings.filter(b => !Farmer.isActive(b.status));

    $('bookings').innerHTML = `
        <div class="booking-list">${current.map((b, i) => bookingItem(b, i === 0)).join('')}</div>
        ${earlier.length ? `
            <h3 class="list-heading">${esc(I18N.t('dashboard.bookings.past'))}</h3>
            <div class="booking-list">${earlier.map(b => bookingItem(b)).join('')}</div>` : ''}`;
}

// Subject and message are saved in the farmer's language when the notification is created.
function notificationItem(n) {
    const href = n.bookingId ? `/farmer/booking.html?id=${n.bookingId}` : '/farmer/dashboard.html';
    const emailed = n.emailed ? `<span>${UI.icon('envelope-simple')}${esc(I18N.t('notifications.emailed'))}</span>` : '';
    return `
        <li class="notif-item${n.read ? '' : ' unread'}">
            <a href="${href}">
                <strong>${esc(n.subject)}</strong>
                <span>${esc(n.message)}</span>
                <span class="notif-meta"><span>${esc(I18N.formatDateTime(n.createdAt))}</span>${emailed}</span>
            </a>
        </li>`;
}

function renderNotifications() {
    if (!notifications) return;
    const { items, unread } = notifications;
    $('unread-count').textContent = I18N.t('notifications.unread', { n: unread });
    $('unread-count').classList.toggle('hidden', unread === 0);
    $('read-all-btn').classList.toggle('hidden', unread === 0);
    if (!items.length) {
        $('notifications').innerHTML = `<li class="muted">${esc(I18N.t('notifications.empty'))}</li>`;
        $('more-btn').classList.add('hidden');
        return;
    }
    const shown = showAllNotifications ? items : items.slice(0, NOTIFICATIONS_SHOWN);
    $('notifications').innerHTML = shown.map(notificationItem).join('');
    $('more-btn').classList.toggle('hidden', shown.length === items.length);
}

function renderAll() {
    renderProfile();
    renderBookings();
    renderNotifications();
}

$('more-btn').addEventListener('click', () => {
    showAllNotifications = true;
    renderNotifications();
});

$('read-all-btn').addEventListener('click', async () => {
    $('read-all-btn').disabled = true;
    try {
        await Api.post('/api/notifications/read-all');
        notifications.items.forEach(n => { n.read = true; });
        notifications.unread = 0;
        renderNotifications();
    } catch (error) {
        showNotice(Api.errorMessage(error), 'error');
    } finally {
        $('read-all-btn').disabled = false;
    }
});

// ---------- Payment details (bank account or UPI) ----------

$('payout-btn').addEventListener('click', () => {
    if (!profile) return;
    if (!$('payout').children.length) Payout.render($('payout'));
    Payout.fill(profile);
    $('payout-form').classList.remove('hidden');
    $('payout-btn').classList.add('hidden');
    $('payout-form').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
});

function closePayoutForm() {
    $('payout-form').classList.add('hidden');
    $('payout-btn').classList.remove('hidden');
}

$('payout-cancel').addEventListener('click', closePayoutForm);

$('payout-form').addEventListener('submit', async event => {
    event.preventDefault();
    const payout = Payout.read();
    if (!payout) return;
    $('payout-save').disabled = true;
    try {
        profile = await Api.put('/api/farmers/me/payout', payout);
        closePayoutForm();
        renderProfile();
        showNotice(I18N.t('dashboard.payout.saved'));
    } catch (error) {
        showNotice(Api.errorMessage(error), 'error');
    } finally {
        $('payout-save').disabled = false;
    }
});

document.addEventListener('langchange', renderAll);
document.addEventListener('languagesaved', event => {
    if (profile) profile.language = event.detail.language;
    renderProfile();
    showNotice(I18N.t('dashboard.langSaved'));
});
document.addEventListener('languagesavefailed', event => showNotice(Api.errorMessage(event.detail.error), 'error'));

async function init() {
    // Ask for the data straight away, while the page texts (language file) are still loading.
    const loading = Auth.user()?.role === 'FARMER'
        ? Promise.all([Api.get('/api/farmers/me'), Api.get('/api/bookings/my'), Api.get('/api/notifications/my')])
        : null;
    loading?.catch(() => {});   // an error is shown below, once the page is ready
    me = await Farmer.initPage();
    if (!me) return;
    renderProfile();
    try {
        [profile, bookings, notifications] = await loading;
        if (profile.language !== I18N.lang) await I18N.setLang(profile.language);
        renderAll();
    } catch (error) {
        showNotice(Api.errorMessage(error), 'error');
    }
}

init();
