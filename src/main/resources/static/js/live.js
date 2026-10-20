/*
 * Live queue updates (Server-Sent Events) for the operator desk, the TV and the farmer's booking page.
 *
 * A browser keeps at most 6 connections open to one server (HTTP/1.1), and every open live page uses
 * one all the time. So a hidden tab closes its connection and opens it again when it is shown;
 * the server then sends the current queue straight away, so nothing is missed.
 */
const LiveQueue = {
    /**
     * onSnapshot(queue) is called with every new queue; onStatus(true/false) when the connection goes up or down.
     * Returns { close() } to stop for good.
     */
    connect(centreId, { onSnapshot, onStatus = () => {} }) {
        const FIRST_RETRY_MS = 2000;
        const MAX_RETRY_MS = 30000;
        let source = null;
        let retryTimer = null;
        let retryDelay = FIRST_RETRY_MS;

        function connected() {
            retryDelay = FIRST_RETRY_MS;    // working again: the next outage starts with a short wait
            onStatus(true);
        }

        function open() {
            if (source) return;
            clearTimeout(retryTimer);
            retryTimer = null;
            const current = new EventSource(`/api/queue/${centreId}/stream`);
            source = current;
            current.addEventListener('open', connected);
            current.addEventListener('error', () => {
                onStatus(false);
                // After a dropped connection the browser tries again by itself. But when the server answers
                // with an error (e.g. 502 from Nginx while the app restarts) the browser gives up for good
                // (CLOSED), so we open a new connection ourselves: after 2 s, then 4 s, 8 s ... at most 30 s.
                if (current === source && current.readyState === EventSource.CLOSED) {
                    current.close();
                    source = null;
                    retryTimer = setTimeout(open, retryDelay);
                    retryDelay = Math.min(retryDelay * 2, MAX_RETRY_MS);
                }
            });
            current.addEventListener('queue', event => {
                connected();
                onSnapshot(JSON.parse(event.data));
            });
        }

        function pause() {
            clearTimeout(retryTimer);
            retryTimer = null;
            source?.close();
            source = null;
        }

        function onVisibilityChange() {
            if (document.hidden) pause();
            else open();
        }

        document.addEventListener('visibilitychange', onVisibilityChange);
        if (!document.hidden) open();

        return {
            close() {
                document.removeEventListener('visibilitychange', onVisibilityChange);
                pause();
            },
        };
    },
};
