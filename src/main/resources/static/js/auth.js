/*
 * Keeps the login token in the browser and guards pages by role.
 *
 *   const me = Auth.require('FARMER');   // at the top of a farmer page
 *   if (!me) { ...page is redirecting to login... }
 */
const Auth = (() => {
    const SESSION_KEY = 'sfps.session';
    const REGISTRATION_KEY = 'sfps.registration';
    const LOGIN_PAGES = { FARMER: '/farmer/login.html', OPERATOR: '/staff/login.html', ADMIN: '/staff/login.html' };
    const HOME_PAGES = { FARMER: '/farmer/dashboard.html', OPERATOR: '/operator/desk.html', ADMIN: '/admin/dashboard.html' };

    function readJson(storage, key) {
        try {
            return JSON.parse(storage.getItem(key));
        } catch {
            return null;
        }
    }

    function writeJson(storage, key, value) {
        try {
            storage.setItem(key, JSON.stringify(value));
        } catch {
            // Storage blocked (private mode etc.) - the user will need to log in again.
        }
    }

    function remove(storage, key) {
        try {
            storage.removeItem(key);
        } catch {
            // ignore
        }
    }

    /** Reads the "exp" (expiry) field inside the JWT without verifying it - the server does that. */
    function isExpired(token) {
        try {
            const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
            const padded = base64 + '='.repeat((4 - base64.length % 4) % 4);
            return JSON.parse(atob(padded)).exp * 1000 <= Date.now();
        } catch {
            return true;
        }
    }

    function session() {
        const saved = readJson(localStorage, SESSION_KEY);
        return saved?.token && !isExpired(saved.token) ? saved : null;
    }

    function loginPageFor(role) {
        return LOGIN_PAGES[role] || '/staff/login.html';
    }

    function homeFor(role) {
        return HOME_PAGES[role] || '/';
    }

    function user() {
        return session()?.user ?? null;
    }

    function token() {
        return session()?.token ?? null;
    }

    function save(newToken, newUser) {
        writeJson(localStorage, SESSION_KEY, { token: newToken, user: newUser });
    }

    function updateUser(changes) {
        const current = session();
        if (current) save(current.token, { ...current.user, ...changes });
    }

    function clear() {
        remove(localStorage, SESSION_KEY);
    }

    /** Returns the logged-in user if they have this role; otherwise redirects and returns null. */
    function require(role) {
        const current = user();
        if (current?.role === role) return current;

        if (current) {
            location.replace(homeFor(current.role));        // logged in, but with another role
        } else {
            const hadSession = readJson(localStorage, SESSION_KEY) !== null;
            clear();
            location.replace(loginPageFor(role) + (hadSession ? '?expired=1' : ''));
        }
        return null;
    }

    /** Called when the server rejects our token (401). */
    function expire() {
        const role = readJson(localStorage, SESSION_KEY)?.user?.role;
        clear();
        location.replace(loginPageFor(role) + '?expired=1');
    }

    function logout() {
        const role = user()?.role;
        clear();
        location.replace(role === 'FARMER' ? '/' : '/staff/login.html');
    }

    // A new farmer's verified mobile, kept only until the registration form is submitted.
    function setPendingRegistration(data) {
        writeJson(sessionStorage, REGISTRATION_KEY, data);
    }

    function pendingRegistration() {
        return readJson(sessionStorage, REGISTRATION_KEY);
    }

    function clearPendingRegistration() {
        remove(sessionStorage, REGISTRATION_KEY);
    }

    return {
        user, token, save, updateUser, clear, require, expire, logout, homeFor, loginPageFor,
        setPendingRegistration, pendingRegistration, clearPendingRegistration,
    };
})();
