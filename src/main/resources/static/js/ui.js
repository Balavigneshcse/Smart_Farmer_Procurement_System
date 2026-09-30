/*
 * Small UI helpers shared by all pages: icons, a styled confirm dialog, loading skeletons
 * and the gentle "appear on scroll" effect of the landing page.
 */
const UI = (() => {
    const SPRITE = '/img/icons.svg';
    const motionAllowed = window.matchMedia?.('(prefers-reduced-motion: no-preference)').matches ?? false;

    /** An icon from the Phosphor sprite, e.g. UI.icon('ticket'). Decorative: screen readers skip it. */
    function icon(name, extraClass = '') {
        return `<svg class="icon ${extraClass}" aria-hidden="true" focusable="false"><use href="${SPRITE}#${name}"></use></svg>`;
    }

    /** Grey placeholder lines shaped like the content that is loading. */
    function skeleton(lines = 3, loadingText = 'Loading…') {
        const widths = ['92%', '74%', '84%', '60%', '88%'];
        return `<div aria-busy="true"><span class="sr-only">${loadingText}</span>`
            + Array.from({ length: lines }, (_, i) =>
                `<span class="skeleton skeleton-line" style="width: ${widths[i % widths.length]}"></span>`).join('')
            + '</div>';
    }

    /**
     * Asks a yes/no question in a dialog that matches the app. Resolves true when confirmed.
     * Falls back to the browser's own confirm() on very old browsers.
     */
    function confirm({ title, message, confirmText, cancelText, danger = false }) {
        if (typeof HTMLDialogElement !== 'function') {
            return Promise.resolve(window.confirm(message));
        }
        const dialog = document.createElement('dialog');
        dialog.className = 'dialog';
        dialog.innerHTML = `
            <form method="dialog">
                <h2></h2>
                <p></p>
                <div class="dialog-actions">
                    <button class="btn btn-quiet" value="cancel"></button>
                    <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" value="ok"></button>
                </div>
            </form>`;
        dialog.querySelector('h2').textContent = title;
        dialog.querySelector('p').textContent = message;
        dialog.querySelector('[value="cancel"]').textContent = cancelText;
        dialog.querySelector('[value="ok"]').textContent = confirmText;
        document.body.appendChild(dialog);

        return new Promise(resolve => {
            dialog.addEventListener('close', () => {
                resolve(dialog.returnValue === 'ok');
                dialog.remove();
            });
            dialog.showModal();
            dialog.querySelector('[value="cancel"]').focus();   // the safe choice has the focus
        });
    }

    /** Elements with data-reveal fade in once when they scroll into view (landing page only). */
    function revealOnScroll() {
        if (!motionAllowed || !('IntersectionObserver' in window)) return;
        document.documentElement.classList.add('js-motion');
        const observer = new IntersectionObserver(entries => {
            entries.forEach(entry => {
                if (!entry.isIntersecting) return;
                entry.target.classList.add('is-visible');
                observer.unobserve(entry.target);
            });
        }, { rootMargin: '0px 0px -10% 0px' });
        document.querySelectorAll('[data-reveal]').forEach(el => observer.observe(el));
    }

    return { icon, skeleton, confirm, revealOnScroll, motionAllowed };
})();
