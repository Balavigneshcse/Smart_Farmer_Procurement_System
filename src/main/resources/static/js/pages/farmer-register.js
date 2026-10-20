/*
 * New farmer registration (/farmer/register.html), right after the email address is verified.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const $ = id => document.getElementById(id);
const pending = Auth.pendingRegistration();

// This page is only reachable right after verifying a new email address.
if (!pending?.token) {
    location.replace('/farmer/login.html');
}

function showAlert(text) {
    $('alert').textContent = text;
    $('alert').className = 'alert alert-error';
}

function setFieldError(inputId, messageKey) {
    $(inputId).setAttribute('aria-invalid', 'true');
    $(`${inputId}-error`).textContent = I18N.t(messageKey);
}

function clearFieldErrors() {
    ['name', 'mobile', 'address', 'district', 'aadhaar'].forEach(id => {
        $(id).removeAttribute('aria-invalid');
        $(`${id}-error`).textContent = '';
    });
}

/** Returns the form values, or null (with errors shown) if something is missing. */
function readForm() {
    const values = {
        name: $('name').value.trim(),
        mobile: $('mobile').value.trim(),
        address: $('address').value.trim(),
        district: $('district').value.trim(),
        aadhaarLast4: $('aadhaar').value.trim(),
        language: document.querySelector('input[name="language"]:checked')?.value || I18N.lang,
    };
    const invalid = [];
    if (values.name.length < 2) invalid.push(['name', 'validation.name']);
    if (!/^[6-9]\d{9}$/.test(values.mobile)) invalid.push(['mobile', 'validation.mobile']);
    if (!values.address) invalid.push(['address', 'validation.address']);
    if (!values.district) invalid.push(['district', 'validation.district']);
    if (!/^\d{4}$/.test(values.aadhaarLast4)) invalid.push(['aadhaar', 'validation.aadhaar']);

    invalid.forEach(([id, key]) => setFieldError(id, key));
    if (invalid.length) {
        $(invalid[0][0]).focus();
        return null;
    }
    return values;
}

function renderEmail() {
    $('email-verified').replaceChildren();
    $('email-verified').insertAdjacentHTML('afterbegin', UI.icon('seal-check'));
    $('email-verified').append(I18N.t('register.emailVerified', { email: pending?.email ?? '' }));
}

// Picking a language switches the page straight away.
document.querySelectorAll('input[name="language"]').forEach(radio => {
    radio.addEventListener('change', () => I18N.setLang(radio.value));
});

document.addEventListener('langchange', () => {
    renderEmail();
    const radio = document.querySelector(`input[name="language"][value="${I18N.lang}"]`);
    if (radio) radio.checked = true;
});

// Only digits in the number fields.
['mobile', 'aadhaar'].forEach(id => $(id).addEventListener('input', event => {
    event.target.value = event.target.value.replace(/\D/g, '');
}));

$('register-form').addEventListener('submit', async event => {
    event.preventDefault();
    clearFieldErrors();
    $('alert').className = 'alert hidden';
    const values = readForm();
    const payout = Payout.read();      // both are checked, so every mistake shows at once
    if (!values || !payout) {
        if (!values) $(document.querySelector('#register-form [aria-invalid]').id).focus();
        return;
    }
    values.payout = payout;

    const button = $('submit-btn');
    button.disabled = true;
    button.textContent = I18N.t('register.submitting');
    try {
        const result = await Api.post('/api/auth/register', { registrationToken: pending.token, ...values });
        Auth.save(result.token, result.user);
        Auth.clearPendingRegistration();
        location.replace(pending.next || '/farmer/dashboard.html');
    } catch (error) {
        showAlert(Api.errorMessage(error));
        if (error.code === 'REGISTRATION_EXPIRED' || error.code === 'ALREADY_REGISTERED') {
            Auth.clearPendingRegistration();
            setTimeout(() => location.replace('/farmer/login.html'), 3000);
        }
        button.disabled = false;
        button.textContent = I18N.t('register.submit');
    }
});

async function loadDistrictSuggestions() {
    try {
        const centres = await Api.get('/api/centres');
        const districts = [...new Set(centres.map(c => c.district))].sort();
        $('districts').replaceChildren(...districts.map(d => new Option(d)));
    } catch {
        // Suggestions are optional - the farmer can still type their district.
    }
}

if (pending?.token) {
    I18N.init().then(() => {
        Payout.render($('payout'));
        renderEmail();
        loadDistrictSuggestions();
        $('name').focus();
    });
}
