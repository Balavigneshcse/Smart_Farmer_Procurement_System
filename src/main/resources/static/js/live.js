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
        let source = null;

        function open() {
            if (source) return;
            source = new EventSource(`/api/queue/${centreId}/stream`);
            source.addEventListener('open', () => onStatus(true));
            source.addEventListener('error', () => onStatus(false));
            source.addEventListener('queue', event => {
                onStatus(true);
                onSnapshot(JSON.parse(event.data));
            });
        }

        function pause() {
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
