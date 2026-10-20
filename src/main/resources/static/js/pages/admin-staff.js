/*
 * Admins & staff (/admin/staff.html): the account list and the add / edit form.
 * In its own file because the Content-Security-Policy header does not allow inline scripts.
 */
const $ = id => document.getElementById(id);
const { esc } = Admin;
const ROLE_LABEL = { ADMIN: 'Centre admin', OPERATOR: 'Staff' };
let accounts = [];
let editing = null;      // the account being edited, or null when adding

const added = iso => new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

function render() {
    const filter = $('filter').value.trim().toLowerCase();
    const shown = accounts.filter(a => !filter || a.name.toLowerCase().includes(filter) || a.username.toLowerCase().includes(filter));
    $('count').textContent = accounts.length;
    $('rows').innerHTML = shown.length ? shown.map(a => `
        <tr>
            <td><strong>${esc(a.name)}</strong></td>
            <td class="mono">${esc(a.username)}</td>
            <td><span class="badge ${a.role === 'ADMIN' ? 'badge-blue' : ''}">${UI.icon(a.role === 'ADMIN' ? 'identification-card' : 'user')}${ROLE_LABEL[a.role]}</span></td>
            <td>${esc(a.centreName)} <span class="small muted mono">${esc(a.centreCode)}</span></td>
            <td>${a.active ? '<span class="badge">Active</span>' : '<span class="badge badge-grey">Switched off</span>'}</td>
            <td>${esc(added(a.createdAt))}</td>
            <td class="num"><button type="button" class="btn btn-outline btn-sm" data-edit="${a.id}">${UI.icon('pencil-simple')}Edit</button></td>
        </tr>`).join('')
        : `<tr class="empty-row"><td colspan="7">${filter ? 'No match.' : 'No accounts yet. Use the Add button above.'}</td></tr>`;
}

function openForm(account) {
    editing = account;
    $('form-title').textContent = account ? `Edit ${account.name}` : $('add-btn-text').textContent;
    $('role-centre-row').classList.toggle('hidden', Boolean(account) || !Admin.isSuper());
    $('f-name').value = account?.name ?? '';
    $('f-username').value = account?.username ?? '';
    $('f-username').disabled = Boolean(account);
    $('f-password').value = '';
    $('f-password-label').textContent = account ? 'New password (leave empty to keep)' : 'Starting password';
    $('f-active-wrap').classList.toggle('hidden', !account);
    $('f-active').checked = account?.active ?? true;
    $('staff-form').classList.remove('hidden');
    $('f-name').focus();
}

function closeForm() {
    editing = null;
    $('staff-form').classList.add('hidden');
}

$('add-btn').addEventListener('click', () => openForm(null));
$('cancel-btn').addEventListener('click', closeForm);
$('filter').addEventListener('input', render);
$('rows').addEventListener('click', event => {
    const button = event.target.closest('[data-edit]');
    if (button) openForm(accounts.find(a => a.id === Number(button.dataset.edit)));
});

$('staff-form').addEventListener('submit', async event => {
    event.preventDefault();
    const name = $('f-name').value.trim();
    const password = $('f-password').value;
    if (!name || (!editing && (!$('f-username').value.trim() || password.length < 8)) || (password && password.length < 8)) {
        Admin.notice('Enter the name, a username and a password of at least 8 characters.', 'error');
        return;
    }
    $('save-btn').disabled = true;
    try {
        if (editing) {
            const saved = await Api.put(`/api/admin/staff/${editing.id}`, { name, active: $('f-active').checked, password: password || null });
            accounts = accounts.map(a => a.id === saved.id ? saved : a);
            Admin.notice(`${saved.name} saved.${password ? ' The new password works from now on.' : ''}`);
        } else {
            const saved = await Api.post('/api/admin/staff', {
                name, username: $('f-username').value.trim(), password,
                role: document.querySelector('input[name="role"]:checked').value,
                centreId: Number($('f-centre').value) || null,
            });
            accounts = [...accounts, saved];
            Admin.notice(`${saved.name} added as ${ROLE_LABEL[saved.role].toLowerCase()} of ${saved.centreName}. Username: ${saved.username}`, 'success', true);
        }
        closeForm();
        render();
    } catch (error) {
        const fields = Object.values(error.fieldErrors || {});
        Admin.notice(fields.length ? fields.join(' ') : Api.errorMessage(error), 'error', true);
    } finally {
        $('save-btn').disabled = false;
    }
});

async function load() {
    try {
        const requests = [Api.get('/api/admin/staff')];
        if (Admin.isSuper()) requests.push(Api.get('/api/admin/centres'));
        const [list, centres] = await Promise.all(requests);
        accounts = list;
        if (centres) {
            $('f-centre').innerHTML = centres.map(c => `<option value="${c.id}">${esc(c.name)} (${esc(c.code)})</option>`).join('');
        }
        render();
    } catch (error) {
        Admin.notice(Api.errorMessage(error), 'error', true);
    }
}

if (Admin.initPage()) {
    if (Admin.isSuper()) {
        $('page-title').textContent = 'Admins & staff';
        $('page-intro').textContent = 'Centre admins and counter staff of every centre. Everyone logs in on the staff login page.';
        $('add-btn-text').textContent = 'Add admin or staff';
    } else {
        document.title = 'Staff | Smart Farmer Procurement System';
        $('centre-col').textContent = 'Centre';
    }
    load();
}
