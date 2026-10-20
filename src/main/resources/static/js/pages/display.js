/*
 * TV queue display (/display.html): now serving, the next tokens and the call-out sound.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const $ = id => document.getElementById(id);
const centreId = Number(new URLSearchParams(location.search).get('centre'));
const lastCalledAt = {};       // counter number -> calledAt we last showed
let firstSnapshot = true;
let soundOn = false;
let audio = null;

/** Escapes text before putting it into HTML, quotes too, so it is also safe inside attribute values. */
function esc(value) {
    const replacements = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(value ?? '').replace(/[&<>"']/g, char => replacements[char]);
}

/** A label in English, Tamil and Hindi, side by side. */
function tri(en, ta, hi) {
    return `<span class="tv-tri"><span>${en}</span><span lang="ta">${ta}</span><span lang="hi">${hi}</span></span>`;
}

function counterName(counterNo) {
    return tri(`Counter ${counterNo}`, 'கவுண்டர்', 'काउंटर');
}

function tick() {
    $('clock').textContent = new Date().toLocaleTimeString('en-IN', { hour: 'numeric', minute: '2-digit' });
}

function render(q) {
    $('centre-name').textContent = q.centreName;
    document.title = `${q.centreName} | Queue`;

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
            <div class="tv-counter">
                <div class="tv-counter-name">${counterName(s.counterNo)}</div>
                <div class="tv-free">${tri('Free', 'காலி', 'खाली')}</div>
            </div>`;
    }).join('');

    $('next').innerHTML = q.waiting.length
        ? q.waiting.slice(0, 12).map(w => `
            <li>
                <span class="tv-next-token">${esc(w.token)}</span>
                <span class="tv-next-eta">${w.etaMinutes === 0 ? 'Now' : `about ${w.etaMinutes} min`}</span>
            </li>`).join('')
        : `<li class="tv-empty">${tri('Nobody waiting', 'யாரும் இல்லை', 'कोई नहीं')}</li>`;

    $('waiting-count').textContent = q.waiting.length;
    $('avg-wait').textContent = q.avgWaitMinutes == null ? 'No data' : `${q.avgWaitMinutes} min`;
    $('served').textContent = q.servedToday;

    firstSnapshot = false;
    newlyCalled.forEach(announce);
}

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

if (centreId) {
    $('board').classList.remove('hidden');
    tick();
    setInterval(tick, 1000);
    connect();
} else {
    showPicker();
}
