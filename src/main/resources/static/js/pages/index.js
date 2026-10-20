/*
 * Home page (/): the procurement centres and today's crop rates.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
let centres = null;
let crops = null;
let loadFailed = false;
const { icon } = UI;

/** Escapes text before putting it into HTML, quotes too, so it is also safe inside attribute values. */
function esc(value) {
    const replacements = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(value ?? '').replace(/[&<>"']/g, char => replacements[char]);
}

function cropName(crop) {
    return { en: crop.nameEn, ta: crop.nameTa, hi: crop.nameHi }[I18N.lang] || crop.nameEn;
}

function renderCentres() {
    const box = document.getElementById('centres');
    if (loadFailed) {
        box.innerHTML = `<p class="alert alert-error">${esc(I18N.t('common.loadError'))}</p>`;
        return;
    }
    if (!centres) return;

    box.innerHTML = centres.map(c => `
        <article class="centre-row">
            <span class="centre-code">${esc(c.code)}</span>
            <div>
                <h3>${esc(c.name)}</h3>
                <p class="address">${esc(c.address)}</p>
            </div>
            <ul class="centre-meta">
                <li>${icon('clock')}${esc(I18N.t('landing.centres.hours', {
                    open: I18N.formatTime(c.openTime),
                    close: I18N.formatTime(c.closeTime),
                }))}</li>
                <li>${icon('users-three')}${esc(I18N.t('landing.centres.counters', { n: c.counters }))}</li>
                <li>${icon('calendar-check')}${esc(I18N.t('landing.centres.perSlot', { n: c.slotCapacity, mins: c.slotMinutes }))}</li>
            </ul>
        </article>`).join('');
}

function renderRates() {
    const body = document.getElementById('rates');
    if (loadFailed) {
        body.innerHTML = `<tr class="empty-row"><td colspan="3">${esc(I18N.t('common.loadError'))}</td></tr>`;
        return;
    }
    if (!crops) return;

    const percent = new Intl.NumberFormat(I18N.locale(), { maximumFractionDigits: 1 });
    body.innerHTML = crops.map(c => `
        <tr>
            <td class="crop-name">${esc(cropName(c))}</td>
            <td class="num">${esc(I18N.formatMoney(c.ratePerQtl))}</td>
            <td class="num">${esc(percent.format(c.maxMoisturePct))}%</td>
        </tr>`).join('');
}

function render() {
    document.getElementById('demo-wait').textContent = I18N.t('landing.demo.minutes', { n: 20 });
    renderCentres();
    renderRates();
}

// Asked for straight away, while the page texts (language file) are still loading.
const dataRequest = Promise.all([Api.get('/api/centres'), Api.get('/api/crops')]);
dataRequest.catch(() => {});

async function loadData() {
    try {
        [centres, crops] = await dataRequest;
    } catch {
        loadFailed = true;
    }
    render();
}

document.addEventListener('langchange', render);
UI.revealOnScroll();
I18N.init().then(loadData);
