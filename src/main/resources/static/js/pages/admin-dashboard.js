/*
 * Admin dashboard (/admin/dashboard.html): today's numbers, centres, crops and the two charts.
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
        </tr>`;
    }).join('') || '<tr class="empty-row"><td colspan="9">No centres.</td></tr>';
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
        renderCentres(d);
        renderPayments(d);
        renderCrops(d);
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
    }
    setInterval(() => {
        if (document.visibilityState === 'visible') load();
    }, REFRESH_MS);
}
