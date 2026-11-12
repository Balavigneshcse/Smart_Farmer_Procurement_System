/*
 * TV queue display (/display.html): now serving, the next tokens and the call-out sound.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const $ = id => document.getElementById(id);
const centreId = Number(new URLSearchParams(location.search).get('centre'));
const lastCalledAt = {};       // counter number -> calledAt we last showed
const NEXT_MAX = 30;           // waiting tokens put on the page; only the ones that fit are shown
let firstSnapshot = true;
let waitingNow = [];
let soundOn = false;
let audio = null;

/** Escapes text before putting it into HTML, quotes too, so it is also safe inside attribute values. */
function esc(value) {
    const replacements = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(value ?? '').replace(/[&<>"']/g, char => replacements[char]);
}

/** A label in English, with Tamil and Hindi on a smaller line below. */
function label(en, ta, hi) {
    return `<span class="tv-l"><span class="tv-l-en">${en}</span>`
        + `<span class="tv-l-alt"><span lang="ta">${ta}</span><span class="sep">·</span><span lang="hi">${hi}</span></span></span>`;
}

function counterName(counterNo) {
    return label(`Counter ${counterNo}`, 'கவுண்டர்', 'काउंटर');
}

function tick() {
    const now = new Date();
    $('clock').textContent = now.toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
    $('date').textContent = now.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' });
}

function render(q) {
    $('centre-name').textContent = q.centreName;
    document.title = `${q.centreName} | Queue`;

    // One row per counter; two columns from 5 counters so the rows stay tall enough
    const cols = q.serving.length > 4 ? 2 : 1;
    $('serving').style.setProperty('--cols', cols);
    $('serving').style.setProperty('--rows', Math.max(1, Math.ceil(q.serving.length / cols)));

    const newlyCalled = [];
    $('serving').innerHTML = q.serving.map(s => {
        const changed = s.token && s.calledAt !== lastCalledAt[s.counterNo];
        if (changed && !firstSnapshot) newlyCalled.push(s);
        lastCalledAt[s.counterNo] = s.calledAt;
        return s.token ? `
            <div class="tv-counter busy ${changed && !firstSnapshot ? 'flash' : ''}">
                <div class="tv-counter-name">${counterName(s.counterNo)}</div>
                <div class="tv-token">${esc(s.token)}</div>
            </div>` : `
            <div class="tv-counter free">
                <div class="tv-counter-name">${counterName(s.counterNo)}</div>
                <div class="tv-free">${label('Free', 'காலி', 'खाली')}</div>
            </div>`;
    }).join('');

    waitingNow = q.waiting;
    renderNext();

    $('waiting-count').textContent = q.waiting.length;
    $('avg-wait').innerHTML = q.avgWaitMinutes == null ? '–' : `${Number(q.avgWaitMinutes)}<small>min</small>`;
    $('served').textContent = q.servedToday;

    firstSnapshot = false;
    newlyCalled.forEach(announce);
}

/** The waiting tokens in order. A TV cannot scroll, so the ones that do not fit become "+ N more". */
function renderNext() {
    const list = $('next');
    if (!waitingNow.length) {
        list.innerHTML = `<li class="tv-empty">${label('Nobody waiting', 'யாரும் இல்லை', 'कोई नहीं')}</li>`;
        return;
    }
    list.innerHTML = waitingNow.slice(0, NEXT_MAX).map((w, i) => `
        <li class="${i === 0 ? 'first' : ''}">
            <span class="tv-pos">${esc(w.position)}</span>
            <span class="tv-next-token">${esc(w.token)}</span>
            <span class="tv-next-eta">${w.etaMinutes === 0 ? 'Now' : `about ${esc(w.etaMinutes)} min`}</span>
        </li>`).join('');

    const items = [...list.children];
    const more = document.createElement('li');
    more.className = 'tv-more';
    let shown = items.length;
    const showMore = () => {
        more.textContent = `+ ${waitingNow.length - shown} more`;
        if (!more.isConnected) list.append(more);
    };
    if (shown < waitingNow.length) showMore();
    while (shown > 1 && list.scrollHeight > list.clientHeight) {
        items[--shown].classList.add('hidden');
        showMore();
    }
}

let resizeTimer;
window.addEventListener('resize', () => {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(renderNext, 150);
});

// ---------- Sound: a chime, then the token is read out ----------

function chime() {
    if (!audio) return;
    [660, 880].forEach((freq, i) => {
        const osc = audio.createOscillator();
        const gain = audio.createGain();
        osc.frequency.value = freq;
        gain.gain.setValueAtTime(0.25, audio.currentTime + i * 0.35);
        gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + i * 0.35 + 0.6);
        osc.connect(gain).connect(audio.destination);
        osc.start(audio.currentTime + i * 0.35);
        osc.stop(audio.currentTime + i * 0.35 + 0.6);
    });
}

function announce(serving) {
    if (!soundOn) return;
    chime();
    if ('speechSynthesis' in window) {
        // "TNJ-003" is read letter by letter: "T N J 0 0 3"
        const spoken = serving.token.replace('-', '').split('').join(' ');
        const speech = new SpeechSynthesisUtterance(`Token ${spoken}, please go to counter ${serving.counterNo}`);
        speech.lang = 'en-IN';
        speech.rate = 0.9;
        setTimeout(() => speechSynthesis.speak(speech), 800);
    }
}

$('sound-btn').addEventListener('click', () => {
    // Browsers only allow sound after someone clicks on the page once.
    soundOn = !soundOn;
    if (soundOn && !audio) audio = new AudioContext();
    $('sound-btn').textContent = soundOn ? 'Turn sound off' : 'Turn sound on';
    if (soundOn) chime();
});

// ---------- Live connection ----------

function setLive(online) {
    $('live-status').classList.toggle('offline', !online);
    $('live-text').textContent = online ? 'Live' : 'Reconnecting…';
}

function connect() {
    LiveQueue.connect(centreId, { onSnapshot: render, onStatus: setLive });
}

async function showPicker() {
    $('picker').classList.remove('hidden');
    const centres = await fetch('/api/centres').then(r => r.json());
    $('centre-links').innerHTML = centres.map(c =>
        `<a href="/display.html?centre=${c.id}">${esc(c.name)} <span class="tv-sub">(${esc(c.district)})</span></a>`).join('');
}

// The sound and theme buttons show while the mouse moves and fade out after a few seconds
let controlsTimer;
function showControls() {
    document.body.classList.add('controls-visible');
    clearTimeout(controlsTimer);
    controlsTimer = setTimeout(() => document.body.classList.remove('controls-visible'), 4000);
}
['mousemove', 'pointerdown', 'keydown'].forEach(type => document.addEventListener(type, showControls));

if (centreId) {
    $('board').classList.remove('hidden');
    tick();
    setInterval(tick, 1000);
    showControls();
    connect();
} else {
    showPicker();
}
