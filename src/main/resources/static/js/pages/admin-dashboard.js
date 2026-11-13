/*
 * Admin dashboard (/admin/dashboard.html): today's numbers, farmers and their reviews, centres, crops and the two charts.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const $ = id => document.getElementById(id);
const { esc, money, number } = Admin;
const REFRESH_MS = 30_000;
let charts = {};
let lastData = null;

/** Chart colours come from the page's design tokens, so the charts follow the light / dark theme. */
function palette() {
    const css = getComputedStyle(document.documentElement);
    const token = name => css.getPropertyValue(name).trim();
    return {
        accent: token('--accent'), warn: token('--warn-line'), quiet: token('--field'),
        text: token('--muted'), grid: token('--line'),
    };
}
const chartLibrary = new Promise(resolve => {
    if (typeof Chart !== 'undefined') resolve();
    $('chart-lib').addEventListener('load', resolve);
    $('chart-lib').addEventListener('error', resolve);   // renderCharts shows a message instead
});

const minutes = m => m == null ? 'No data' : `${m} min`;
const dayLabel = iso => new Date(iso + 'T00:00').toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric' });
const dateLabel = iso => new Date(iso + 'T00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

/** "1 review", "3 reviews" */
const count = (n, word) => `${number(n)} ${word}${n === 1 ? '' : 's'}`;

/** 3 of 12 -> "25%"; never divides by zero. */
function percent(part, whole) {
    if (!whole) return '0%';
    const value = Math.round(Math.min(part, whole) * 100 / whole);
    return value === 0 && part > 0 ? 'under 1%' : `${value}%`;
}

// The "what should improve" areas a farmer can tap after the visit: the same codes and order as the API.
const CENTRE_AREAS = {
    WAITING: 'Waiting time', STAFF: 'Staff behaviour', FACILITIES: 'Water, shade and toilets',
    WEIGHING: 'Weighing and grading', PAYMENT: 'Payment speed',
};
const APP_AREAS = {
    BOOKING: 'Booking a slot', LIVE_QUEUE: 'Live queue and TV screen', MESSAGES: 'Emails and reminders',
    LANGUAGE: 'Language and easy reading', LOGIN: 'Login with OTP',
};

/** An average rating like 4.3 with one star. Screen readers hear "4.3 out of 5" instead of the star. */
function averageStars(average) {
    if (average == null) return '&ndash;';
    return `${esc(Number(average).toFixed(1))}<span class="review-stars" aria-hidden="true"> ★</span><span class="sr-only"> out of 5</span>`;
}

/** A 1-5 rating as five stars (filled ★ and empty ☆, so it is not shown by colour alone). */
function ratingStars(rating) {
    const filled = Math.max(0, Math.min(5, Math.round(Number(rating) || 0)));
    return `<span class="review-stars" role="img" aria-label="${filled} out of 5">${'★'.repeat(filled)}`
        + `<span class="review-stars-off">${'☆'.repeat(5 - filled)}</span></span>`;
}

function renderTiles(d) {
    const t = d.today;
    $('today-label').textContent = new Date(d.date + 'T00:00')
        .toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    $('t-booked').textContent = number(t.booked);
    $('t-booked-sub').textContent = `${t.arrived} arrived, ${t.cancelled} cancelled`;
    $('t-waiting').textContent = number(t.waiting);
    $('t-waiting-sub').textContent = `${t.atCounter} at counters`;
    $('t-served').textContent = number(t.served);
    $('t-served-sub').textContent = `${t.rejected} rejected, ${t.noShow} no-show`;
    $('t-wait').textContent = minutes(t.avgWaitMinutes);
    $('t-qty').textContent = `${number(t.quantityQtl, 1)} qtl`;
    $('t-value').textContent = `worth ${money(t.value)}`;
    $('t-pending').textContent = number(d.payments.pendingCount);
    $('t-pending-sub').textContent = money(d.payments.pendingAmount);
}

/** The all-time row: farmers registered, farmers who brought their crop, and the two average ratings. */
function renderFarmers(d) {
    const farmers = d.farmers || {};
    const reviews = d.feedback?.count || 0;
    $('f-registered').textContent = number(farmers.registered);
    // Every farmer can book at any centre, so this number is the same for every admin.
    $('f-registered-sub').textContent = `+${number(farmers.newThisWeek)} this week${Admin.isSuper() ? '' : ' (all centres)'}`;
    $('f-brought').textContent = number(farmers.broughtCrops);
    $('f-brought-sub').textContent = `${number(farmers.accepted)} accepted · ${count(farmers.visits ?? 0, 'visit')} weighed`;
    $('f-centre-rating').innerHTML = averageStars(d.feedback?.avgCentreRating);
    $('f-centre-rating-sub').textContent = reviews ? `from ${count(reviews, 'review')}` : 'No reviews yet';
    $('f-app-rating').innerHTML = averageStars(d.feedback?.avgAppRating);
    $('f-app-rating-sub').textContent = farmers.visits
        ? `${percent(reviews, farmers.visits)} of visits reviewed` : 'No crop weighed yet';
}

function renderCentres(d) {
    $('centre-rows').innerHTML = d.centres.map(c => {
        const filled = c.capacity ? Math.round(c.booked * 100 / c.capacity) : 0;
        const level = filled >= 80 ? 'high' : filled >= 50 ? 'medium' : 'low';
        return `
        <tr>
            <td>
                <strong>${esc(c.name)}</strong>${c.active ? '' : ' <span class="badge badge-grey">Closed</span>'}<br>
                <span class="small muted"><span class="mono">${esc(c.code)}</span>, ${c.counters} counters,
                    <a href="/display.html?centre=${c.id}" target="_blank" rel="noopener">TV screen</a></span>
            </td>
            <td class="num">${c.expected}</td>
            <td class="num">${c.waiting ? `<strong>${c.waiting}</strong>` : 0}</td>
            <td class="num">${c.atCounter} / ${c.counters}</td>
            <td class="num">${c.served}</td>
            <td class="num">${c.noShow}</td>
            <td class="num">${esc(minutes(c.avgWaitMinutes))}</td>
            <td class="num">${esc(number(c.quantityQtl, 1))} qtl</td>
            <td class="fill-cell">
                <span class="small">${c.booked} of ${c.capacity} places</span>
                <div class="fill-bar" data-level="${level}"><span style="width: ${Math.min(filled, 100)}%"></span></div>
            </td>
            <td class="num">${centreRating(c)}</td>
        </tr>`;
    }).join('') || '<tr class="empty-row"><td colspan="10">No centres.</td></tr>';
}

/** "4.3 ★ (12)" for the centres table, or "No reviews". */
function centreRating(c) {
    if (!c.reviews || c.rating == null) return '<span class="small muted">No reviews</span>';
    return `${averageStars(c.rating)} <span class="small muted">(${number(c.reviews)}<span class="sr-only"> reviews</span>)</span>`;
}

function renderPayments(d) {
    const p = d.payments;
    $('p-pending').textContent = `${p.pendingCount} farmers, ${money(p.pendingAmount)}`;
    $('p-today').textContent = `${p.paidTodayCount} farmers, ${money(p.paidTodayAmount)}`;
    $('p-total').textContent = money(p.paidTotal);
}

function renderCrops(d) {
    const max = Math.max(1, ...d.crops.map(c => c.quantityQtl));
    $('crop-bars').innerHTML = d.crops.map(c => `
        <div class="bar-row">
            <span class="bar-label crop-name">${esc(c.crop)}</span>
            <span class="bar-track"><span class="bar-fill" style="width: ${(c.quantityQtl * 100 / max).toFixed(1)}%"></span></span>
            <span class="bar-value">${esc(number(c.quantityQtl))} qtl<br><span class="small muted">${c.farmers} farmers</span></span>
        </div>`).join('');
}

// ---------- Farmer reviews ----------

/** One bar per area, always all five in the fixed order, so the list does not jump around on every refresh. */
function areaBars(areas, list, reviews) {
    const counts = Object.fromEntries((list || []).map(item => [item.code, item.count]));
    return Object.entries(areas).map(([code, label]) => {
        const n = counts[code] || 0;
        const width = reviews ? Math.min(100, n * 100 / reviews).toFixed(1) : 0;
        return `
        <div class="bar-row">
            <span class="bar-label">${esc(label)}</span>
            <span class="bar-track">${n ? `<span class="bar-fill" style="width: ${width}%"></span>` : ''}</span>
            <span class="bar-value">${number(n)}<br><span class="small muted">${esc(percent(n, reviews))}</span></span>
        </div>`;
    }).join('');
}

/** The centre half or the app half of one review: stars, the areas the farmer picked and the comment. */
function reviewPart(title, rating, codes, areas, comment) {
    const picked = (codes || []).map(code => `<span class="badge badge-grey">${esc(areas[code] || code)}</span>`).join('');
    // The comment is escaped; its line breaks show because .review-comment has white-space: pre-line.
    const text = comment ? comment.replace(/\r\n?/g, '\n').replace(/\n{3,}/g, '\n\n').trim() : '';
    return `
    <div class="review-part">
        <div><span class="small muted">${esc(title)}</span> ${ratingStars(rating)}</div>
        ${picked ? `<div class="review-tags">${picked}</div>` : ''}
        ${text ? `<p class="review-comment">${esc(text)}</p>` : ''}
        ${!picked && !text ? '<span class="small muted">Nothing picked to improve</span>' : ''}
    </div>`;
}

function renderReviews(d) {
    const feedback = d.feedback || {};
    const reviews = feedback.count || 0;
    // No reviews yet: a friendly explanation instead of empty bars.
    $('reviews-empty').classList.toggle('hidden', reviews > 0);
    $('reviews-body').classList.toggle('hidden', reviews === 0);
    if (!reviews) return;

    const note = `How many of the ${count(reviews, 'review')} picked each area`;
    $('centre-issues-note').textContent = note;
    $('app-issues-note').textContent = note;
    $('centre-issue-bars').innerHTML = areaBars(CENTRE_AREAS, feedback.centreIssues, reviews);
    $('app-issue-bars').innerHTML = areaBars(APP_AREAS, feedback.appIssues, reviews);

    $('review-list').innerHTML = (feedback.recent || []).map(r => `
        <li class="review-item">
            <div class="review-head">
                <strong>${esc(r.farmerName)}</strong>
                <span class="small muted">${Admin.isSuper() ? `${esc(r.centreName)} · ` : ''}<span class="mono">${esc(r.token)}</span>
                    · <time datetime="${esc(r.date)}">${esc(dateLabel(r.date))}</time></span>
            </div>
            <div class="grid grid-2">
                ${reviewPart('Centre', r.centreRating, r.centreIssues, CENTRE_AREAS, r.centreComment)}
                ${reviewPart('App', r.appRating, r.appIssues, APP_AREAS, r.appComment)}
            </div>
        </li>`).join('');
}

/** Two bar charts with Chart.js; if the library didn't load, the rest of the page still works. */
function renderCharts(d) {
    if (typeof Chart === 'undefined') {
        document.querySelectorAll('.chart-box').forEach(box => {
            box.innerHTML = '<p class="muted small">Chart could not load (no internet?).</p>';
        });
        return;
    }
    const labels = d.lastDays.map(day => dayLabel(day.date));
    const served = d.lastDays.map(day => day.served);
    const noShow = d.lastDays.map(day => day.noShow);
    const pending = d.lastDays.map(day => Math.max(0, day.booked - day.served - day.noShow));
    const qty = d.lastDays.map(day => Number(day.quantityQtl));

    lastData = d;
    if (charts.farmers) {
        Object.assign(charts.farmers.data, { labels });
        charts.farmers.data.datasets[0].data = served;
        charts.farmers.data.datasets[1].data = pending;
        charts.farmers.data.datasets[2].data = noShow;
        charts.farmers.update('none');   // no animation on the 30-second refresh
        charts.qty.data.labels = labels;
        charts.qty.data.datasets[0].data = qty;
        charts.qty.update('none');
        return;
    }
    const colours = palette();
    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
    Chart.defaults.color = colours.text;
    const common = {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
            x: { stacked: true, grid: { display: false } },
            y: { stacked: true, beginAtZero: true, ticks: { precision: 0 }, grid: { color: colours.grid } },
        },
        plugins: { legend: { position: 'bottom', labels: { boxWidth: 12, boxHeight: 12 } } },
    };
    charts.farmers = new Chart($('chart-farmers'), {
        type: 'bar',
        data: {
            labels,
            datasets: [
                { label: 'Served', data: served, backgroundColor: colours.accent, borderRadius: 6 },
                { label: 'Still to serve', data: pending, backgroundColor: colours.warn, borderRadius: 6 },
                { label: 'No-show', data: noShow, backgroundColor: colours.quiet, borderRadius: 6 },
            ],
        },
        options: common,
    });
    charts.qty = new Chart($('chart-qty'), {
        type: 'bar',
        data: { labels, datasets: [{ label: 'Quintals', data: qty, backgroundColor: colours.accent, borderRadius: 6 }] },
        options: {
            ...common,
            plugins: {
                legend: { display: false },
                tooltip: { callbacks: { label: ctx => `${number(ctx.parsed.y, 1)} qtl` } },
            },
        },
    });
}

async function load() {
    try {
        const d = await Api.get('/api/admin/dashboard');
        renderTiles(d);
        renderFarmers(d);
        renderCentres(d);
        renderPayments(d);
        renderCrops(d);
        renderReviews(d);
        chartLibrary.then(() => renderCharts(d));
        const at = new Date(d.updatedAt).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
        $('refresh-status').innerHTML = `<span class="live-dot"></span> Updated ${esc(at)}`;
        $('refresh-status').classList.remove('offline');
    } catch (error) {
        $('refresh-status').innerHTML = '<span class="live-dot"></span> Not updating';
        $('refresh-status').classList.add('offline');
        Admin.notice(Api.errorMessage(error), 'error', true);
    }
}

// ---------- Demo data ----------

function renderDemo(status) {
    $('demo-status').textContent = status.farmers
        ? `Demo data is loaded: ${status.farmers} demo farmers, ${status.bookingsToday} bookings today, ${status.bookingsTotal} in total.`
        : 'No demo data loaded.';
    $('demo-remove').disabled = !status.farmers;
}

async function demoAction(button, request, question, doneText) {
    if (!await UI.confirm(question)) return;
    button.disabled = true;
    try {
        renderDemo(await request());
        Admin.notice(doneText);
        load();
    } catch (error) {
        Admin.notice(Api.errorMessage(error), 'error', true);
    } finally {
        button.disabled = false;
    }
}

$('demo-load').addEventListener('click', event => demoAction(event.currentTarget,
    () => Api.post('/api/admin/demo'),
    {
        title: 'Load a fresh demo day?', message: 'Old demo data is replaced. Real farmers are not touched.',
        confirmText: 'Load demo day', cancelText: 'Cancel',
    },
    'Demo day loaded. Open the operator desk or a TV screen to see the queue.'));

$('demo-remove').addEventListener('click', event => demoAction(event.currentTarget,
    () => Api.delete('/api/admin/demo'),
    {
        title: 'Remove demo data?', message: 'All demo farmers, their bookings and payments are deleted.',
        confirmText: 'Remove demo data', cancelText: 'Cancel', danger: true,
    },
    'Demo data removed.'));

// Theme switched: draw the charts again in the new colours.
document.addEventListener('themechange', () => {
    if (!charts.farmers || !lastData) return;
    Object.values(charts).forEach(chart => chart.destroy());
    charts = {};
    renderCharts(lastData);
});

const me = Admin.initPage();
if (me) {
    load();
    if (Admin.isSuper()) {
        // Demo data fills every centre at once, so only the super admin gets it.
        $('demo-card').classList.remove('hidden');
        Api.get('/api/admin/demo').then(renderDemo).catch(() => { $('demo-status').textContent = 'Could not check demo data.'; });
    } else {
        $('page-title').textContent = 'Today at your centre';
        $('centres-title').textContent = me.centreName || 'Your centre';
        // A centre admin only sees visits and reviews of their own centre.
        $('f-brought-label').textContent = 'Brought their crop here';
        $('centre-issues-title').textContent = 'What should improve at your centre';
    }
    setInterval(() => {
        if (document.visibilityState === 'visible') load();
    }, REFRESH_MS);
}
