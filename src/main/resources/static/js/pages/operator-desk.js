/*
 * Operator desk (/operator/desk.html): check-in, counters, weighment and the live queue.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const $ = id => document.getElementById(id);
const me = Auth.require('OPERATOR');
let queue = null;
let source = null;
let refreshTimer = null;
let noticeTimer = null;
let scanner = null;

/** Escapes text before putting it into HTML, quotes too, so it is also safe inside attribute values. */
function esc(value) {
    const replacements = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(value ?? '').replace(/[&<>"']/g, char => replacements[char]);
}

/** "08:30:00" -> "8:30 am" */
function slotTime(hhmmss) {
    const [h, m] = hhmmss.split(':').map(Number);
    return new Date(2000, 0, 1, h, m).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

/** "2026-09-24T09:05:12" -> "9:05 am" */
function clock(iso) {
    return iso ? new Date(iso).toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' }) : '';
}

function qty(n) {
    return `${Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 })} qtl`;
}

function showNotice(text, type = 'success') {
    $('notice').textContent = text;
    $('notice').className = `alert alert-${type}`;
    clearTimeout(noticeTimer);
    noticeTimer = setTimeout(() => { $('notice').className = 'alert hidden'; }, 6000);
}

// ---------- Rendering ----------

function render() {
    if (!queue) return;
    const { centre, stats } = queue;
    $('centre-name').textContent = centre.name;
    $('tv-link').href = `/display.html?centre=${centre.id}`;
    $('s-expected').textContent = stats.expected;
    $('s-waiting').textContent = stats.waiting;
    $('s-counters').textContent = `${stats.atCounters}/${centre.counters}`;
    $('s-served').textContent = stats.served;
    $('s-wait').textContent = stats.avgWaitMinutes == null ? 'No data' : `${stats.avgWaitMinutes} min`;
    renderCounters();
    renderWaiting();
    renderExpected();
    // The farmer being weighed is no longer at a counter (e.g. skipped from another screen): close the form.
    if (weighing && !queue.counters.some(c => c.booking?.id === weighing.id)) closeWeighment();
}

function renderCounters() {
    const anyoneWaiting = queue.waiting.length > 0;
    $('counters').innerHTML = queue.counters.map(({ counterNo, booking: b }) => b ? `
        <div class="card counter-card busy">
            <div class="counter-head">
                <span class="counter-no">Counter ${counterNo}</span>
                <span class="badge badge-amber">${UI.icon('bell-ringing')}Serving</span>
            </div>
            <div class="token-sm">${esc(b.token)}</div>
            <div><strong>${esc(b.farmerName)}</strong></div>
            <div class="small">Aadhaar ends ${esc(b.aadhaarLast4 ?? '')}</div>
            <div class="muted small">${esc(b.cropName)}, ${esc(qty(b.estQtyQtl))}</div>
            <div class="muted small">Called ${esc(clock(b.calledAt))}</div>
            <div class="counter-actions">
                <button type="button" class="btn btn-primary btn-sm" data-weigh="${b.id}">${UI.icon('scales')}Record weighment</button>
                <button type="button" class="btn btn-outline btn-sm" data-recall="${b.id}">Call again</button>
                <button type="button" class="btn btn-danger btn-sm" data-skip="${b.id}" data-token="${esc(b.token)}">Not present</button>
            </div>
        </div>` : `
        <div class="card counter-card free">
            <div class="counter-head">
                <span class="counter-no">Counter ${counterNo}</span>
                <span class="badge badge-grey">Free</span>
            </div>
            <button type="button" class="btn btn-primary btn-block" data-call="${counterNo}" ${anyoneWaiting ? '' : 'disabled'}>
                ${UI.icon('megaphone')}Call next
            </button>
        </div>`).join('');
}

function renderWaiting() {
    $('waiting-count').textContent = queue.waiting.length;
    $('waiting').innerHTML = queue.waiting.length ? queue.waiting.map(b => `
        <tr>
            <td>${b.position}</td>
            <td><strong class="mono">${esc(b.token)}</strong></td>
            <td>${esc(b.farmerName)}${b.late ? ' <span class="badge badge-red">Late</span>' : ''}</td>
            <td>${esc(b.cropName)}, ${esc(qty(b.estQtyQtl))}</td>
            <td>${esc(slotTime(b.slotStart))}</td>
            <td>${esc(clock(b.checkedInAt))}</td>
            <td class="num">${b.etaMinutes === 0 ? 'Now' : `${b.etaMinutes} min`}</td>
        </tr>`).join('')
        : '<tr class="empty-row"><td colspan="7">Nobody is waiting.</td></tr>';
}

function renderExpected() {
    const filter = $('expected-filter').value.trim().toLowerCase();
    const rows = queue.expected.filter(b =>
        !filter || b.token.toLowerCase().includes(filter) || b.farmerName.toLowerCase().includes(filter));
    $('expected-count').textContent = queue.expected.length;
    $('expected').innerHTML = rows.length ? rows.map(b => `
        <tr>
            <td><strong class="mono">${esc(b.token)}</strong></td>
            <td>${esc(b.farmerName)}</td>
            <td>${esc(slotTime(b.slotStart))} to ${esc(slotTime(b.slotEnd))}</td>
            <td>${esc(b.cropName)}, ${esc(qty(b.estQtyQtl))}</td>
            <td class="num"><button type="button" class="btn btn-outline btn-sm" data-checkin="${esc(b.token)}">Check in</button></td>
        </tr>`).join('')
        : `<tr class="empty-row"><td colspan="5">${filter ? 'No match.' : 'No more farmers expected today.'}</td></tr>`;
}

function showCheckInResult(result) {
    const b = result.booking;
    $('checkin-result').innerHTML = `
        <div class="alert alert-success checkin-lines">
            <strong><span class="mono">${esc(b.token)}</span> checked in${b.late ? ' <span class="badge badge-red">Late</span>' : ''}</strong>
            <span>${esc(b.farmerName)}, Aadhaar ends <strong>${esc(b.aadhaarLast4 ?? '')}</strong></span>
            <span>+91 ${esc(b.mobile ?? '')}</span>
            <span>${esc(b.cropName)}, ${esc(qty(b.estQtyQtl))}, slot ${esc(slotTime(b.slotStart))}</span>
            <span>Position <strong>${b.position}</strong> in the line, ETA ${b.etaMinutes === 0 ? 'now' : `${b.etaMinutes} min`}</span>
        </div>`;
}

// ---------- Actions ----------

async function refresh() {
    try {
        queue = await Api.get('/api/operator/queue');
        render();
    } catch (error) {
        showNotice(Api.errorMessage(error), 'error');
    }
}

async function act(path, body) {
    try {
        const result = await Api.post(path, body);
        queue = result.queue;
        render();
        showNotice(result.message);
        return result;
    } catch (error) {
        showNotice(Api.errorMessage(error), 'error');
        return null;
    }
}

let checkingIn = false;    // one check-in at a time, so a double click cannot send the same farmer twice

async function checkIn(code) {
    if (checkingIn) {
        // E.g. a QR scanned while the last check-in is still saving: say so, the scanned code stays in the box.
        showNotice('Please wait for the previous check-in to finish, then press Check in again.', 'error');
        return;
    }
    $('checkin-result').replaceChildren();
    if (!code) {
        showNotice('Enter a token or scan the QR code.', 'error');
        return;
    }
    checkingIn = true;
    $('checkin-btn').disabled = true;
    const result = await act('/api/operator/checkin', { code });
    $('checkin-btn').disabled = false;
    checkingIn = false;
    if (result) {
        showCheckInResult(result);
        if ($('code').value.trim() === code.trim()) $('code').value = '';    // keep a code scanned in the meantime
    }
    $('code').focus();
}

$('checkin-form').addEventListener('submit', event => {
    event.preventDefault();
    checkIn($('code').value.trim());
});

$('counters').addEventListener('click', async event => {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.dataset.weigh) {
        openWeighment(Number(button.dataset.weigh));
        return;
    }
    button.disabled = true;
    if (button.dataset.call) {
        await act('/api/operator/call-next', { counterNo: Number(button.dataset.call) });
    } else if (button.dataset.recall) {
        await act(`/api/operator/bookings/${button.dataset.recall}/recall`);
    } else if (button.dataset.skip) {
        const sure = await UI.confirm({
            title: `${button.dataset.token} not present?`,
            message: 'The farmer did not come to the counter. They will go to the back of the line.',
            confirmText: 'Move to back of line', cancelText: 'Keep at counter', danger: true,
        });
        if (sure) await act(`/api/operator/bookings/${button.dataset.skip}/skip`);
    }
    button.disabled = false;
});

// ---------- Weighment ----------

let weighing = null;    // the booking being weighed

const money = amount => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount);

function openWeighment(bookingId) {
    weighing = queue.counters.map(c => c.booking).find(b => b && b.id === bookingId);
    if (!weighing) return;
    $('w-token').textContent = weighing.token;
    $('w-info').textContent = `${weighing.farmerName}, ${weighing.cropName}, booked ${qty(weighing.estQtyQtl)}. `
        + `Rate ${money(weighing.cropRatePerQtl)} per quintal.`;
    $('w-limit').textContent = `Limit for ${weighing.cropName}: ${weighing.cropMaxMoisturePct}%`;
    $('weigh-form').reset();
    $('w-error').textContent = '';
    updateWeighment();
    $('weigh-panel').classList.remove('hidden');
    $('weigh-panel').scrollIntoView({ behavior: 'smooth', block: 'start' });
    $('w-weight').focus();
}

function closeWeighment() {
    weighing = null;
    $('weigh-panel').classList.add('hidden');
}

/** Moisture above the crop's limit forces "Reject"; the amount preview follows the inputs. */
function updateWeighment() {
    if (!weighing) return;
    const moisture = parseFloat($('w-moisture').value);
    const tooWet = moisture > weighing.cropMaxMoisturePct;
    const accept = document.querySelector('input[name="decision"][value="accept"]');
    accept.disabled = tooWet;
    if (tooWet) document.querySelector('input[name="decision"][value="reject"]').checked = true;
    $('w-moisture-warning').textContent = tooWet
        ? `Moisture ${moisture}% is above the ${weighing.cropMaxMoisturePct}% limit. The crop must be rejected.` : '';

    const weight = parseFloat($('w-weight').value);
    const accepted = document.querySelector('input[name="decision"]:checked').value === 'accept';
    $('w-amount').textContent = !(weight > 0) ? 'Enter the weight to see the amount.'
        : accepted ? `Amount to pay: ${money(Math.round(weight * weighing.cropRatePerQtl * 100) / 100)}`
        : 'Rejected. No payment.';
}

$('weigh-form').addEventListener('input', updateWeighment);
$('w-cancel').addEventListener('click', closeWeighment);

$('weigh-form').addEventListener('submit', async event => {
    event.preventDefault();
    const weight = parseFloat($('w-weight').value);
    const moisture = parseFloat($('w-moisture').value);
    const accepted = document.querySelector('input[name="decision"]:checked').value === 'accept';
    const remarks = $('w-remarks').value.trim();
    const problems = [];
    if (!(weight >= 0.1 && weight <= 1000)) problems.push('Enter a weight between 0.1 and 1000 quintals.');
    if (!(moisture >= 0 && moisture <= 40)) problems.push('Enter the moisture between 0 and 40%.');
    if (!accepted && !remarks) problems.push('Write the reason for rejecting.');
    $('w-error').textContent = problems.join(' ');
    if (problems.length) return;

    const body = {
        weightQtl: Math.round(weight * 100) / 100,
        moisturePct: Math.round(moisture * 10) / 10,
        grade: document.querySelector('input[name="grade"]:checked').value,
        accepted,
        remarks: remarks || null,
    };
    $('w-save').disabled = true;
    const result = await act(`/api/operator/bookings/${weighing.id}/weighment`, body);
    $('w-save').disabled = false;
    if (result) closeWeighment();
});

$('expected').addEventListener('click', async event => {
    const button = event.target.closest('[data-checkin]');
    if (!button || button.disabled) return;
    button.disabled = true;
    await checkIn(button.dataset.checkin);
    button.disabled = false;    // still shown only if the check-in failed (after a success the list is redrawn)
});

$('expected-filter').addEventListener('input', () => queue && renderExpected());
$('logout-btn').addEventListener('click', Auth.logout);

// ---------- Live updates ----------

let liveWasDown = false;

/** Several updates close together cause one reload of the desk. */
function refreshSoon() {
    clearTimeout(refreshTimer);
    refreshTimer = setTimeout(refresh, 250);
}

function setLive(online) {
    // Back after an outage (e.g. the server restarted): reload, changes made meanwhile were never streamed to us.
    if (online && liveWasDown) refreshSoon();
    liveWasDown = !online;
    $('live-status').classList.toggle('offline', !online);
    $('live-text').textContent = online ? 'Live' : 'Reconnecting…';
}

/** Any change at this centre (another counter, a farmer booking for today, ...) refreshes the desk. */
function connectLive(centreId) {
    source = LiveQueue.connect(centreId, {
        onStatus: setLive,
        onSnapshot: refreshSoon,
    });
}

// ---------- QR scanning with the camera (optional) ----------

// The scanner library is big (~370 KB), so it is only downloaded the first time the camera is used.
// The integrity hash makes the browser refuse the file if the CDN ever serves something else.
const QR_LIBRARY = 'https://cdn.jsdelivr.net/npm/html5-qrcode@2.3.8/html5-qrcode.min.js';
const QR_LIBRARY_HASH = 'sha384-c9d8RFSL+u3exBOJ4Yp3HUJXS4znl9f+z66d1y54ig+ea249SpqR+w1wyvXz/lk+';
let qrLibrary = null;

function loadQrLibrary() {
    qrLibrary ??= new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.integrity = QR_LIBRARY_HASH;
        script.crossOrigin = 'anonymous';
        script.src = QR_LIBRARY;
        script.onload = resolve;
        script.onerror = () => {
            qrLibrary = null;
            script.remove();
            reject(new Error('the scanner could not be downloaded (check the internet connection)'));
        };
        document.head.appendChild(script);
    });
    return qrLibrary;
}

async function stopScan() {
    if (scanner) {
        await scanner.stop().catch(() => {});
        scanner = null;
    }
    $('reader').classList.add('hidden');
    $('scan-btn').textContent = 'Scan QR with camera';
}

$('scan-btn').addEventListener('click', async () => {
    if (scanner) {
        await stopScan();
        return;
    }
    $('scan-btn').disabled = true;
    try {
        await loadQrLibrary();
    } catch (error) {
        showNotice(`Camera not available: ${error.message}`, 'error');
        return;
    } finally {
        $('scan-btn').disabled = false;
    }
    $('reader').classList.remove('hidden');
    $('scan-btn').textContent = 'Stop camera';
    scanner = new Html5Qrcode('reader');
    try {
        await scanner.start({ facingMode: 'environment' }, { fps: 10, qrbox: 220 }, async text => {
            await stopScan();
            $('code').value = text;
            checkIn(text);
        }, () => {});
    } catch (error) {
        showNotice(`Camera not available: ${error}`, 'error');
        await stopScan();
    }
});

async function init() {
    $('user-name').textContent = me.name;
    if (navigator.mediaDevices?.getUserMedia) $('scan-btn').classList.remove('hidden');   // this device has a camera API
    await refresh();
    if (queue) connectLive(queue.centre.id);
    $('code').focus();
}

if (me) init();
