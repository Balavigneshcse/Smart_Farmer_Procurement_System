# Smart Farmer Procurement System — Design Brief

**For:** the designer of the new user interface
**Project:** college mini project, theme "Smart Automation"
**Status:** the app works end to end; only the look and feel is being redesigned. Everything described here already exists and must keep working.

---

## 1. The product in one minute

Farmers in Tamil Nadu sell paddy and other crops to government **procurement centres**. Today they queue all day without knowing when they will be served, and afterwards they do not know when the money will arrive.

This web app fixes that:

1. **Book a slot.** The farmer logs in with email + one-time password (OTP), picks a centre, day, time and crop, and gets a **token** (e.g. `TNJ-017`) and a **QR code**.
2. **Live queue.** At the centre an operator checks the farmer in and calls them to a counter. The farmer's phone and a **TV screen** at the centre show the queue live ("You are number 3 · about 20 min").
3. **Weighment.** At the counter the operator records weight, moisture and grade. The crop is accepted (the amount is calculated) or rejected (with a reason).
4. **Payment.** The admin pays the farmer (Razorpay, test mode). The farmer sees every step on a timeline and gets emails.
5. **Admin dashboard.** Today's numbers for all centres, charts for the week, and settings for crop rates, counters and opening hours.

Farmer pages come in **English, Tamil (தமிழ்) and Hindi (हिन्दी)**. Staff pages (operator, admin, TV) are English, and the TV shows labels in all three languages.

---

## 2. What we need from you (deliverables)

### 2.1 Screens, in priority order

| Priority | Screen | Sizes to design |
|---|---|---|
| **P1** | Landing page (home) | Phone 375 px + desktop 1440 px |
| **P1** | Farmer login (email → OTP) | Phone + desktop |
| **P1** | Farmer dashboard (my bookings, notifications, my details) | Phone + desktop |
| **P1** | Book a slot (4-step wizard + success) | Phone + desktop |
| **P1** | Booking detail with live queue | Phone + desktop |
| **P1** | Operator desk | Laptop 1366×768 and desktop 1440 px |
| **P1** | TV queue display | 1920×1080 (must also work at 1280×720) |
| **P2** | Farmer registration | Phone + desktop |
| **P2** | Admin dashboard | Desktop 1440 px + phone |
| **P2** | Admin payments | Desktop + phone |
| **P2** | Admin "Centres & crops" settings | Desktop + phone |
| **P2** | Staff login | Desktop + phone |
| **P3** | 404 page, email templates (OTP, notification) | — |

### 2.2 For every screen

- **All states:** loading, empty, error, success, and the special states listed per screen (for example "reconnecting…" on live pages).
- **At least one screen in Tamil** (the farmer dashboard or booking detail is best). Tamil text is much longer than English and will break a layout designed only in English. Real text is in the Appendix.

### 2.3 Design system (one page or frame)

- **Colours:** hex values with a name and use (primary, accent, text, muted text, borders, background, success, warning, danger, info), plus the status colours in section 8.
- **Typography:** font families, sizes, weights and line heights for H1–H3, body, small text, numbers (token, amounts) and buttons, on phone and desktop.
- **Spacing and radius scale, shadows.**
- **Components with states** (normal, hover, focus, pressed, disabled, error). The component list is in section 7.
- **Icons:** as **SVG** (a consistent set, e.g. Lucide or Material Symbols, or your own).
- **Illustrations or photos:** SVG preferred. Photos as **WebP**, max 150 KB each (phone versions max 80 KB), with the licence or source. Free-to-use sources (Unsplash, Pexels, own photos) only.

### 2.4 Handoff format

Any of these works. They are listed from most to least useful for implementation:

1. **Figma** link (view + Dev Mode access, or a copy of the file), with styles/variables for colours and text.
2. **Static HTML + CSS mockups**, one file per screen. Output from an AI design tool is fine (v0, Figma Make, Stitch, Claude…); Tailwind output is also fine, and I will convert it to plain CSS.
3. **PNG exports** at 1× and 2× for every screen and state, plus the design-system page with exact values.

Send everything as one ZIP or a shared folder link, with screens named after the table above (e.g. `P1-booking-detail-phone-waiting.png`).

---

## 3. Who uses it

| User | Device and place | What they need |
|---|---|---|
| **Farmer** (main user) | Budget Android phone (360–412 px wide), often outdoors in bright sunlight, slow 3G/4G. Often older, many read only Tamil, limited experience with apps. | Big clear text and buttons, few steps, icons **with** words, the token and "go to counter 2" impossible to miss, everything in their language. |
| **Operator** (one per centre) | Laptop or desktop at the check-in counter, sometimes a USB QR scanner. Works fast all day. | Check a farmer in by typing or scanning a token, call the next farmer per counter, record weighment. Dense but readable, keyboard-friendly. |
| **Admin** (district office) | Desktop, sometimes a phone. | Today's numbers at a glance, pay farmers, change rates and centre settings. |
| **Farmers at the centre** (TV) | TV on a wall, seen from 5–10 metres. | Which token is at which counter, who is next, roughly how long. |

---

## 4. Brand and style direction

- **Name:** Smart Farmer Procurement System (Tamil: ஸ்மார்ட் விவசாயி கொள்முதல் அமைப்பு, Hindi: स्मार्ट किसान खरीद प्रणाली). A short logo mark is welcome, e.g. a paddy sprig in a rounded square.
- **Tagline:** "Book your slot. Skip the queue. Track your payment."
- **Feeling:** trustworthy (government service), calm, friendly, rooted in farming. Not flashy, not a startup look.
- **Colour:** **green** has been the brand colour so far (agriculture) with gold/amber as the accent (ripe paddy). A better palette is welcome. If you move away from green, show why.
- **Imagery:** paddy fields of the Cauvery delta, farmers, grain. Avoid generic stock clichés. Illustration or real photos are both fine, within the performance limits (5.5).
- **Current design:** screenshots are attached for reference. The owner is **not happy with it**, so feel free to change the direction.

---

## 5. Requirements (must be followed)

### 5.1 Technology

- Built with **plain HTML, CSS and vanilla JavaScript**, served by a Java (Spring Boot) server. There is no React, Vue or CSS framework at run time, and no build step. Anything you design must be possible in normal CSS.
- External scripts only from public CDNs (cdnjs, jsDelivr). Libraries already in use: **Chart.js** (admin charts), **qrcodejs** (token QR), **html5-qrcode** (camera scanning), **Razorpay Checkout** (payment pop-up; its look **cannot** be changed).
- Fonts: web fonts allowed (Google Fonts), at most **2 families and 4 weights** in total. They must cover **Latin, Tamil and Devanagari**. Good options are *Noto Sans* + *Noto Sans Tamil* + *Noto Sans Devanagari*, or *Mukta* / *Hind* families. The current design uses system fonts.

### 5.2 Three languages

- Every farmer-facing label exists in English, Tamil and Hindi, and the farmer switches with a language button (English | தமிழ் | हिन्दी) in the header.
- **Tamil text is often 1.5–2× longer than English** and its letters are taller. Buttons and cards must allow text to **wrap to two lines**, with no fixed widths for text and nothing truncated.
- **No text inside images.**
- **No ALL CAPS** styling for Tamil or Hindi. Letter-spacing effects break these scripts.
- Line height at least 1.5 for body text.

### 5.3 Responsive layout

- Farmer pages are **mobile-first**: design for 360–414 px, then scale up. There must be **no horizontal scrolling at 360 px**.
- Operator desk: works on a 1366×768 laptop without scrolling to reach "Call next".
- Admin pages: desktop first, but usable on a phone (tables may scroll sideways inside their card).
- TV: 16:9, **no scrolling ever**. Everything fits at 1920×1080 and 1280×720.

### 5.4 Accessibility (WCAG 2.1 AA)

- Text contrast at least **4.5:1** (3:1 for large text). The farmer pages must stay readable in sunlight, so prefer strong contrast.
- Touch targets at least **48×48 px** on farmer pages.
- A visible **focus style** on every interactive element (keyboard users, the operator).
- **Never colour alone:** every status has a text label, and an icon where it helps.
- Body text at least **16 px** on phones (17 px preferred for farmers).
- Motion: gentle only, and it must respect "reduce motion".

### 5.5 Performance (slow phones and networks)

- Page weight: at most **300 KB per page** without the external libraries, and all images on one page together at most 150 KB.
- The page must be readable before images load (background colour behind any image).
- **Avoid:** big blur or glass effects (`backdrop-filter`), animated shadows, parallax, video backgrounds, heavy full-screen photos on phones. These made the current version lag.
- Animations only with `transform` and `opacity`.

### 5.6 Keep all content and features

Every field, button and state listed in section 6 must still be in the new design. You may regroup, rename (in English) and restyle everything.

---

## 6. Screens and their content

Real example values are in brackets. The URL is where the page lives.

### 6.1 Landing page — `/`
- **Header:** logo + name, language switch (English | தமிழ் | हिन्दी).
- **Hero:** headline ("No more waiting all day at the procurement centre"), short text, buttons **Book a slot** (main) and **Track my booking**, 3 short benefits ("Your own time slot", "Live token on your phone", "Payment tracked till credited"). There is also an example of the farmer's phone screen showing a token (`TNJ-017`, position 3, wait 20 min, "Your turn is near").
- **How it works:** 4 steps (register with email → book a slot → come at your time → get paid), each with an icon, title and one line.
- **Procurement centres:** a card per centre with code (`TNJ`), name (Thanjavur Procurement Centre), address, opening hours (8:00 am – 5:00 pm), counters (3) and farmers per 30-minute slot (9).
- **Procurement rates:** a table of crop, rate per quintal (₹2,369) and maximum moisture (17%), with a note that crops above the moisture limit may be rejected.
- **Footer:** © line, links to Staff login and Queue display screen.
- **States:** loading, could not load (no internet).

### 6.2 Farmer login — `/farmer/login.html`
- **Step 1:** title "Farmer login", one line of explanation, **email** field, **Send OTP** button (busy state "Sending…").
- **Step 2:** "Enter OTP", "We sent a 6-digit OTP to ravi@gmail.com", "Not in your inbox? Check Spam", a 6-digit **OTP** field (large digits), **Verify and continue**, a **Resend OTP** link with countdown ("Resend OTP in 42 s"), and **Change email**.
- **Messages:** wrong OTP ("Wrong OTP. 2 attempts left"), expired, too many requests, invalid email, session expired ("Please log in again").
- Link to Staff login.

### 6.3 Farmer registration — `/farmer/register.html` (only for new farmers, right after OTP)
- "Create your account", "Just a few details. You only do this once."
- A **verified email** badge ("✓ Email verified: ravi@gmail.com").
- **Language choice** as 3 big options (English / தமிழ் / हिन्दी). Picking one switches the page immediately.
- Fields: full name, **mobile** (+91 prefix, 10 digits), address (2–3 lines), district (with suggestions), **Aadhaar last 4 digits** plus a hint ("Only the last 4 digits, to check your identity at the counter").
- **Create account** button; errors shown under each field.

### 6.4 Farmer dashboard — `/farmer/dashboard.html`
- Greeting ("வணக்கம், Ravi Kumar"), one line, big **Book a slot** button.
- **My bookings:** current bookings first, then "Earlier bookings". Each item shows:
  - token (`TNJ-017`)
  - status badge (section 8)
  - payment badge if weighed ("Paid · ₹58,040.50")
  - day and time ("Today, 24 Sep · 10:30 AM – 11:00 AM")
  - centre, crop and quantity (Thanjavur Procurement Centre · Paddy (Common) · 25 quintal)
  - Tapping an item opens the booking detail.
- **Empty state:** "You have no bookings yet" + Book a slot.
- **Notifications:** title with an unread count ("3 new"), **Mark all as read**. Each item shows a subject, the message (up to ~3 lines in Tamil), time and "✉ emailed" when an email was also sent; unread items are highlighted. The first 3 are shown, with **Show all** for the rest. Empty state.
- **My details:** email, mobile (+91 …), address, district, Aadhaar (XXXX XXXX 4821), language.
- **Header (logged in):** logo, language switch, **Log out**.

### 6.5 Book a slot — `/farmer/book.html` (4-step wizard on one page)
1. **Choose a centre:** a card per centre (name, address, opening hours). The farmer's own district is marked ("Your district").
2. **Choose a day:** today + next 6 days as chips ("Today", "Tomorrow", "Friday 26"), each with a small bar showing how busy it is.
3. **Choose a time:** 30-minute slots in a grid (8:00 – 8:30 AM …).
   - Each slot shows how many places are left, and is coloured by how crowded it is (low / medium / high).
   - Some slots are **Full** or **Past** (disabled). The least crowded slot is marked **Suggested**.
   - There is a legend for the colours.
4. **Crop and quantity:** crop choice (4 crops, names in the farmer's language, with rate) and quantity in quintals (0.5–500). It shows the **approximate amount** ("Approximate amount: ₹59,225") and a summary of the choices, then **Confirm booking** (busy state "Booking…").
- **Success screen:** "Your slot is booked!", a **big token**, the **QR code**, day/time/centre, "Please come 10 minutes before your time", then **View booking** and **Go to my bookings**.
- **Errors:** slot just became full, already booked that day, slot closed.

### 6.6 Booking detail + live queue — `/farmer/booking.html?id=…` (the most important farmer screen)
- **Live queue card** (only on the day of the booking, at the top, full width). Its states:
  - *Not arrived yet:* "When you reach the centre, show your token at the check-in desk" + "Farmers waiting now: 5".
  - *Waiting:* **Your position: 3**, **Estimated wait: 20 min**, "2 farmers ahead of you".
  - *Next:* "You are next! Please stay near the counters."
  - *Called:* a **very prominent** "Please go to Counter 2 now" (the phone also vibrates). This must be unmissable.
  - *Reconnecting…* (connection lost), and "Updated 10:42 am".
- **Token card:** status badge, "Token", **big token**, **QR code** (only while the booking is active), "Show this at the counter".
- **Cancel booking** button (only before the slot starts), which needs a confirmation.
- **Weighment result** (after the counter):
  - Accepted: weight (24.5 quintal), moisture (16.5%), grade (A), rate, **amount** (₹58,040.50).
  - Rejected: red message "Your crop was not accepted" + reason ("Moisture 19% is above the 17% limit") + the measurements.
- **Payment:** "Payment pending — the amount will be credited soon…" or "Paid on 24 Sep, 3:40 PM · Reference pay_Q8x…".
- **Details:** day, time, centre (+ address), crop, quantity, approximate amount.
- **Status history (timeline), newest first:** Booked → Arrived → Called to counter 2 → Procured → Payment pending → Paid, each with date and time. Other entries: Cancelled, Missed (no-show), "Not at the counter when called – back in the queue", Rejected.

### 6.7 Staff login — `/staff/login.html`
- "Staff login — for procurement centre operators and administrators", username, password (with **Show**), **Log in**, and a link "Farmer? Log in with your email".

### 6.8 Operator desk — `/operator/desk.html`
- **Header:** "Operator desk", centre name (Thanjavur Procurement Centre), live status (Live / Reconnecting…), **Open TV display**, user name, Log out.
- **Stats:** Expected today (4), Waiting (5), At counters (2), Served today (7), Average wait (17 min).
- **Check in a farmer:**
  - Token field (autofocus; typing `TNJ-017` or scanning the QR, then Enter), **Check in** button, **Scan QR with camera** (opens a camera view).
  - The result card shows "✓ TNJ-017 checked in", the farmer name, **Aadhaar ••••4821** (identity check), mobile, crop, quantity, slot, **position in the line and ETA** ("Position 4 · ETA 9 min"), and a **Late** badge if applicable. There are error messages (wrong centre, not today, already checked in, unknown token).
- **Counters (1–3):** one card per counter.
  - *Free:* **Call next** button.
  - *Busy:* token, farmer, crop, quantity and "called 3 min ago", with buttons **Record weighment**, **Call again**, **Not present**.
- **Weighment form** (for a busy counter):
  - Fields: weight (quintals), moisture (%), grade (A / B / C), decision **Accept / Reject**, remarks (required for reject).
  - It shows the crop's rate and moisture limit, a live **amount preview** (weight × rate), and blocks Accept with a warning when moisture is above the limit.
  - **Save** / **Cancel**.
- **Waiting line (table):** position, token, farmer, crop, slot, arrived at, ETA, Late badge.
- **Expected today (table):** token, farmer, slot, crop, with a search box.
- **Messages:** "Called TNJ-017 to counter 2", "Counter 2 is still serving a farmer", "No farmers are waiting", etc.

### 6.9 TV queue display — `/display.html?centre=1` (full screen, no header or footer of the site)
- Centre name (large), clock, stats: **Waiting · காத்திருப்பு · प्रतीक्षा** (5), **Avg wait** (17 min), **Served** (7).
- **Now serving · இப்போது · अभी:** one big box per counter ("Counter 1 · கவுண்டர் · काउंटर") with the **token in very large type** (readable at 10 m; at least ~120 px tall at 1080p), or **Free · காலி · खाली**. A newly called counter **flashes** for a few seconds (and a chime / spoken announcement can be switched on).
- **Next · அடுத்து · अगला:** the next 5–8 tokens with ETA ("~9 min", "Now").
- **Footer:** "Show your token at the counter" in 3 languages, live status, **Turn sound on** button.
- **Without a centre:** a simple centre picker.
- **States:** empty queue, connecting / reconnecting.

### 6.10 Admin dashboard — `/admin/dashboard.html`
- **Admin header:** logo, nav (**Dashboard · Payments · Centres & crops**), user name, Log out.
- Title "Today at the centres", date, "Updated 10:23 am" (refreshes every 30 s).
- **6 tiles:**
  - Farmers booked (37 · 28 arrived · 0 cancelled)
  - Waiting now (10 · 4 at counters)
  - Served (14 · 2 rejected · 0 no-show)
  - Average wait (17 min)
  - Procured today (338.1 qtl, worth ₹8,06,238)
  - Payments pending (10 · ₹6,43,134)
- **Centres right now (table):** centre (+ code, counters, link to its TV screen), still to come, waiting, at counters (2 / 3), served, no-show, average wait, procured (qtl), slots filled today (18 of 162 places + bar).
- **Charts (last 7 days):** farmers per day (stacked bars: served / still to serve / no-show) and crop procured per day (quintals).
- **Farmer payments:** waiting for payment, paid today, paid in total, and an **Open payments** button.
- **Crops (last 7 days):** a horizontal bar per crop with quintals and number of farmers.
- **Demo data card:** explanation, status ("Demo data is loaded: 40 demo farmers, 37 bookings today"), **Load demo day** and **Remove demo data** (danger).

### 6.11 Admin payments — `/admin/payments.html`
- **Tiles:** waiting for payment (count + amount), paid today, paid in total.
- **Filter:** Pending / Paid / All. A hint shows the test UPI ID `success@razorpay`.
- **Table:** date, token, farmer (+ mobile), crop (+ weight · grade · centre), amount, status (Pending, or Paid + time + reference), and a **Pay** button.
- Pay opens the Razorpay pop-up (not designable). Then a success or failure message.
- A warning banner appears when the payment keys are not set.

### 6.12 Admin "Centres & crops" — `/admin/settings.html`
- **Crops and rates:**
  - A table of crop (English + Tamil · Hindi names), rate per quintal, maximum moisture and status (Bookable / Switched off), with an **Edit** button per row.
  - **Add crop**: a form with names in English, Tamil and Hindi, rate (₹), moisture limit (%) and a "farmers can book it" switch, then Save / Cancel, plus a note about rates.
- **Procurement centres:** one form card per centre. Each has:
  - code + name, and an "open for bookings" switch
  - name, district, address
  - opens at / closes at (half-hour steps)
  - counters, minutes per farmer, and a live note ("9 farmers per 30-minute slot")
  - a **Save** button, then a confirmation.
- **Errors:** time not on a half-hour, counter busy, zero capacity.

### 6.13 Other
- **404 page:** "Page not found" with links home.
- **Emails (optional, P3):** the OTP email (big 6-digit code) and a notification email (greeting, message, "Open my booking" button, footer). These must be simple table-based HTML for email apps; a mock-up is enough.

---

## 7. Components needed (design system)

- **Headers:**
  - public/farmer header (logo, language switch, log out)
  - staff header (logo, nav, user, log out)
  - page title area (optional banner)
- **Language switch:** 3 options, with the current one clearly selected.
- **Buttons:** primary, secondary/outline, accent (main call to action), danger, text link; sizes large (farmer) / normal / small (tables); busy state with text change.
- **Form controls:**
  - text input, input with prefix (+91), OTP input (6 digits), textarea, select, number
  - switch (on/off), big radio cards (language, grade A/B/C, accept/reject)
  - errors under fields, hints.
- **Cards:** normal, clickable (booking item), highlighted (live queue), dashed (demo).
- **Badges:** booking statuses (7), payment statuses (3), "Late", "Suggested", "Your district", unread count.
- **Alerts / messages:** success, info, warning, error (inline and as a notice at the top).
- **Stat tiles** (label, big number, small sub-text).
- **Tables:** header, rows, numbers right-aligned, empty row, horizontal scroll on phones.
- **Wizard pieces:** step number + title, centre card, date chip with busy bar, slot button (low / medium / high / full / past / suggested / selected), crop option with rate, summary box, estimate.
- **Token display:** big token text + QR code block.
- **Live queue card:** all states from 6.6, including the "go to counter" alert.
- **Timeline** (vertical, newest first).
- **Notification item** (unread / read, emailed icon).
- **Counter card** (free / busy with actions) and the weighment form.
- **Progress / fill bar**, crop bar list, chart style (colours, grid, legend).
- **Confirm dialog** (currently the browser's own dialog; a custom design is welcome).
- **Loading** (skeleton or spinner) and **empty states** (with a small illustration if you like).
- **Footer.**

---

## 8. Status reference (for badges and colours)

| Booking status | Label (EN · TA · HI) | Meaning | Suggested tone |
|---|---|---|---|
| BOOKED | Booked · முன்பதிவானது · बुक | Slot booked, not arrived | Blue / neutral |
| CHECKED_IN | Arrived · வருகை பதிவானது · पहुँच गए | Checked in, waiting | Amber |
| CALLED | At the counter · கவுண்டரில் · काउंटर पर | Called to a counter now | Amber / strong |
| COMPLETED | Procured · கொள்முதல் முடிந்தது · खरीद पूरी | Crop accepted | Green |
| REJECTED | Rejected · நிராகரிக்கப்பட்டது · अस्वीकृत | Crop not accepted | Red |
| CANCELLED | Cancelled · ரத்து செய்யப்பட்டது · रद्द | Cancelled by farmer | Grey |
| NO_SHOW | Missed · வரவில்லை · नहीं आए | Never came | Grey |

| Payment | Label (EN · TA · HI) | Tone |
|---|---|---|
| PENDING | Payment pending · பணம் நிலுவையில் · भुगतान बाकी | Amber |
| PAID | Paid · பணம் செலுத்தப்பட்டது · भुगतान हो गया | Green |
| FAILED | Payment failed | Red |

| Slot crowd level | Meaning |
|---|---|
| Low | Less than half full |
| Medium | Half to 80% full |
| High | Over 80% full |
| Full / Past | Cannot be booked (disabled) |

---

## 9. Sample data for mock-ups

**Centres**
| Code | Name | Address | Hours | Counters | Farmers per 30-min slot |
|---|---|---|---|---|---|
| TNJ | Thanjavur Procurement Centre | Near New Bus Stand, Thanjavur 613005 | 8:00 am – 5:00 pm | 3 | 9 |
| TVR | Tiruvarur Procurement Centre | Near Collectorate, Tiruvarur 610004 | 8:00 am – 5:00 pm | 2 | 6 |
| NGT | Nagapattinam Procurement Centre | Near Old Bus Stand, Nagapattinam 611001 | 8:00 am – 4:00 pm | 2 | 5 |

**Crops**
| English | Tamil | Hindi | Rate per quintal | Max moisture |
|---|---|---|---|---|
| Paddy (Common) | நெல் (பொது ரகம்) | धान (सामान्य) | ₹2,369 | 17% |
| Paddy (Grade A) | நெல் (சன்ன ரகம்) | धान (ग्रेड ए) | ₹2,389 | 17% |
| Maize | மக்காச்சோளம் | मक्का | ₹2,400 | 14% |
| Ragi | கேழ்வரகு | रागी | ₹4,886 | 12% |

**Other examples:**
- Farmers: Murugan K, Lakshmi R, Ravi Kumar, Anita Devi
- Tokens: `TNJ-008`, `TNJ-017`, `TVR-003`
- QR code content: `SFPS:12:TNJ-017`
- Amounts use Indian grouping: **₹58,040.50**, **₹8,06,238**
- Times: "10:30 AM – 11:00 AM" (30-minute slots)
- Payment reference: `pay_Q8xY2mN4kL`

---

## 10. Appendix — real text in three languages

Use these to test that layouts survive Tamil and Hindi.

| Where | English | Tamil | Hindi |
|---|---|---|---|
| Hero title | No more waiting all day at the procurement centre | கொள்முதல் நிலையத்தில் இனி நாள் முழுவதும் காத்திருக்க வேண்டாம் | अब खरीद केंद्र पर दिन भर इंतज़ार नहीं |
| Main button | Book a slot | நேரம் முன்பதிவு செய்ய | स्लॉट बुक करें |
| Second button | Track my booking | என் முன்பதிவைக் கண்காணிக்க | मेरी बुकिंग देखें |
| Login title | Farmer login | விவசாயி உள்நுழைவு | किसान लॉग इन |
| Button | Verify and continue | சரிபார்த்துத் தொடரவும் | सत्यापित करें और आगे बढ़ें |
| Registration | Create your account | உங்கள் கணக்கை உருவாக்குங்கள் | अपना खाता बनाएं |
| Section | My bookings | என் முன்பதிவுகள் | मेरी बुकिंग |
| Section | Notifications | அறிவிப்புகள் | सूचनाएँ |
| Wizard step 1 | Choose a centre | நிலையத்தைத் தேர்ந்தெடுங்கள் | केंद्र चुनें |
| Wizard step 4 | Crop and quantity | பயிர் மற்றும் அளவு | फसल और मात्रा |
| Slot tag | Suggested / Full | பரிந்துரைக்கப்பட்டது / நிரம்பியது | सुझाया गया / भरा हुआ |
| Button | Confirm booking | முன்பதிவை உறுதிசெய் | बुकिंग पक्की करें |
| Success | Your slot is booked! | உங்கள் நேரம் முன்பதிவு செய்யப்பட்டது! | आपका स्लॉट बुक हो गया! |
| Success text | Show this token or QR code at the centre. Please come 10 minutes before your time. | இந்த டோக்கன் அல்லது QR குறியீட்டை நிலையத்தில் காட்டுங்கள். உங்கள் நேரத்திற்கு 10 நிமிடம் முன்பே வாருங்கள். | यह टोकन या QR कोड केंद्र पर दिखाएँ। अपने समय से 10 मिनट पहले आएँ। |
| Live | Your position / Estimated wait | உங்கள் வரிசை எண் / தோராயக் காத்திருப்பு | आपका स्थान / अनुमानित इंतज़ार |
| Live | 2 farmers ahead of you | உங்களுக்கு முன் 2 விவசாயிகள் | आपसे पहले 2 किसान |
| Live | You are next! Please stay near the counters. | அடுத்தது நீங்கள்! கவுண்டர் அருகில் இருங்கள். | अगली बारी आपकी है! कृपया काउंटर के पास रहें। |
| Live (called) | Please go to Counter 2 now | இப்போதே கவுண்டர் 2-க்குச் செல்லுங்கள் | कृपया अभी काउंटर 2 पर जाएँ |
| Live | When you reach the centre, show your token at the check-in desk. | நிலையத்திற்கு வந்ததும், பதிவு மேசையில் உங்கள் டோக்கனைக் காட்டுங்கள். | केंद्र पहुँचने पर चेक-इन डेस्क पर अपना टोकन दिखाएँ। |
| Token card | Show this at the counter | இதை கவுண்டரில் காட்டுங்கள் | इसे काउंटर पर दिखाएँ |
| Button | Cancel booking | முன்பதிவை ரத்து செய் | बुकिंग रद्द करें |
| Result | Your crop was not accepted. | உங்கள் விளைபொருள் ஏற்றுக்கொள்ளப்படவில்லை. | आपकी फसल स्वीकार नहीं की गई। |
| Status | Procured | கொள்முதல் முடிந்தது | खरीद पूरी |
| Payment | Payment pending | பணம் நிலுவையில் | भुगतान बाकी |

A notification message in Tamil (the longest kind of text on the dashboard):
> Thanjavur Procurement Centre-இல் வியாழன், 24 செப்டம்பர், முற்பகல் 10:30 - முற்பகல் 11:00 நேரத்திற்கு உங்கள் முன்பதிவு உறுதி செய்யப்பட்டது. பயிர்: நெல் (பொது ரகம்). உங்கள் டோக்கன் TNJ-019.

---

## 11. Decisions for the project owner (before design starts)

1. **Colour:** keep green as the brand colour, or open to a new palette?
2. **Imagery:** real photos of paddy fields and farmers, or illustrations?
3. **Logo:** does the project need a new logo mark?
4. **Dark mode:** not required (only the TV is dark). Add it?

## 12. After the design arrives

1. I compare every screen with this brief and ask about anything missing.
2. I build the design into the existing pages (same features, same URLs), screen by screen in the priority order above, and check each one at 360 px, 375 px, 1366 px and 1920 px, in all three languages.
3. The project owner reviews each screen in the browser before it is committed.
