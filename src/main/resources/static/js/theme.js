/*
 * Light / dark theme. Loaded in <head> so the page is drawn in the right colours from the start.
 * The choice is remembered in the browser; until someone chooses, the device setting is used.
 *
 * A page can change two things on its <html> element:
 *  - data-theme-key: where the choice is remembered (the TV display keeps its own, so a laptop that uses
 *    the light theme for the admin pages still shows the TV board in dark)
 *  - data-theme-default: the theme to use until someone chooses, instead of the device setting
 * ?theme=light or ?theme=dark in the address chooses the theme too (handy on a TV without a mouse).
 */
(() => {
    const root = document.documentElement;
    const KEY = root.dataset.themeKey || 'sfps.theme';
    const fallback = root.dataset.themeDefault;
    const THEMES = ['light', 'dark'];

    function saved() {
        try {
            return localStorage.getItem(KEY);
        } catch {
            return null;
        }
    }

    function remember(theme) {
        try {
            localStorage.setItem(KEY, theme);
        } catch {
            // Private mode: the theme still changes, it just is not remembered.
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

    const fromAddress = new URLSearchParams(location.search).get('theme');
    if (THEMES.includes(fromAddress)) remember(fromAddress);

    const system = window.matchMedia('(prefers-color-scheme: dark)');
    apply(saved() || fallback || (system.matches ? 'dark' : 'light'));
    system.addEventListener('change', event => {
        if (!saved() && !fallback) apply(event.matches ? 'dark' : 'light');
    });

    document.addEventListener('DOMContentLoaded', () => {
        apply(root.dataset.theme);   // label the buttons now that they exist
        document.querySelectorAll('.theme-toggle').forEach(button => button.addEventListener('click', () => {
            const next = root.dataset.theme === 'dark' ? 'light' : 'dark';
            remember(next);
            apply(next);
        }));
    });
})();
