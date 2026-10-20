/*
 * Farmer payments (/admin/payments.html): the list, and paying with Razorpay Checkout (test mode).
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const $ = id => document.getElementById(id);
const { esc } = Admin;
const me = Admin.initPage();
let filter = 'PENDING';
let data = null;
let noticeTimer = null;

const money = amount => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount);
const date = iso => new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
const dateTime = iso => new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });

function showNotice(text, type = 'success', stay = false) {
    $('notice').textContent = text;
    $('notice').className = `alert alert-${type}`;
    clearTimeout(noticeTimer);
    if (!stay) noticeTimer = setTimeout(() => { $('notice').className = 'alert hidden'; }, 8000);
}

function render() {
    const { payments, summary, razorpayConfigured } = data;
    $('config-warning').classList.toggle('hidden', razorpayConfigured);
    $('s-pending').textContent = summary.pendingCount;
    $('s-pending-amount').textContent = money(summary.pendingAmount);
    $('s-today').textContent = money(summary.paidTodayAmount);
    $('s-today-count').textContent = `${summary.paidTodayCount} payment(s)`;
    $('s-total').textContent = money(summary.paidTotalAmount);

    $('rows').innerHTML = payments.length ? payments.map(p => `
        <tr>
            <td>${esc(date(p.bookingDate))}</td>
            <td><strong class="mono">${esc(p.token)}</strong></td>
            <td>${esc(p.farmerName)}<br><span class="small muted">+91 ${esc(p.farmerMobile)}</span><br>
                ${p.payTo ? `<span class="small">${esc(p.payTo)}</span>` : '<span class="badge badge-amber">No payment details yet</span>'}</td>
            <td>${esc(p.cropName)}<br><span class="small muted">${esc(p.weightQtl)} qtl, grade ${esc(p.grade)}<br>${esc(p.centreName)}</span></td>
            <td class="num"><strong>${esc(money(p.amount))}</strong></td>
            <td>${p.status === 'PAID'
                ? `<span class="badge">Paid</span><br><span class="small muted">${esc(dateTime(p.paidAt))}</span><br><span class="mono">${esc(p.rzpPaymentId)}</span>`
                : `<span class="badge badge-amber">${p.status === 'PENDING' ? 'Pending' : 'Failed'}</span>`}</td>
            <td class="num">${p.status !== 'PAID'
                ? `<button type="button" class="btn btn-primary btn-sm" data-pay="${p.id}" ${razorpayConfigured ? '' : 'disabled'}>Pay</button>`
                : ''}</td>
        </tr>`).join('')
        : `<tr class="empty-row"><td colspan="7">${filter === 'PENDING' ? 'No payments waiting.' : 'No payments yet.'}</td></tr>`;

    document.querySelectorAll('[data-filter]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.filter === filter)));
}

async function load() {
    try {
        data = await Api.get(`/api/admin/payments${filter ? `?status=${filter}` : ''}`);
        render();
    } catch (error) {
        showNotice(Api.errorMessage(error), 'error', true);
    }
}

// ---------- Razorpay's answer, kept until our server has checked it ----------

// If the connection drops (or the tab is closed) right after paying, our server never hears about it and
// the payment stays "pending". So Razorpay's answer is saved first, and sent again when this page opens.
const PENDING_VERIFY_KEY = 'sfps.pendingVerify';

function savePendingVerify(answer) {
    try {
        localStorage.setItem(PENDING_VERIFY_KEY, JSON.stringify(answer));
    } catch {
        // Storage blocked (private mode etc.) - the answer is still sent, it just cannot be sent again later.
    }
}

function readPendingVerify() {
    try {
        return JSON.parse(localStorage.getItem(PENDING_VERIFY_KEY));
    } catch {
        return null;
    }
}

/** Removes the saved answer, but only if it is this order's: all tabs share it, another may have saved a newer one. */
function clearPendingVerify(orderId) {
    try {
        if (readPendingVerify()?.razorpayOrderId === orderId) localStorage.removeItem(PENDING_VERIFY_KEY);
    } catch {
        // ignore
    }
}

/** The server has given its final word (wrong signature, unknown order), so sending the answer again cannot help. */
function isFinalAnswer(error) {
    return error.code === 'SIGNATURE_INVALID' || error.code === 'PAYMENT_NOT_FOUND'
        || error.status === 400 || error.status === 404;
}

/** Our server checks Razorpay's signature and marks the payment as paid. earlier = answer saved on a past visit. */
async function verify(answer, earlier = false) {
    try {
        const paid = await Api.post('/api/payments/verify', answer);
        clearPendingVerify(answer.razorpayOrderId);
        const text = `Paid ${money(paid.amount)} to ${paid.farmerName} (${paid.token}). Reference ${paid.rzpPaymentId}.`;
        showNotice(earlier ? `An earlier payment was confirmed. ${text}` : text);
    } catch (error) {
        // Network errors, server errors and an ended login are not final: the saved answer is kept.
        let text = Api.errorMessage(error);
        if (isFinalAnswer(error)) {
            clearPendingVerify(answer.razorpayOrderId);
        } else {
            text += ' The payment will be checked again the next time this page is opened.';
        }
        showNotice(earlier ? `An earlier payment could not be confirmed. ${text}` : text, 'error', true);
    }
}

/** A saved answer from a visit that ended before our server confirmed it: send it once more. */
async function retryPendingVerify() {
    const saved = readPendingVerify();
    if (saved?.razorpayOrderId) await verify(saved, true);
}

// ---------- Paying ----------

/** Pay: our server creates the Razorpay order, Checkout collects the (test) payment, our server verifies it. */
async function pay(paymentId, button) {
    if (typeof Razorpay === 'undefined') {
        showNotice('Razorpay Checkout did not load. Check your internet connection.', 'error', true);
        return;
    }
    button.disabled = true;
    let order;
    try {
        order = await Api.post(`/api/payments/${paymentId}/order`);
    } catch (error) {
        showNotice(Api.errorMessage(error), 'error', true);
        button.disabled = false;
        return;
    }

    if (order.alreadyPaid) {
        // Razorpay says this order was paid before (e.g. the connection dropped right after paying).
        // The server has now marked it as paid, so there is nothing left to pay: no Checkout.
        clearPendingVerify(order.orderId);
        showNotice(`Razorpay shows this payment was already made (ref ${order.rzpPaymentId}). It is now marked as paid.`, 'success', true);
        load();
        return;
    }

    const standIn = order.amountPaise < order.fullAmountPaise;
    if (standIn) {
        showNotice(`Test mode: Razorpay charges ${money(order.amountPaise / 100)} as a stand-in. `
            + `${order.farmerName} is recorded as paid the full ${money(order.fullAmountPaise / 100)}.`, 'info', true);
    }
    const checkout = new Razorpay({
        key: order.keyId,
        order_id: order.orderId,
        amount: order.amountPaise,
        currency: order.currency,
        name: 'Smart Farmer Procurement System',
        description: standIn ? `${order.description} (test stand-in for ${money(order.fullAmountPaise / 100)})` : order.description,
        prefill: { name: order.farmerName, contact: `+91${order.farmerMobile}` },
        notes: { paymentId: String(order.paymentId) },
        theme: { color: '#1d6a40' },
        handler: async response => {
            const answer = {
                razorpayOrderId: response.razorpay_order_id,
                razorpayPaymentId: response.razorpay_payment_id,
                razorpaySignature: response.razorpay_signature,
            };
            savePendingVerify(answer);    // saved before sending, see above
            await verify(answer);
            load();
        },
        modal: {
            ondismiss: () => {
                button.disabled = false;
            },
        },
    });
    checkout.on('payment.failed', response => {
        showNotice(`Payment failed: ${response.error.description}`, 'error', true);
    });
    checkout.open();
}

$('rows').addEventListener('click', event => {
    const button = event.target.closest('[data-pay]');
    if (button) pay(Number(button.dataset.pay), button);
});

document.querySelectorAll('[data-filter]').forEach(button => button.addEventListener('click', () => {
    filter = button.dataset.filter;
    load();
}));

if (me) {
    retryPendingVerify().then(load);
}
