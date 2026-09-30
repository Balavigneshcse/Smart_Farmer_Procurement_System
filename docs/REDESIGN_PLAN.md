# Frontend Redesign Plan (pre-build)

This is the preparation for the new frontend, done with two design skills from
[Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill), installed in `.claude/skills/`:

- **design-taste-frontend**: design read, dials, design-system choice, anti-"AI look" rules, pre-flight check
- **redesign-existing-projects**: scan, audit, then fix inside the existing stack without breaking features

Nothing in the app has been changed yet. Building starts after the owner answers section 10.

---

## 1. Design read and mode

> **Reading this as:** a redesign (overhaul) of a trust-first public-service web app for Tamil Nadu farmers on budget phones, plus staff work screens, with a calm, field-rooted, plain-spoken language, leaning toward native CSS tokens, one type family across three scripts, real Cauvery-delta photography and Phosphor icons.

**Mode: Redesign, overhaul of the visuals.** The owner has rejected the look twice, so everything visual changes. Kept exactly as they are:

- URLs
- element IDs and class names used by JavaScript
- API calls
- i18n keys
- features and states
- the green brand colour (the owner asked to keep it)

### Dials

| Surface | Variance | Motion | Density | Why |
|---|---|---|---|---|
| Current site (measured) | 3 | 2 | 5 | Centred, symmetric, almost no motion |
| Landing page | 6 | 4 | 4 | Trust-first (3/3/5) + overhaul bonus. Still calm. |
| Farmer app pages | 3 | 3 | 4 | Older users, sunlight, one clear action per screen |
| Operator desk, admin | 2 | 2 | 7 | Work tools. The skill says dashboards are out of its scope (section 13), so only tokens, type and states apply here. |
| TV display | 2 | 3 | 3 | Huge type seen from 10 m. The only motion is the "just called" flash. |

---

## 2. How the skills fit this project

The main skill assumes React + Tailwind. The redesign skill's first rule is "work with the existing stack, do not migrate", and the brief (section 5.1) says plain HTML/CSS/JS with no build step. Where the two disagree:

| Skill default | This project | Reason |
|---|---|---|
| React / Next.js, Tailwind v4, Motion library | Plain HTML, one `app.css`, vanilla JS | Spring Boot serves static files; no build step |
| `next/font` self-hosting | Self-hosted `woff2` with `@font-face`, `font-display: swap`, `unicode-range` | Same goal: no Google Fonts request, and each script loads only when needed |
| `@phosphor-icons/react` | Phosphor SVGs combined into one sprite, `/img/icons.svg` | No React; one cached file |
| Image-gen tool, then Picsum | Real Unsplash-licensed photos, converted to WebP and self-hosted | No image tool here; Picsum is random and would show the wrong country |
| Official design system (GOV.UK / USWDS...) | Own tokens, native CSS (skill section 2.B) | India's UX4G is Bootstrap-based and too heavy for the 300 KB page budget; UK/US systems don't fit |
| Dark mode is mandatory | Automatic (`prefers-color-scheme`), TV always dark | Skill 6.C |
| GSAP / scroll-jacking | CSS transitions + `IntersectionObserver` only | Motion ≤ 4 and slow phones |

Rules adopted as-is:

- one accent colour
- one radius rule
- no em/en dashes in visible text; middle dots used sparingly
- no fake device mock-ups
- no hand-drawn decorative SVGs
- no equal-card rows
- hero ≤ 20-word text
- full loading / empty / error states
- press feedback
- reduced motion
- the pre-flight check (section 9)

---

## 3. Audit of the current frontend

**Scanned:**
- 13 pages
- `css/app.css`: 872 lines, 32 KB
- 5 shared scripts
- 3 language files
- about 150 CSS classes used by markup and JavaScript

### 3.1 Keep

- The green brand, dark enough for sunlight (#1e6b3a is 6.6:1 on white)
- 48 px touch targets, the visible focus ring, and `prefers-reduced-motion` handling
- The token card as a real component (it becomes the landing page's visual)
- The language switch, sentence-case copy, and tabular numbers
- The performance work:
  - no `backdrop-filter`
  - SSE pauses in hidden tabs
  - Chart.js and html5-qrcode load lazily
  - data requests start before the language file loads

### 3.2 Retire (with the skill rule that asks for it)

| # | Found | Replace with | Rule |
|---|---|---|---|
| 1 | Four hand-drawn SVG artworks (`hero-field`, `field-band`, `crop-pattern`, `sprig`) on every page | 3–4 real photos on landing and login pages only; plain tinted background elsewhere | 4.8 |
| 2 | Hand-drawn icons: brand mark, 4 step icons, check-mark data-URI | Phosphor icons | 3.C |
| 3 | Text symbols used as icons: `✉ › ← ✓` (5 places) | Phosphor icons | 3.C, 3.D |
| 4 | System font stack, no character | Anek (Latin + Tamil) and Geist Mono for codes | 4.1 |
| 5 | Two accents: green plus gold on CTAs, eyebrow and gradients | Green only. Amber is kept for status meanings (arrived, called to counter). | 4.2 |
| 6 | Gradient buttons with coloured glow shadows | Flat buttons, hover shade, `scale(.98)` press | 4.5, 9.A |
| 7 | Seven different corner radii (3, 6, 10, 12, 18, 34, 999 px) | Three-step rule (section 4.3) | 4.4 |
| 8 | Fake phone frame around the demo token, rotated 2° | The real token card, no device frame, overlapping the hero photo | 4.8, 9.F |
| 9 | Hero: 28-word text plus a 3-item benefit list inside the hero | ≤ 20 words; benefits move to a strip below the hero | 4.7 |
| 10 | Equal-card rows: 4 "how it works" cards, 3 centre cards | Numbered timeline beside a photo; centre list rows | 9.C, 4.7 |
| 11 | Dark "field at dusk" banner on every logged-in page (104 px bottom padding) and a fixed 200 px field band at the bottom | Plain page title; content starts about 180 px higher on phones | Density |
| 12 | Uppercase + letter-spaced table headers, also applied to Tamil/Hindi headers on the landing page | Sentence case, no tracking on Tamil/Hindi | Brief 5.2 |
| 13 | En-dash separators (3 keys × 3 languages, about 40 in pages/JS) and heavy `·` chains (about 80) | Hyphen, "to", line breaks or columns | 9.G, 9.F |
| 14 | "Loading…" text is the only loading state; "–" placeholders | Skeletons shaped like the content | 4.5 |
| 15 | 32 inline `style=""` attributes | Classes | Code quality |
| 16 | Browser `confirm()` dialogs (3 places) | A styled `<dialog>` | Components |
| 17 | No dark mode (except TV), no favicon, no skip link, meta description only on the landing page | Add all four | 6.C, omissions |
| 18 | Server sends CSS/JS/JSON uncompressed with no cache headers | `server.compression` + cache headers for `/fonts`, `/img` | 6.D |

### 3.3 SEO baseline

Only the landing page is public. Every other page is behind a login or is a staff tool, so SEO risk is low. Titles and URLs stay the same, and a meta description is added to public pages.

---

## 4. Design system (proposed, contrast checked)

### 4.1 Colour tokens

All pairs below were measured against WCAG 2.1.

| Token | Light | Dark | Use | Contrast |
|---|---|---|---|---|
| `--bg` | `#f3f5f1` | `#0e1410` | Page | |
| `--surface` | `#fcfdfb` | `#151d17` | Cards, header | |
| `--sunken` | `#e9eee7` | `#1b251e` | Skeletons, table header, stat tile | |
| `--ink` | `#17231b` | `#e6ece7` | Text | 15.9 / 14.4 |
| `--muted` | `#4f5e54` | `#a2b0a6` | Secondary text | 6.7 / 7.6 |
| `--line` | `#d5ddd4` | `#2b372f` | Dividers (decorative) | |
| `--field` | `#76857a` | `#66756a` | Input borders | 3.8 / 3.5 (≥ 3 needed) |
| `--accent` | `#1d6a40` | `#6cc892` | Brand, primary button, links | 6.5 / 8.5 |
| `--on-accent` | `#fcfdfb` | `#0e1410` | Text on primary button | 6.5 / 9.2 |
| `--accent-soft` | `#e1efe5` | `#1c3325` | Selected option, success tint | accent on it 5.6 / 6.7 |
| `--warn-ink` / `--warn-bg` | `#8a5a00` / `#fcefd2` | `#f3c46b` / `#33280f` | Arrived, called, late, pending | 5.2 / 8.9 |
| `--danger-ink` / `--danger-bg` | `#a8231b` / `#fbe3df` | `#f4a59c` / `#3a1a17` | Rejected, errors, cancel | 5.9 / 8.0 |
| `--info-ink` / `--info-bg` | `#1c5a91` / `#e2edf8` | `#9cc4ef` / `#16273a` | Booked, info notes | 6.1 / 8.3 |
| `--focus` | `#b77905` | `#f3c46b` | Focus ring, 3 px | 3.6 / 10.6 |

- One grey family, tinted green; no warm/cool mix.
- No pure black or pure white.
- Status colours are meanings, not accents, and always come with a text label (and an icon where it helps).

### 4.2 Typography

| Role | Font | Weights | Size (phone → desktop) |
|---|---|---|---|
| Everything (Latin, Tamil) | **Anek Latin + Anek Tamil** (Ek Type, OFL) | 400, 600, 700 | body 17 px / 1.6 (Tamil 1.7) |
| Tokens, OTP, amounts in tables, TV tokens | **Geist Mono** (OFL), Latin only | variable | token 40 → 56 px; TV token ≥ 120 px at 1080p |
| Hindi | Device font (Nirmala UI on Windows, Noto Sans Devanagari on every Android) | | |

- **Why Anek:** it is designed by an Indian foundry as one family across Latin, Tamil and Devanagari, so the three languages look like the same product. It has more character than Inter, which the skill discourages.
- **Why mono for codes:** farmers and operators read `TNJ-017` aloud, so 0/O and 1/I must never be confused, and amounts must line up in columns.
- **Why Hindi uses the device font:** Anek Devanagari is 92 KB *per weight* (276 KB for three), which alone would break the 300 KB page budget. It can be added later at two weights (+184 KB, Hindi pages only) if wanted.
- **Font cost per page** (fonts load only for scripts that appear on the page):

  | Language | Font cost |
  |---|---|
  | English | ≈ 82 KB |
  | Tamil | ≈ 134 KB |
  | Hindi | ≈ 82 KB |
- **Scale:**
  - h1 30 → 44 px, h2 24 → 30 px, h3 19 → 21 px, small 15 px
  - slight negative tracking on English headings only; none on Tamil/Hindi, and no uppercase anywhere in farmer pages
  - `text-wrap: balance` on headings, `pretty` on paragraphs, paragraphs ≤ 65ch

### 4.3 Shape, space, depth

- **Radius rule:**
  - 16 px for containers (cards, panels, dialogs, photos)
  - 12 px for controls (buttons, inputs, chips, slot buttons, language switch)
  - 8 px for badges
  - Nothing is pill-shaped.
- **Spacing:** 4 px base: 4 · 8 · 12 · 16 · 24 · 32 · 48 · 64 · 96.
  - Landing sections: 64–96 px (48 on phones).
  - App pages: 24–32 px.
- **Depth:** two green-tinted shadows only.
  - `--shadow-1` for clickable cards
  - `--shadow-2` for the token card, dialogs and the "go to counter" alert
  - Everything else is grouped by spacing and hairlines, not boxes.

### 4.4 Icons

- **Library:** Phosphor Regular (MIT), in one sprite `/img/icons.svg` (about 35 icons, ~15 KB compressed), sized 20/24 px.
- **Usage:** `<svg class="icon"><use href="/img/icons.svg#grains"/></svg>`. A small helper will produce the same markup for JS-built HTML.
- **Coverage:** all 47 candidates were checked and exist, including grains, plant, tractor, scales, drop, currency-inr, qr-code, scan, bell-ringing, envelope-simple, ticket, television and wifi-slash.
- **Farmer pages:** an icon always appears **with** a text label.

### 4.5 Photos

- **Source and format:** Unsplash License (free, no attribution required; credited in README anyway). Converted locally to WebP with Pillow and self-hosted; `<picture>` gives phones a smaller file.
- **Budget:**

  | Where | Desktop | Phone |
  |---|---|---|
  | Landing hero | ≤ 110 KB | ≤ 60 KB |
  | "How it works" photo | ≤ 40 KB | not loaded |
  | Login/registration side photo | ≤ 70 KB | not loaded |
- **Loading:** a background colour always sits behind each photo so text never waits for it.

**Shortlist (owner picks; I will look at each one before using it and drop any that don't fit):**

| For | Photo | Photographer |
|---|---|---|
| Hero | [Woman harvesting rice](https://unsplash.com/photos/woman-harvesting-rice-5NGTf4oD8RA) | Nandhu Kumar |
| Hero | [Man standing in a rice field](https://unsplash.com/photos/photo-of-man-standing-on-rice-field-fg5k00uT0TU) | Nandhu Kumar |
| Hero | [Person walking on a rice field](https://unsplash.com/photos/person-walking-on-rice-field-lXVWulsnNQE) | Foto Murthy |
| Hero | [People planting rice in a flooded field](https://unsplash.com/photos/BJlJG23ciXI) | Prahlad Inala |
| How it works | [Sacks of grain in storage](https://unsplash.com/photos/05XWRj91krc) | Giri |
| How it works | [Handful of grain](https://unsplash.com/photos/EtGcf0zDaLc) | Helena Pfisterer |
| How it works | [Golden rice stalks](https://unsplash.com/photos/wnIeCBJfUYM) | Declan Sun |
| Login side | [Man kneeling in a field](https://unsplash.com/photos/rV-8fmxzOR4) | Gowtham AGM |
| Login side | [Person sitting in a green field](https://unsplash.com/photos/oHxOVLFSOWo) | Sidharth Abhimanyu |

### 4.6 Motion

Every animation has a reason. All of them are off under `prefers-reduced-motion`, and all use only `transform` and `opacity`.

| Animation | Where | Reason |
|---|---|---|
| Hero fades/rises in once (300 ms) | Landing | Hierarchy |
| Sections reveal once on scroll (`IntersectionObserver`) | Landing only | Storytelling of the 4 steps |
| Button press `scale(.98)`, 150 ms hover shade | Everywhere | Feedback |
| Queue position number briefly highlights when it changes | Booking detail | State change |
| "Go to Counter 2" alert slides in + 2 gentle pulses (+ vibration, already there) | Booking detail | Must be unmissable |
| Counter box flashes for a few seconds on a new call | TV | State change seen from 10 m |
| Live dot pulse | Live pages | Real connection state |
| Skeleton shimmer | While data loads | Shows progress |

---

## 5. Screen plan

URLs, IDs and JS behaviour stay. The layout changes are:

### Landing `/`

Five sections, five different layout families, no equal-card rows:

1. **Hero:** asymmetric split.
   - Left: eyebrow (tagline), headline, ≤ 20-word text, **Book a slot** and **Track my booking**.
   - Right: hero photo with the real token card overlapping its lower edge.
   - Phones: a short photo band on top, copy below, CTAs visible without scrolling.
2. **Benefit strip:** the 3 facts moved out of the hero, as icon + text on one tinted band (not cards).
3. **How it works:** a vertical numbered timeline (4 verbs + icons) beside a sticky grain photo; a plain vertical list on phones.
4. **Centres:** list rows (code block, name + address, hours, counters, per-slot capacity), not cards.
5. **Rates:** a clean data table with mono right-aligned numbers and a moisture note with a drop icon.

The footer is a simple row with Staff login and Queue display. There is one eyebrow on the page.

### Farmer login, registration, staff login

- Single column on phones.
- Form + photo split on desktop; the photo is not downloaded on phones.
- OTP input in Geist Mono with large digits.
- The verified-email badge gets a Phosphor check.
- Language choice stays as 3 big options.

### Farmer dashboard

- Plain title row: greeting + **Book a slot**.
- The next active booking is shown larger (token, status, time, "live queue" link); the other bookings follow as compact rows; then "Earlier bookings".
- Notifications: unread dot only for real unread state, envelope icon for "emailed".
- My details as a two-column definition list.
- Skeletons while loading.

### Book a slot

- Compact step indicator (Centre · Day · Time · Crop).
- Centre choice as selectable rows with a "Your district" badge.
- Day chips with a thin busy bar (no background track).
- Slot grid with low / medium / high / full / past / suggested states; the legend has text + a colour swatch.
- Crop options with rate.
- Sticky summary + **Confirm booking** at the bottom on phones.
- Success screen: big mono token, QR, "come 10 minutes early", the two buttons.

### Booking detail + live queue (most important)

- **Live queue card first:** position as a huge number, ETA, people ahead.
- **Called state:** a full-width amber block with the bell icon ("Please go to Counter 2 now") that stays at the top of the screen.
- Reconnecting state with the wifi-slash icon.
- Then the token card, weighment result (accepted / rejected), payment, details, and a timeline with icons per event.
- Cancel uses the styled dialog.

### Operator desk

- No banner, so **Call next** fits at 1366×768.
- Stats as a thin strip.
- Check-in field (mono, autofocus) + scan button.
- Result card with Aadhaar check and position/ETA.
- Counter cards: *free* has a dashed border; *busy* has an amber edge and a "Busy" label.
- Weighment form with a live amount preview.
- Dense tables.
- "Not present" uses the styled dialog.

### TV display

- Always dark; Anek + Geist Mono.
- Three-language labels stacked on separate lines instead of `·` chains.
- Counter boxes with ≥ 120 px tokens; next 5–8 tokens with ETA.
- Checked to fit 1920×1080 and 1280×720 with no scrolling.

### Admin dashboard, payments, settings

- Staff header with an active-page nav.
- Stat tiles without gradient bars.
- Tables restyled with mono numbers.
- Chart.js colours read from the CSS tokens, so the charts follow dark mode.
- Crop bars without background tracks.
- Demo card with a dashed border.
- Forms and switches restyled.
- "Remove demo data" uses the styled dialog.

### 404

Icon, one line, links home. **Emails:** not part of this pass (server templates; email apps ignore web fonts).

---

## 6. Copy changes (in all three languages)

| Key / place | Now | Proposed (English) |
|---|---|---|
| `landing.hero.text` | 28 words | "Book a time to sell your crop, follow your turn live on your phone, and track your payment." (18 words) |
| `landing.centres.hours` | "Open {open} – {close}" | "Open {open} to {close}" |
| `register.aadhaar` | "Aadhaar number – last 4 digits" | "Aadhaar number (last 4 digits)" |
| `event.SKIPPED` | "…when called – back in the queue" | "Not at the counter when called. Back in the queue." |
| Pages / JS | ~40 en-dashes, ~80 `·` chains | "to", hyphens, line breaks or columns; at most one `·` per line |
| TV labels | "Waiting · காத்திருப்பு · प्रतीक्षा" | The three languages stacked on 3 lines |

I will write the Tamil and Hindi versions; the owner should check them. Server-side email and notification texts are not touched in this pass.

---

## 7. Engineering approach

- **`css/app.css` rewritten in layers:**
  1. tokens (light + dark)
  2. `@font-face`
  3. base
  4. layout
  5. components
  6. page sections

  Every class the JavaScript builds (booking items, badges, notification items, counter cards, slots…) keeps its name. It stays one file for one request.
- **New files:**
  - `fonts/` (7 woff2 files)
  - `img/icons.svg`
  - `img/photos/*.webp` (+ `-sm` phone versions)
  - `favicon.svg`
  - `js/ui.js` (icon helper, skeletons, styled confirm dialog, landing reveal)
- **Removed:** the four hand-drawn field SVGs.
- **Backend (config only):** `server.compression.enabled` for HTML/CSS/JS/JSON/SVG, and long cache headers for `/fonts/**` and `/img/**`.
- **Page weight target:** ≤ 300 KB per page transferred (without Chart.js/QR/Razorpay). Example landing page, English: fonts 82 + icons 15 + photos ≤ 150 + compressed HTML/CSS/JS ≈ 25 → ≈ 270 KB.

---

## 8. Build order and testing

| Phase | Work | Commit |
|---|---|---|
| 0 | Assets: fonts, icon sprite, photos → WebP, favicon (needs download approval) | with phase 1 |
| 1 | Foundation: tokens, fonts, base, components, headers, footer, dark mode, compression. All pages must still work before any markup changes. | "Redesign: design system" |
| 2 | P1 farmer: landing, login, dashboard, book, booking detail | "Redesign: farmer pages" |
| 3 | P1 staff: operator desk, TV | "Redesign: operator desk and TV" |
| 4 | P2: registration, staff login, admin dashboard, payments, settings | "Redesign: admin and login pages" |
| 5 | P3: 404, styled dialogs, README, final pre-flight | "Redesign: polish" |

**Every phase is tested on:**
- widths 360, 375, 768, 1366×768 and 1920×1080 (TV also 1280×720)
- English, Tamil and Hindi
- light and dark mode
- reduced motion
- keyboard only on the operator desk

**Real flows exercised with the demo data:**
- login
- booking
- check-in
- call next
- weighment
- the payments page (no checkout)

---

## 9. Pre-flight checklist (adapted from the skill, run before each commit)

- [ ] Zero em/en dashes in visible text (all 3 languages); at most one `·` per line
- [ ] One accent (green) everywhere; amber/red/blue only as status meanings with labels
- [ ] Radius rule 16 / 12 / 8 followed; no pills
- [ ] Every button and form text ≥ 4.5:1 in light **and** dark; input borders ≥ 3:1; visible focus ring
- [ ] No CTA wraps at desktop; no duplicate CTA intent on a page
- [ ] Hero: ≤ 4 text elements, text ≤ 20 words, CTAs visible without scroll at 360 px and 1366 px
- [ ] One eyebrow on the landing page; no split headers; no equal-card rows; ≥ 4 layout families
- [ ] No hand-drawn decorative SVG, no fake device frames, no text-symbol icons
- [ ] Loading (skeleton), empty and error states on every data area
- [ ] Every animation has a reason and stops under reduced motion; only transform/opacity
- [ ] No horizontal scroll at 360 px; Tamil wraps cleanly; no uppercase in Tamil/Hindi
- [ ] Page weight ≤ 300 KB; photos have a colour placeholder; nothing layout-shifts when fonts load
- [ ] All features from brief section 6 still work (IDs and classes used by JS unchanged)

---

## 10. Owner decisions needed before building

1. **Downloads (approval needed):**
   - 7 font files, about 190 KB, from jsDelivr (Fontsource, OFL licence)
   - about 35 Phosphor icon SVGs, about 40 KB (MIT)
   - 3–4 Unsplash photos, originals 2–6 MB each, kept in a temp folder and shrunk to WebP ≤ 110 KB for the project
2. **Photos:** pick from section 4.5, or let me choose after looking at them.
3. **Confirm the direction:**
   - green as the only accent (gold leaves the buttons)
   - automatic dark mode
   - Anek + Geist Mono, with Hindi on the device font
4. **Copy changes** in section 6: OK?
5. **Commit the skills?** Commit `.claude/skills/` + `skills-lock.json` so future sessions use the same skills.
6. Still open from before: keep or drop `spring-boot-starter-actuator` in `pom.xml`.
