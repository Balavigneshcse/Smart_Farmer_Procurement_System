/*
 * Translations for farmer-facing pages (English, Tamil, Hindi).
 *
 * In HTML:  <h1 data-i18n="landing.hero.title">English fallback</h1>
 *           <input data-i18n-placeholder="login.mobile.placeholder">   <img data-i18n-alt="landing.photo.hero">
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

    let lang = 'en';          // the language the page is shown in
    let wanted = 'en';        // the language last asked for (its file may still be downloading)
    let chosenByUser = false; // the farmer clicked a language button on this page
    let dict = {};
    let english = {};
    const cache = {};         // language code -> Promise of its texts

    function savedLang() {
        try {
            return localStorage.getItem(STORAGE_KEY);
        } catch {
            return null;
        }
    }

    /** Each language file is fetched once; two quick clicks share the same download. */
    function loadDictionary(code) {
        if (!cache[code]) {
            cache[code] = fetch(`/i18n/${code}.json`)
                .then(res => (res.ok ? res.json() : {}))
                .catch(() => {
                    delete cache[code];   // try again next time (network hiccup)
                    return {};
                });
        }
        return cache[code];
    }

    /** Translate a key; {placeholders} are filled from params. Falls back to English, then the key. */
    function t(key, params = {}) {
        const text = dict[key] ?? english[key] ?? key;
        return text.replace(/\{(\w+)\}/g, (_, name) => (name in params ? params[name] : `{${name}}`));
    }

    function apply(root = document) {
        root.querySelectorAll('[data-i18n]').forEach(el => {
            el.textContent = t(el.dataset.i18n);
        });
        root.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
            el.placeholder = t(el.dataset.i18nPlaceholder);
        });
        root.querySelectorAll('[data-i18n-alt]').forEach(el => {
            el.alt = t(el.dataset.i18nAlt);
        });
        document.documentElement.lang = lang;
        document.querySelectorAll('.lang-switch [data-lang]').forEach(btn => {
            btn.setAttribute('aria-pressed', String(btn.dataset.lang === lang));
        });
    }

    /**
     * Shows the page in another language. On a slow connection the language files can arrive out of
     * order (Hindi asked first, English clicked meanwhile), so only the language asked for last is applied.
     */
    async function setLang(code) {
        const target = SUPPORTED.includes(code) ? code : 'en';
        wanted = target;
        try {
            localStorage.setItem(STORAGE_KEY, target);
        } catch {
            // Private mode etc. - the language just won't be remembered.
        }
        // the button lights up at once; the texts follow when their file has arrived
        document.querySelectorAll('.lang-switch [data-lang]').forEach(btn => {
            btn.setAttribute('aria-pressed', String(btn.dataset.lang === target));
        });
        const [en, texts] = await Promise.all([loadDictionary('en'), loadDictionary(target)]);
        if (target !== wanted) return;   // another language was chosen while this one was loading
        english = en;
        dict = texts;
        lang = target;
        apply();
        document.dispatchEvent(new CustomEvent('langchange', { detail: { lang } }));
    }

    /**
     * Loads the saved language and wires the language buttons.
     * options.onUserChange(lang, shown) runs when the user clicks a language button (farmer pages use
     * it to save the choice to their profile); shown is a promise that settles once the page shows it.
     */
    async function init(options = {}) {
        document.querySelectorAll('.lang-switch [data-lang]').forEach(btn => {
            btn.addEventListener('click', () => {
                const code = btn.dataset.lang;
                if (code === wanted) return;
                chosenByUser = true;
                const shown = setLang(code);
                // saving starts at once, alongside the download of the language file
                options.onUserChange?.(code, shown);
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

    /** 2369 -> "₹2,369", 58040.5 -> "₹58,040.50" (paise always shown with 2 digits). */
    function formatMoney(amount) {
        const digits = Math.round(Number(amount) * 100) % 100 === 0 ? 0 : 2;
        return new Intl.NumberFormat(locale(), {
            style: 'currency', currency: 'INR', maximumFractionDigits: digits, minimumFractionDigits: digits,
        }).format(amount);
    }

    return {
        init, t, setLang, apply, locale, formatTime, formatDate, formatDateTime, formatMoney,
        get lang() { return lang; },
        /** True once the farmer clicked a language button; the page then keeps their choice. */
        get chosenByUser() { return chosenByUser; },
    };
})();
