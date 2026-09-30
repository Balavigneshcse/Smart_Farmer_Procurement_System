/*
 * Light / dark theme. Loaded in <head> so the page is drawn in the right colours from the start.
 * The choice is remembered in the browser; until someone chooses, the device setting is used.
 */
(() => {
    const KEY = 'sfps.theme';
    const root = document.documentElement;

    function saved() {
        try {
            return localStorage.getItem(KEY);
        } catch {
            return null;
        }
    }

    function apply(theme) {
        root.dataset.theme = theme;
        document.querySelectorAll('.theme-toggle').forEach(button => {
            const label = theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme';
            button.setAttribute('aria-label', label);
            button.title = label;
        });
        document.dispatchEvent(new CustomEvent('themechange', { detail: { theme } }));
    }

    const system = window.matchMedia('(prefers-color-scheme: dark)');
    apply(saved() || (system.matches ? 'dark' : 'light'));
    system.addEventListener('change', event => {
        if (!saved()) apply(event.matches ? 'dark' : 'light');
    });

    document.addEventListener('DOMContentLoaded', () => {
        apply(root.dataset.theme);   // label the buttons now that they exist
        document.querySelectorAll('.theme-toggle').forEach(button => button.addEventListener('click', () => {
            const next = root.dataset.theme === 'dark' ? 'light' : 'dark';
            try {
                localStorage.setItem(KEY, next);
            } catch {
                // Private mode: the theme still changes, it just is not remembered.
            }
            apply(next);
        }));
    });
})();
