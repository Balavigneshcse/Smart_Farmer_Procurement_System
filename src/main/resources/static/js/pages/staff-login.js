/*
 * Staff login (/staff/login.html) for operators, centre admins and the super admin.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const $ = id => document.getElementById(id);

// Already logged in as staff in this tab? Offer to continue, but still allow logging in as someone else.
const ROLE_NAMES = { SUPER_ADMIN: 'super admin', ADMIN: 'centre admin', OPERATOR: 'staff' };
const current = Auth.user();
if (current && current.role !== 'FARMER') {
    $('signed-in-text').textContent = `This tab is logged in as ${current.name} (${ROLE_NAMES[current.role] ?? current.role}).`;
    $('continue-link').href = Auth.homeFor(current.role);
    $('signed-in').classList.remove('hidden');
}

function showAlert(text, type = 'error') {
    $('alert').textContent = text;
    $('alert').className = `alert alert-${type}`;
}

function setFieldError(id, text) {
    $(id).setAttribute('aria-invalid', 'true');
    $(`${id}-error`).textContent = text;
}

function clearErrors() {
    $('alert').className = 'alert hidden';
    ['username', 'password'].forEach(id => {
        $(id).removeAttribute('aria-invalid');
        $(`${id}-error`).textContent = '';
    });
}

$('toggle-password').addEventListener('click', () => {
    const show = $('password').type === 'password';
    $('password').type = show ? 'text' : 'password';
    $('toggle-password').textContent = show ? 'Hide' : 'Show';
    $('toggle-password').setAttribute('aria-label', show ? 'Hide password' : 'Show password');
});

$('login-form').addEventListener('submit', async event => {
    event.preventDefault();
    clearErrors();
    const username = $('username').value.trim();
    const password = $('password').value;
    if (!username) setFieldError('username', 'Enter your username.');
    if (!password) setFieldError('password', 'Enter your password.');
    if (!username || !password) return;

    const button = $('login-btn');
    button.disabled = true;
    button.textContent = 'Logging in…';
    try {
        const result = await Api.post('/api/auth/login', { username, password });
        Auth.save(result.token, result.user);
        location.replace(Auth.homeFor(result.user.role));
    } catch (error) {
        showAlert(Api.errorMessage(error));
        $('password').value = '';
        $('password').focus();
        button.disabled = false;
        button.textContent = 'Log in';
    }
});

if (new URLSearchParams(location.search).get('expired')) {
    showAlert('Your session has ended. Please log in again.', 'info');
}
$('username').focus();
