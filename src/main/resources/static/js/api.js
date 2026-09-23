/*
 * Small wrapper around fetch() for calling the backend.
 * Throws an Error with the server's message when the response is not OK.
 */
const Api = (() => {
    async function request(method, path, body) {
        const options = { method, headers: { Accept: 'application/json' } };
        if (body !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(body);
        }

        const res = await fetch(path, options);
        const data = res.status === 204 ? null : await res.json().catch(() => null);
        if (!res.ok) {
            const error = new Error(data?.message || `Request failed (${res.status})`);
            error.status = res.status;
            error.data = data;
            throw error;
        }
        return data;
    }

    return {
        get: path => request('GET', path),
        post: (path, body) => request('POST', path, body ?? {}),
        put: (path, body) => request('PUT', path, body ?? {}),
    };
})();
