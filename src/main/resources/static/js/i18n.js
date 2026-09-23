/*
 * Translations for farmer-facing pages (English, Tamil, Hindi).
 *
 * In HTML:  <h1 data-i18n="landing.hero.title">English fallback</h1>
 *           <input data-i18n-placeholder="login.mobile.placeholder">
 * In JS:    I18N.t('centres.counters', { n: 3 })
 *
 * The chosen language is remembered in the browser. After a farmer logs in,
 * their registered language is applied instead (see farmer pages).
 * Operator and admin pages are English only and don't load this file.
 */
const I18N = (() => {
    const SUPPORTED = ['en', 'ta', 'hi'];
    const LOCALES = { en: 'en-IN', ta: 'ta-IN', hi: 'hi-IN' };
    const STORAGE_KEY = 'sfps.lang';

    let lang = 'en';
    let dict = {};
    const cache = {};

    function savedLang() {
        try {
            return localStorage.getItem(STORAGE_KEY);
        } catch {
            return null;
        }
    }

    async function loadDictionary(code) {
        if (!cache[code]) {
            const res = await fetch(`/i18n/${code}.json`);
            cache[code] = res.ok ? await res.json() : {};
        }
        return cache[code];
    }

    /** Translate a key; {placeholders} are filled from params. Falls back to English, then the key. */
    function t(key, params = {}) {
        const text = dict[key] ?? cache.en?.[key] ?? key;
        return text.replace(/\{(\w+)\}/g, (_, name) => (name in params ? params[name] : `{${name}}`));
    }

    function apply(root = document) {
        root.querySelectorAll('[data-i18n]').forEach(el => {
            el.textContent = t(el.dataset.i18n);
        });
        root.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
            el.placeholder = t(el.dataset.i18nPlaceholder);
        });
        document.documentElement.lang = lang;
        document.querySelectorAll('.lang-switch [data-lang]').forEach(btn => {
            btn.setAttribute('aria-pressed', String(btn.dataset.lang === lang));
        });
    }

    async function setLang(code) {
        lang = SUPPORTED.includes(code) ? code : 'en';
        try {
            localStorage.setItem(STORAGE_KEY, lang);
        } catch {
            // Private mode etc. - the language just won't be remembered.
        }
        await loadDictionary('en');
        dict = await loadDictionary(lang);
        apply();
        document.dispatchEvent(new CustomEvent('langchange', { detail: { lang } }));
    }

    /**
     * Loads the saved language and wires the language buttons.
     * options.onUserChange(lang) runs when the user clicks a language button
     * (farmer pages use it to save the choice to their profile).
     */
    async function init(options = {}) {
        document.querySelectorAll('.lang-switch [data-lang]').forEach(btn => {
            btn.addEventListener('click', async () => {
                if (btn.dataset.lang === lang) return;
                await setLang(btn.dataset.lang);
                options.onUserChange?.(lang);
            });
        });
        await setLang(savedLang() || 'en');
    }

    function locale() {
        return LOCALES[lang];
    }

    /** "08:00:00" -> "8:00 am" in the current language. */
    function formatTime(hhmmss) {
        const [h, m] = hhmmss.split(':').map(Number);
        const date = new Date(2000, 0, 1, h, m);
        return date.toLocaleTimeString(locale(), { hour: 'numeric', minute: '2-digit' });
    }

    /** "2026-09-24" -> "Thu, 24 Sep" in the current language (options change the format). */
    function formatDate(isoDate, options = { weekday: 'short', day: 'numeric', month: 'short' }) {
        const [y, m, d] = isoDate.split('-').map(Number);
        return new Date(y, m - 1, d).toLocaleDateString(locale(), options);
    }

    /** "2026-09-24T09:05:12" -> "24 Sep, 9:05 am" in the current language. */
    function formatDateTime(isoDateTime) {
        return new Date(isoDateTime).toLocaleString(locale(), {
            day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit',
        });
    }

    /** 2369 -> "₹2,369" in the current language. */
    function formatMoney(amount) {
        return new Intl.NumberFormat(locale(), {
            style: 'currency', currency: 'INR', maximumFractionDigits: 2, minimumFractionDigits: 0,
        }).format(amount);
    }

    return {
        init, t, setLang, apply, locale, formatTime, formatDate, formatDateTime, formatMoney,
        get lang() { return lang; },
    };
})();
