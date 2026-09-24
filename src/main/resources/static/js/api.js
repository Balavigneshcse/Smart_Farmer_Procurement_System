/*
 * Small wrapper around fetch() for calling the backend.
 *  - adds the login token (if auth.js is loaded and the user is logged in)
 *  - throws an Error with .status, .code, .params and .fieldErrors when the response is not OK
 *  - if the server says our token is no longer valid (401), logs out and goes to the login page
 */
const Api = (() => {
    async function request(method, path, body) {
        const token = typeof Auth !== 'undefined' ? Auth.token() : null;
        const options = { method, headers: { Accept: 'application/json' } };
        if (token) {
            options.headers.Authorization = `Bearer ${token}`;
        }
        if (body !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }

        let res;
        try {
            res = await fetch(path, options);
        } catch {
            const error = new Error('Could not reach the server. Check your internet and try again.');
            error.code = 'NETWORK';
            throw error;
        }

        const data = res.status === 204 ? null : await res.json().catch(() => null);
        if (res.status === 401 && token) {
            Auth.expire();
        }
        if (!res.ok) {
            const error = new Error(data?.message || `Request failed (${res.status})`);
            error.status = res.status;
            error.code = data?.code;
            error.params = data?.params || {};
            error.fieldErrors = data?.fieldErrors || {};
            throw error;
        }
        return data;
    }

    /** Best message for an error: translated by its code on farmer pages, else the server's English text. */
    function errorMessage(error) {
        if (typeof I18N !== 'undefined' && error.code) {
            const key = `error.${error.code}`;
            const text = I18N.t(key, error.params);
            if (text !== key) return text;
        }
        return error.message;
    }

    return {
        get: path => request('GET', path),
        post: (path, body) => request('POST', path, body ?? {}),
        put: (path, body) => request('PUT', path, body ?? {}),
        delete: path => request('DELETE', path),
        errorMessage,
    };
})();
