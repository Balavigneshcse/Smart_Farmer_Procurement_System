/*
 * Farmer login (/farmer/login.html): the email address, then the OTP sent to it.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
// Where to go after login (only these pages are allowed, so the link can't send users elsewhere).
const NEXT_PAGES = { dashboard: '/farmer/dashboard.html', book: '/farmer/book.html' };
const params = new URLSearchParams(location.search);
const nextPage = NEXT_PAGES[params.get('next')] || NEXT_PAGES.dashboard;

const $ = id => document.getElementById(id);
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
let email = '';
let resendLeft = 0;
let resendTimer = null;

// Already logged in as a farmer? Skip the login page.
if (Auth.user()?.role === 'FARMER') {
    location.replace(nextPage);
}

function showAlert(text, type = 'error') {
    $('alert').textContent = text;
    $('alert').className = `alert alert-${type}`;
}

function hideAlert() {
    $('alert').className = 'alert hidden';
}

function setFieldError(inputId, messageKey) {
    $(inputId).setAttribute('aria-invalid', 'true');
    $(`${inputId}-error`).textContent = I18N.t(messageKey);
    $(inputId).focus();
}

function clearFieldError(inputId) {
    $(inputId).removeAttribute('aria-invalid');
    $(`${inputId}-error`).textContent = '';
}

function setBusy(button, busyKey, busy) {
    button.disabled = busy;
    button.textContent = I18N.t(busy ? busyKey : button.dataset.i18n);
}

function renderOtpStepText() {
    $('otp-sent-to').textContent = I18N.t('login.otpSentTo', { email });
    $('resend-btn').disabled = resendLeft > 0;
    $('resend-btn').textContent = resendLeft > 0
        ? I18N.t('login.resendIn', { seconds: resendLeft })
        : I18N.t('login.resend');
}

function startResendCountdown(seconds) {
    resendLeft = Math.ceil(seconds);
    clearInterval(resendTimer);
    renderOtpStepText();
    resendTimer = setInterval(() => {
        resendLeft -= 1;
        renderOtpStepText();
        if (resendLeft <= 0) clearInterval(resendTimer);
    }, 1000);
}

function showOtpStep() {
    $('email-form').classList.add('hidden');
    $('otp-form').classList.remove('hidden');
    $('otp').value = '';
    clearFieldError('otp');
    renderOtpStepText();
    $('otp').focus();
}

function showEmailStep() {
    clearInterval(resendTimer);
    resendLeft = 0;
    hideAlert();
    $('otp-form').classList.add('hidden');
    $('email-form').classList.remove('hidden');
    $('email').focus();
}

async function requestOtp() {
    const result = await Api.post('/api/auth/otp/send', { email, language: I18N.lang });
    startResendCountdown(result.resendAfterSeconds);
}

$('email-form').addEventListener('submit', async event => {
    event.preventDefault();
    hideAlert();
    const value = $('email').value.trim().toLowerCase();
    if (!EMAIL_PATTERN.test(value) || value.length > 254) {
        setFieldError('email', 'validation.email');
        return;
    }
    clearFieldError('email');
    email = value;

    const button = $('send-btn');
    setBusy(button, 'login.sending', true);
    try {
        await requestOtp();
        showOtpStep();
    } catch (error) {
        if (error.code === 'OTP_COOLDOWN') {
            // An OTP was sent a moment ago (e.g. page refreshed) - let them type it.
            showOtpStep();
            startResendCountdown(error.params.seconds);
        }
        showAlert(Api.errorMessage(error));
    } finally {
        setBusy(button, 'login.sending', false);
    }
});

$('otp-form').addEventListener('submit', async event => {
    event.preventDefault();
    hideAlert();
    const code = $('otp').value.replace(/\D/g, '');
    if (!/^\d{6}$/.test(code)) {
        setFieldError('otp', 'validation.otp');
        return;
    }
    clearFieldError('otp');

    const button = $('verify-btn');
    setBusy(button, 'login.verifying', true);
    try {
        const result = await Api.post('/api/auth/otp/verify', { email, otp: code });
        if (result.registered) {
            Auth.save(result.token, result.user);
            await I18N.setLang(result.user.language);   // show the farmer's own language from now on
            location.replace(nextPage);
            return;
        }
        Auth.setPendingRegistration({ token: result.registrationToken, email, next: nextPage });
        location.href = '/farmer/register.html';
    } catch (error) {
        showAlert(Api.errorMessage(error));
        if (error.code === 'OTP_EXPIRED' || error.code === 'OTP_TOO_MANY_ATTEMPTS') {
            $('otp').value = '';
        }
        setBusy(button, 'login.verifying', false);
    }
});

$('resend-btn').addEventListener('click', async () => {
    hideAlert();
    $('resend-btn').disabled = true;
    try {
        await requestOtp();
        showAlert(I18N.t('login.otpResent'), 'success');
    } catch (error) {
        if (error.code === 'OTP_COOLDOWN') startResendCountdown(error.params.seconds);
        else renderOtpStepText();
        showAlert(Api.errorMessage(error));
    }
});

$('change-btn').addEventListener('click', showEmailStep);

// Only digits in the OTP field.
$('otp').addEventListener('input', event => {
    event.target.value = event.target.value.replace(/\D/g, '');
});

document.addEventListener('langchange', () => {
    hideAlert();
    if (email) renderOtpStepText();
});

I18N.init().then(() => {
    if (params.get('expired')) showAlert(I18N.t('login.expired'), 'info');
});
