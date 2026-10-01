/*
 * Keeps the login token in the browser and guards pages by role.
 *
 * Where the login is kept:
 *  - farmers: localStorage, shared by all tabs and kept after the browser closes (7-day login)
 *  - staff, centre admins, super admin: sessionStorage, so every tab has its own login.
 *    An admin can work in one tab and log in as staff in another.
 *
 *   const me = Auth.require('FARMER');   // at the top of a farmer page
 *   if (!me) { ...page is redirecting to login... }
 */
const Auth = (() => {
    const SESSION_KEY = 'sfps.session';
    const REGISTRATION_KEY = 'sfps.registration';
    const LOGIN_PAGES = { FARMER: '/farmer/login.html' };   // every staff role uses /staff/login.html
    const HOME_PAGES = {
        FARMER: '/farmer/dashboard.html', OPERATOR: '/operator/desk.html',
        ADMIN: '/admin/dashboard.html', SUPER_ADMIN: '/admin/dashboard.html',
    };

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

    function valid(saved) {
        return saved?.token && !isExpired(saved.token) ? saved : null;
    }

    /** This tab's staff login if there is one, otherwise the farmer login shared by all tabs. */
    function session() {
        const tab = valid(readJson(sessionStorage, SESSION_KEY));
        if (tab) return tab;
        const shared = readJson(localStorage, SESSION_KEY);
        if (shared && shared.user?.role !== 'FARMER') {
            remove(localStorage, SESSION_KEY);    // a staff login saved by an older version: no longer shared
            return null;
        }
        return valid(shared);
    }

    function hadSession() {
        return readJson(sessionStorage, SESSION_KEY) !== null || readJson(localStorage, SESSION_KEY) !== null;
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
        const value = { token: newToken, user: newUser };
        if (newUser.role === 'FARMER') {
            remove(sessionStorage, SESSION_KEY);    // a staff login in this tab would hide the farmer's
            writeJson(localStorage, SESSION_KEY, value);
        } else {
            writeJson(sessionStorage, SESSION_KEY, value);
        }
    }

    function updateUser(changes) {
        const current = session();
        if (current) save(current.token, { ...current.user, ...changes });
    }

    /** Logs out the login this tab is using: its own staff login, or else the shared farmer login. */
    function clear() {
        if (readJson(sessionStorage, SESSION_KEY) !== null) {
            remove(sessionStorage, SESSION_KEY);
        } else {
            remove(localStorage, SESSION_KEY);
        }
    }

    /** Returns the logged-in user if they have this role (or one of these roles); otherwise redirects and returns null. */
    function require(roles) {
        const allowed = [].concat(roles);
        const role = allowed[0];
        const current = user();
        if (current && allowed.includes(current.role)) return current;

        if (current) {
            location.replace(homeFor(current.role));        // logged in, but with another role
        } else {
            const expired = hadSession();
            clear();
            location.replace(loginPageFor(role) + (expired ? '?expired=1' : ''));
        }
        return null;
    }

    /** Called when the server rejects our token (401). */
    function expire() {
        const role = (readJson(sessionStorage, SESSION_KEY) ?? readJson(localStorage, SESSION_KEY))?.user?.role;
        clear();
        location.replace(loginPageFor(role) + '?expired=1');
    }

    function logout() {
        const role = user()?.role;
        clear();
        location.replace(role === 'FARMER' ? '/' : '/staff/login.html');
    }

    // A new farmer's verified email, kept only until the registration form is submitted.
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
