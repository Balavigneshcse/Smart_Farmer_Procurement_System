/*
 * One booking (/farmer/booking.html?id=...): details, QR code, live queue position, result and payment.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const $ = id => document.getElementById(id);
const { esc } = Farmer;
const t = (key, params) => I18N.t(key, params);
const bookingId = Number(new URLSearchParams(location.search).get('id'));
let booking = null;
let qrShownFor = null;
const EVENT_ICON = {
    BOOKED: 'calendar-check', CANCELLED: 'x', CHECKED_IN: 'map-pin', CALLED: 'bell-ringing', COMPLETED: 'scales',
    REJECTED: 'x-circle', NO_SHOW: 'warning-circle', PAYMENT_PENDING: 'hourglass', PAID: 'hand-coins',
    SKIPPED: 'arrow-clockwise',
};

function showAlert(text, type = 'error') {
    $('alert').textContent = text;
    $('alert').className = `alert alert-${type}`;
}

function render() {
    if (!booking) return;
    const b = booking;
    $('status').innerHTML = Farmer.statusBadge(b.status);
    $('token').textContent = b.token;

    // The QR is only useful while the booking is still active.
    const active = Farmer.isActive(b.status);
    $('qr-block').classList.toggle('hidden', !active);
    if (active && qrShownFor !== b.qrText) {
        Farmer.renderQr($('qr'), b.qrText);
        qrShownFor = b.qrText;
    }

    $('details').innerHTML = `
        <dt>${esc(t('booking.date'))}</dt><dd>${esc(Farmer.dayLabel(b.date))}</dd>
        <dt>${esc(t('booking.time'))}</dt><dd>${esc(Farmer.slotTime(b.startTime, b.endTime))}</dd>
        <dt>${esc(t('booking.centre'))}</dt><dd>${esc(b.centre.name)}<br><span class="muted small">${esc(b.centre.address)}</span></dd>
        <dt>${esc(t('booking.crop'))}</dt><dd>${esc(Farmer.cropName(b.crop))}</dd>
        <dt>${esc(t('booking.quantity'))}</dt><dd>${esc(Farmer.quantity(b.estQtyQtl))}</dd>
        <dt>${esc(t('booking.estimate'))}</dt><dd>${esc(I18N.formatMoney(b.estAmount))}</dd>`;

    // Newest event at the top.
    $('timeline').innerHTML = [...(b.events || [])].reverse().map(e => `
        <li>
            <span class="timeline-icon">${UI.icon(EVENT_ICON[e.event] ?? 'info')}</span>
            <div>
                <strong>${esc(t(`event.${e.event}`))}</strong>
                <time datetime="${esc(e.at)}">${esc(I18N.formatDateTime(e.at))}</time>
            </div>
        </li>`).join('');

    $('cancel-btn').classList.toggle('hidden', !b.cancellable);
    renderResult();
    renderFeedback();
    $('content').classList.remove('hidden');
    updateLive();
}

/** Weighment result and payment, once the crop has been weighed at the counter. */
function renderResult() {
    const p = booking.procurement;
    $('result-card').classList.toggle('hidden', !p);
    if (!p) return;

    const number = (n, digits) => new Intl.NumberFormat(I18N.locale(), { maximumFractionDigits: digits }).format(n);
    const measured = `
        <dt>${esc(t('result.weight'))}</dt><dd>${esc(Farmer.quantity(p.weightQtl))}</dd>
        <dt>${esc(t('result.moisture'))}</dt><dd>${esc(number(p.moisturePct, 1))}%</dd>
        <dt>${esc(t('result.grade'))}</dt><dd>${esc(p.grade)}</dd>`;

    if (!p.accepted) {
        $('result').innerHTML = `
            <div class="alert alert-error">
                <strong>${esc(t('result.rejected'))}</strong><br>${esc(t('result.reason', { reason: p.remarks }))}
            </div>
            <dl class="details">${measured}</dl>`;
        return;
    }

    const pay = booking.payment;
    let paymentHtml = '';
    if (pay) {
        paymentHtml = `
            <div class="payment-block">
                <h3>${esc(t('payment.title'))}</h3>
                ${Farmer.paymentBadge(pay)}
                ${pay.status === 'PAID'
                    ? `<p class="small muted">${esc(t('payment.paidOn', { date: I18N.formatDateTime(pay.paidAt) }))}<br>
                         <span class="mono">${esc(t('payment.reference', { ref: pay.reference }))}</span></p>`
                    : `<p class="small muted">${esc(t('payment.pendingNote'))}</p>`}
            </div>`;
    }
    $('result').innerHTML = `
        <dl class="details">
            ${measured}
            <dt>${esc(t('result.rate'))}</dt><dd>${esc(t('book.ratePerQtl', { rate: I18N.formatMoney(p.ratePerQtl) }))}</dd>
            <dt>${esc(t('result.amount'))}</dt><dd class="result-amount">${esc(I18N.formatMoney(p.amount))}</dd>
        </dl>
        ${paymentHtml}`;
}

// ---------- Review of the visit (after the crop is weighed: accepted or rejected) ----------

const REVIEW_STATUSES = ['COMPLETED', 'REJECTED'];
const PARTS = ['centre', 'app'];   // the two halves of the form; their element ids start with these words
const COMMENT_MAX = 500;
let sendingFeedback = false;

/** Shows a translated text; data-i18n lets I18N.apply() translate it again when the language changes. */
function setText(element, key) {
    if (!key) {
        delete element.dataset.i18n;
        element.textContent = '';
        return;
    }
    if (element.dataset.i18n === key) return;   // unchanged: don't make screen readers repeat it
    element.dataset.i18n = key;
    element.textContent = t(key);
}

function chosenRating(part) {
    return Number($('feedback-form').querySelector(`input[name="${part}Rating"]:checked`)?.value) || null;
}

function chosenIssues(part) {
    return [...$('feedback-form').querySelectorAll(`input[name="${part}Issues"]:checked`)].map(box => box.value);
}

function commentText(part) {
    return $(`${part}-comment`).value.trim() || null;
}

/** The word under the stars ("Good") and the letter counter follow what the farmer has chosen and typed. */
function syncPart(part) {
    const rating = chosenRating(part);
    const word = $(`${part}-rating-word`);
    setText(word, rating ? `feedback.word.${rating}` : 'feedback.tapStar');
    word.classList.toggle('chosen', Boolean(rating));
    if (rating) setText($(`${part}-rating-error`), null);
    $(`${part}-comment-count`).textContent = `${$(`${part}-comment`).value.length}/${COMMENT_MAX}`;
}

/**
 * The review card: the form while there is no review yet, a thank-you with the farmer's answers after.
 * Only the texts around the form change here, never the inputs, so nothing typed is lost on a re-render.
 */
function renderFeedback() {
    const b = booking;
    const show = REVIEW_STATUSES.includes(b.status);
    $('feedback-card').classList.toggle('hidden', !show);
    if (!show) return;

    const done = Boolean(b.feedback);
    setText($('feedback-title'), done ? 'feedback.doneTitle' : 'feedback.title');
    // a kinder opening line when the crop was not accepted
    setText($('feedback-intro'), b.status === 'REJECTED' ? 'feedback.introRejected' : 'feedback.intro');
    $('feedback-intro').classList.toggle('hidden', done);
    $('feedback-form').classList.toggle('hidden', done);
    $('feedback-thanks').classList.toggle('hidden', !done);
    if (done) {
        renderThanks(b.feedback);
    } else {
        PARTS.forEach(syncPart);
    }
}

/** Five small stars, the first `rating` of them filled; read out as "4 out of 5". */
function starRow(rating) {
    const stars = [1, 2, 3, 4, 5].map(n =>
        `<svg class="star-svg${n <= rating ? ' on' : ''}" aria-hidden="true" focusable="false"><use href="#star"></use></svg>`).join('');
    return `<span class="star-row" role="img" aria-label="${esc(t(`feedback.star.${rating}`))}">${stars}</span>`;
}

function thanksPart(titleKey, rating, issues, comment) {
    const areas = issues?.length ? `
        <p class="feedback-improve">${esc(t('feedback.thanks.improve'))}</p>
        <ul class="tag-list">${issues.map(code => `<li class="badge">${esc(t(`feedback.area.${code}`))}</li>`).join('')}</ul>` : '';
    return `
        <div class="feedback-summary-item">
            <h3>${esc(t(titleKey))}</h3>
            <p class="feedback-rating">${starRow(rating)}<span>${esc(t(`feedback.word.${rating}`))}</span></p>
            ${areas}
            ${comment ? `<blockquote class="feedback-quote">${esc(comment)}</blockquote>` : ''}
        </div>`;
}

function renderThanks(f) {
    $('feedback-summary').innerHTML =
        thanksPart('feedback.thanks.centre', f.centreRating, f.centreIssues, f.centreComment)
        + thanksPart('feedback.thanks.app', f.appRating, f.appIssues, f.appComment);
    $('feedback-sent-on').textContent = f.createdAt
        ? t('feedback.thanks.sentOn', { date: I18N.formatDateTime(f.createdAt) }) : '';
}

// Keep the star word and the letter counters up to date while the farmer fills in the form.
['input', 'change'].forEach(type => $('feedback-form').addEventListener(type, () => PARTS.forEach(syncPart)));

$('feedback-form').addEventListener('submit', async event => {
    event.preventDefault();
    if (sendingFeedback) return;
    $('feedback-error').textContent = '';

    // Both star ratings are needed; the rest is optional.
    const missing = PARTS.filter(part => !chosenRating(part));
    PARTS.forEach(part => setText($(`${part}-rating-error`), missing.includes(part) ? 'feedback.pickStars' : null));
    if (missing.length) {
        $('feedback-form').querySelector(`input[name="${missing[0]}Rating"]`).focus();
        return;
    }

    const body = {
        centreRating: chosenRating('centre'),
        appRating: chosenRating('app'),
        centreIssues: chosenIssues('centre'),
        appIssues: chosenIssues('app'),
        centreComment: commentText('centre'),
        appComment: commentText('app'),
    };
    sendingFeedback = true;
    $('feedback-send').disabled = true;
    setText($('feedback-send-label'), 'feedback.sending');
    try {
        booking = await Api.post(`/api/bookings/${bookingId}/feedback`, body);
        render();
        $('feedback-thanks').focus();   // screen readers hear the thank-you
    } catch (error) {
        if (error.code === 'FEEDBACK_ALREADY_GIVEN') {
            // sent already (maybe from another tab or phone): show that review instead of the form
            try {
                booking = await Api.get(`/api/bookings/${bookingId}`);
                render();
                return;
            } catch {
                // fall through and show the message
            }
        }
        $('feedback-error').textContent = Api.errorMessage(error);
    } finally {
        sendingFeedback = false;
        $('feedback-send').disabled = false;
        setText($('feedback-send-label'), 'feedback.send');
    }
});

/** Opened from the "How was your visit?" reminder (...#feedback): bring the review card into view. */
function scrollToFeedback() {
    if (location.hash !== '#feedback' || $('feedback-card').classList.contains('hidden')) return;
    $('feedback-card').scrollIntoView({ block: 'start' });
}

// ---------- Live queue (only on the day of the booking, while it is active) ----------

let source = null;
let snapshot = null;
let reloadTimer = null;
let announcedCounter = null;
let shownPosition = null;

function isLiveDay() {
    return booking && booking.date === Farmer.todayIso() && Farmer.isActive(booking.status);
}

function updateLive() {
    if (!isLiveDay()) {
        source?.close();
        source = null;
        $('live').classList.add('hidden');
        return;
    }
    $('live').classList.remove('hidden');
    if (!source) {
        source = LiveQueue.connect(booking.centre.id, {
            onSnapshot: queue => {
                snapshot = queue;
                renderLive();
                syncStatus();
            },
            onStatus: online => {
                if (!online) $('live-updated').textContent = t('live.reconnecting');
            },
        });
    }
    renderLive();
}

/** Where is this farmer's token in the public queue? */
function liveState() {
    const serving = snapshot.serving.find(s => s.token === booking.token);
    if (serving) return { status: 'CALLED', counterNo: serving.counterNo };
    const waiting = snapshot.waiting.find(w => w.token === booking.token);
    if (waiting) return { status: 'CHECKED_IN', position: waiting.position, eta: waiting.etaMinutes };
    return { status: 'NOT_IN_QUEUE' };
}

function renderLive() {
    if (!snapshot) {
        $('live-body').innerHTML = UI.skeleton(2, esc(t('common.loading')));
        return;
    }
    const state = liveState();
    $('live').classList.toggle('is-called', state.status === 'CALLED');
    if (state.status === 'CALLED') {
        // Drawn once per call, so the entrance animation plays once and not on every queue update.
        if (announcedCounter !== state.counterNo || !$('live-body').querySelector('.live-called')) {
            $('live-body').innerHTML = `<div class="live-called">${UI.icon('bell-ringing')}<span></span></div>`;
        }
        $('live-body').querySelector('.live-called span').textContent = t('live.goToCounter', { n: state.counterNo });
        if (announcedCounter !== state.counterNo) {
            announcedCounter = state.counterNo;
            navigator.vibrate?.([400, 150, 400]);
        }
    } else if (state.status === 'CHECKED_IN') {
        announcedCounter = null;
        const ahead = state.position - 1;
        const moved = shownPosition !== null && shownPosition !== state.position;
        shownPosition = state.position;
        $('live-body').innerHTML = `
            <div class="live-numbers">
                <div class="live-number${moved ? ' changed' : ''}">
                    <div class="stat-label">${esc(t('live.position'))}</div>
                    <div class="stat-value">${state.position}</div>
                </div>
                <div class="live-number">
                    <div class="stat-label">${esc(t('live.eta'))}</div>
                    <div class="stat-value">${esc(state.eta === 0 ? t('live.now') : t('live.minutes', { n: state.eta }))}</div>
                </div>
            </div>
            <p class="live-message">${UI.icon(ahead === 0 ? 'bell-ringing' : 'users-three')}<span>${esc(ahead === 0 ? t('live.next') : t('live.ahead', { n: ahead }))}</span></p>`;
    } else {
        announcedCounter = null;
        $('live-body').innerHTML = `
            <p class="live-message">${UI.icon('map-pin')}<span>${esc(t('live.notArrived'))}</span></p>
            <p class="muted live-waiting">${esc(t('live.waitingNow', { n: snapshot.waiting.length }))}</p>`;
    }
    $('live-updated').textContent = t('live.updated', { time: I18N.formatTime(snapshot.updatedAt.substring(11, 16)) });
}

/** If the queue shows a different status than our copy of the booking, reload it (timeline, badge). */
function syncStatus() {
    const shown = liveState().status;
    const expected = booking.status === 'BOOKED' ? 'NOT_IN_QUEUE' : booking.status;
    if (shown !== expected) {
        clearTimeout(reloadTimer);
        reloadTimer = setTimeout(async () => {
            try {
                booking = await Api.get(`/api/bookings/${bookingId}`);
                render();
            } catch {
                // keep showing what we have; the next update will try again
            }
        }, 300);
    }
}

$('cancel-btn').addEventListener('click', async () => {
    const sure = await UI.confirm({
        title: t('booking.cancel'), message: t('booking.cancelConfirm'),
        confirmText: t('booking.cancel'), cancelText: t('booking.keep'), danger: true,
    });
    if (!sure) return;
    const button = $('cancel-btn');
    button.disabled = true;
    try {
        booking = await Api.post(`/api/bookings/${bookingId}/cancel`);
        render();
        showAlert(t('booking.cancelled'), 'success');
    } catch (error) {
        showAlert(Api.errorMessage(error));
    } finally {
        button.disabled = false;
    }
});

document.addEventListener('langchange', render);

async function init() {
    // Ask for the booking straight away, while the page texts (language file) are still loading.
    const loading = bookingId && Auth.user()?.role === 'FARMER' ? Api.get(`/api/bookings/${bookingId}`) : null;
    loading?.catch(() => {});   // an error is shown below, once the page is ready
    const me = await Farmer.initPage();
    if (!me) return;
    if (!bookingId) {
        location.replace('/farmer/dashboard.html');
        return;
    }
    try {
        booking = await loading;
        render();
        scrollToFeedback();
    } catch (error) {
        showAlert(Api.errorMessage(error));
    }
}

init();
