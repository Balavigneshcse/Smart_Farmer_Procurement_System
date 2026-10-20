/*
 * Centres & crops (/admin/settings.html): crop rates, centre hours and counters, adding centres.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const $ = id => document.getElementById(id);
const { esc, money } = Admin;
let crops = [];
let centres = [];
let editingCropId = null;

// ---------- Crops ----------

function renderCrops() {
    $('crop-rows').innerHTML = crops.map(c => `
        <tr>
            <td><span class="crop-name">${esc(c.nameEn)}</span>
                <span class="crop-names"><span lang="ta">${esc(c.nameTa)}</span>, <span lang="hi">${esc(c.nameHi)}</span></span></td>
            <td class="num"><strong>${esc(money(c.ratePerQtl))}</strong></td>
            <td class="num">${esc(c.maxMoisturePct)}%</td>
            <td>${c.active ? '<span class="badge">Bookable</span>' : '<span class="badge badge-grey">Switched off</span>'}</td>
            <td class="num">${Admin.isSuper()
                ? `<button type="button" class="btn btn-outline btn-sm" data-edit-crop="${c.id}">Edit</button>` : ''}</td>
        </tr>`).join('') || '<tr class="empty-row"><td colspan="5">No crops yet.</td></tr>';
}

function openCropForm(crop) {
    editingCropId = crop?.id ?? null;
    $('crop-form-title').textContent = crop ? `Edit ${crop.nameEn}` : 'Add crop';
    $('c-name-en').value = crop?.nameEn ?? '';
    $('c-name-ta').value = crop?.nameTa ?? '';
    $('c-name-hi').value = crop?.nameHi ?? '';
    $('c-rate').value = crop?.ratePerQtl ?? '';
    $('c-moisture').value = crop?.maxMoisturePct ?? '';
    $('c-active').checked = crop?.active ?? true;
    $('crop-form').classList.remove('hidden');
    $('c-name-en').focus();
}

function closeCropForm() {
    editingCropId = null;
    $('crop-form').classList.add('hidden');
}

$('add-crop-btn').addEventListener('click', () => openCropForm(null));
$('crop-cancel-btn').addEventListener('click', closeCropForm);
$('crop-rows').addEventListener('click', event => {
    const button = event.target.closest('[data-edit-crop]');
    if (button) openCropForm(crops.find(c => c.id === Number(button.dataset.editCrop)));
});

$('crop-form').addEventListener('submit', async event => {
    event.preventDefault();
    const body = {
        nameEn: $('c-name-en').value.trim(),
        nameTa: $('c-name-ta').value.trim(),
        nameHi: $('c-name-hi').value.trim(),
        ratePerQtl: $('c-rate').value,
        maxMoisturePct: $('c-moisture').value,
        active: $('c-active').checked,
    };
    if (!body.nameEn || !body.nameTa || !body.nameHi || !body.ratePerQtl || !body.maxMoisturePct) {
        Admin.notice('Fill in all names, the rate and the moisture limit.', 'error');
        return;
    }
    $('crop-save-btn').disabled = true;
    try {
        const saved = editingCropId
            ? await Api.put(`/api/admin/crops/${editingCropId}`, body)
            : await Api.post('/api/admin/crops', body);
        Admin.notice(`${saved.nameEn} saved: ${money(saved.ratePerQtl)} per quintal, max ${saved.maxMoisturePct}% moisture.`);
        closeCropForm();
        crops = await Api.get('/api/admin/crops');
        renderCrops();
    } catch (error) {
        const fields = Object.values(error.fieldErrors || {});
        Admin.notice(fields.length ? fields.join(' ') : Api.errorMessage(error), 'error', true);
    } finally {
        $('crop-save-btn').disabled = false;
    }
});

// ---------- Centres ----------

/** Opening/closing times on slot boundaries, e.g. every 30 minutes from 5:00 to 22:00. */
function timeOptions(selected, slotMinutes) {
    const options = [];
    for (let m = 5 * 60; m <= 22 * 60; m += slotMinutes) {
        const value = `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
        options.push(`<option value="${value}" ${selected.startsWith(value) ? 'selected' : ''}>${esc(Admin.time(value))}</option>`);
    }
    return options.join('');
}

function centreCard(c) {
    return `
    <form class="card centre-form" data-centre="${c.id}" novalidate>
        <div class="centre-form-head">
            <div>
                <span class="centre-code">${esc(c.code)}</span>
                <h3>${esc(c.name)}</h3>
            </div>
            <label class="switch"><input type="checkbox" name="active" ${c.active ? 'checked' : ''}> Open for bookings</label>
        </div>
        <div class="field"><label>Name</label><input name="name" maxlength="100" value="${esc(c.name)}"></div>
        <div class="form-row">
            <div class="field"><label>District</label><input name="district" maxlength="60" value="${esc(c.district)}"></div>
            <div class="field"><label>Address</label><input name="address" maxlength="255" value="${esc(c.address)}"></div>
        </div>
        <div class="form-row">
            <div class="field"><label>Opens at</label><select name="openTime">${timeOptions(c.openTime, c.slotMinutes)}</select></div>
            <div class="field"><label>Closes at</label><select name="closeTime">${timeOptions(c.closeTime, c.slotMinutes)}</select></div>
        </div>
        <div class="form-row">
            <div class="field"><label>Counters</label><input name="counters" type="number" min="1" max="20" value="${c.counters}"></div>
            <div class="field"><label>Minutes per farmer</label><input name="avgServiceMin" type="number" min="1" max="60" value="${c.avgServiceMin}"></div>
        </div>
        <p class="capacity-note" data-capacity></p>
        <button type="submit" class="btn btn-primary">Save ${esc(c.code)}</button>
    </form>`;
}

// form.elements.namedItem, because form.name would be the form's own name, not the "name" input.
const field = (form, name) => form.elements.namedItem(name);

function updateCapacity(form) {
    const centre = centres.find(c => c.id === Number(form.dataset.centre));
    const counters = Number(field(form, 'counters').value) || 0;
    const service = Number(field(form, 'avgServiceMin').value) || 0;
    const perSlot = service ? Math.floor(counters * centre.slotMinutes / service) : 0;
    form.querySelector('[data-capacity]').innerHTML = perSlot > 0
        ? `<strong>${perSlot} farmers</strong> per ${centre.slotMinutes}-minute slot`
        : '<strong>0 farmers</strong> per slot. Add counters or lower the minutes per farmer.';
}

function renderCentres() {
    $('centres').innerHTML = centres.map(centreCard).join('');
    document.querySelectorAll('.centre-form').forEach(updateCapacity);
}

$('centres').addEventListener('input', event => {
    const form = event.target.closest('.centre-form');
    if (form) updateCapacity(form);
});

$('centres').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.target;
    const id = Number(form.dataset.centre);
    const body = {
        name: field(form, 'name').value.trim(),
        district: field(form, 'district').value.trim(),
        address: field(form, 'address').value.trim(),
        openTime: field(form, 'openTime').value,
        closeTime: field(form, 'closeTime').value,
        counters: Number(field(form, 'counters').value),
        avgServiceMin: Number(field(form, 'avgServiceMin').value),
        active: field(form, 'active').checked,
    };
    const button = form.querySelector('[type="submit"]');
    button.disabled = true;
    try {
        const saved = await Api.put(`/api/admin/centres/${id}`, body);
        centres = centres.map(c => c.id === id ? saved : c);
        Admin.notice(`${saved.name} saved: ${saved.counters} counters, ${Admin.time(saved.openTime)} to ${Admin.time(saved.closeTime)}, `
            + `${saved.slotCapacity} farmers per slot. Slots were updated.`);
    } catch (error) {
        const fields = Object.values(error.fieldErrors || {});
        Admin.notice(fields.length ? fields.join(' ') : Api.errorMessage(error), 'error', true);
    } finally {
        button.disabled = false;
    }
});

// ---------- Add centre (super admin) ----------

function openCentreForm() {
    $('n-open').innerHTML = timeOptions('08:00', 30);
    $('n-close').innerHTML = timeOptions('17:00', 30);
    $('centre-add-form').classList.remove('hidden');
    $('n-code').focus();
}

$('add-centre-btn').addEventListener('click', openCentreForm);
$('centre-add-cancel').addEventListener('click', () => $('centre-add-form').classList.add('hidden'));
$('n-code').addEventListener('input', event => {
    event.target.value = event.target.value.replace(/[^A-Za-z]/g, '').toUpperCase();
});

$('centre-add-form').addEventListener('submit', async event => {
    event.preventDefault();
    const body = {
        code: $('n-code').value.trim(),
        name: $('n-name').value.trim(),
        district: $('n-district').value.trim(),
        address: $('n-address').value.trim(),
        openTime: $('n-open').value,
        closeTime: $('n-close').value,
        counters: Number($('n-counters').value),
        avgServiceMin: Number($('n-service').value),
    };
    if (!body.code || !body.name || !body.district || !body.address) {
        Admin.notice('Fill in the code, name, district and address.', 'error');
        return;
    }
    $('centre-add-save').disabled = true;
    try {
        const saved = await Api.post('/api/admin/centres', body);
        centres = [...centres, saved].sort((a, b) => a.name.localeCompare(b.name));
        renderCentres();
        event.target.reset();
        event.target.classList.add('hidden');
        Admin.notice(`${saved.name} (${saved.code}) added. Farmers can book it now. Next, add its admin under Admins & staff.`);
    } catch (error) {
        const fields = Object.values(error.fieldErrors || {});
        Admin.notice(fields.length ? fields.join(' ') : Api.errorMessage(error), 'error', true);
    } finally {
        $('centre-add-save').disabled = false;
    }
});

async function load() {
    try {
        [crops, centres] = await Promise.all([Api.get('/api/admin/crops'), Api.get('/api/admin/centres')]);
        renderCrops();
        renderCentres();
    } catch (error) {
        Admin.notice(Api.errorMessage(error), 'error', true);
    }
}

if (Admin.initPage()) {
    if (Admin.isSuper()) {
        $('add-crop-btn').classList.remove('hidden');
        $('add-centre-btn').classList.remove('hidden');
    } else {
        // A centre admin changes their own centre; crop rates are set by the super admin for all centres.
        document.title = 'My centre | Smart Farmer Procurement System';
        $('page-title').textContent = 'My centre';
        $('page-intro').textContent = 'Counters and opening hours of your centre. Crop rates are set by the super admin.';
        $('centres-title').textContent = 'Centre settings';
    }
    load();
}
