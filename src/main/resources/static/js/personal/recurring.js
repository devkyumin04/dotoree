requireLogin();

// 반복 화면 — 규칙 목록 · 추가 · 수정 · 지우기 (욕조 ② — ADR-067)
// 날짜 계산(31일 · 2/29)과 규칙 검사는 전부 서버. 화면은 고르게만 한다
let rules = [];
let categories = [];
let accounts = [];
let editingNum = null;
let ruleType = 'E';    // E 지출 · I 수입 · T 이체 — E·I 는 카테고리 목록을 거르는 화면 전용(서버는 카테고리에서 파생)

const listEl = document.getElementById('ruleList');
const emptyEl = document.getElementById('emptyMessage');
const modal = document.getElementById('ruleModal');
const form = document.getElementById('ruleForm');
const resultMessage = document.getElementById('resultMessage');
const repeatType = document.getElementById('repeatType');

async function loadRules() {
    const [list, cats, accs] = await Promise.all([
        apiRequest('/api/recurring', 'GET'),
        apiRequest('/api/categories', 'GET'),
        apiRequest('/api/accounts', 'GET'),
    ]);
    if (!list || !cats || !accs) return;
    rules = list;
    categories = cats;
    accounts = accs;
    render();
}

function render() {
    listEl.innerHTML = '';
    rules.forEach(r => listEl.appendChild(createItem(r)));
    emptyEl.hidden = rules.length > 0;
}

// "매달 25일" · "매년 3월 15일" · 끝 날짜가 있으면 "~2027-09"
function scheduleLabel(r) {
    const when = r.repeatType === 'Y' ? `매년 ${r.repeatMonth}월 ${r.repeatDay}일` : `매달 ${r.repeatDay}일`;
    return r.endDate ? `${when} · ~${r.endDate.slice(0, 7)}` : when;
}

function targetLabel(r) {
    if (r.kind === 'T') return `↔ ${escapeHtml(r.fromAccountName)} → ${escapeHtml(r.toAccountName)}`;
    return `${escapeHtml(r.categoryEmoji ?? '')} ${escapeHtml(r.categoryName)} <small class="trans-account">${escapeHtml(r.accountName)}</small>`;
}

function createItem(r) {
    const li = document.createElement('li');
    li.className = r.kind === 'T' ? 'transaction-item transfer-item' : 'transaction-item';
    const sign = r.kind === 'I' ? '+' : (r.kind === 'E' ? '-' : '');
    const amountClass = r.kind === 'I' ? 'amount-income' : (r.kind === 'E' ? 'amount-expense' : '');
    li.innerHTML = `
        <span class="trans-category">${scheduleLabel(r)}</span>
        <span class="trans-memo">${targetLabel(r)}${r.ruleMemo ? ' · ' + escapeHtml(r.ruleMemo) : ''}</span>
        <span class="trans-amount ${amountClass}">${sign}${won(r.ruleAmount)}</span>
        <span class="actions">
            <button data-action="edit" data-id="${r.ruleNum}">수정</button>
            <button data-action="delete" data-id="${r.ruleNum}">삭제</button>
        </span>
    `;
    return li;
}

// ───── 모달
document.getElementById('addBtn').addEventListener('click', () => openModal(null));
document.getElementById('cancelBtn').addEventListener('click', closeModal);
repeatType.addEventListener('change', applyRepeatType);

listEl.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-action]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    if (btn.dataset.action === 'edit') openModal(id);
    if (btn.dataset.action === 'delete') removeRule(id);
});

modal.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => setRuleType(tab.dataset.type));
});

function setRuleType(type) {
    ruleType = type;
    modal.querySelectorAll('.tab').forEach(x => x.classList.toggle('active', x.dataset.type === type));
    const isTransfer = type === 'T';
    document.getElementById('transactionFields').hidden = isTransfer;
    document.getElementById('transferFields').hidden = !isTransfer;
    if (!isTransfer) fillCategoryOptions();
}

function applyRepeatType() {
    document.getElementById('repeatMonthField').hidden = repeatType.value !== 'Y';
}

function fillCategoryOptions() {
    const select = document.getElementById('categoryNum');
    select.innerHTML = '';
    const list = categories.filter(c => c.categoryType === ruleType);
    list.filter(c => c.parentCategoryNum === null).forEach(p => {
        select.add(new Option(`${p.categoryEmoji ?? ''} ${p.categoryName}`, p.categoryNum));
        list.filter(c => c.parentCategoryNum === p.categoryNum)
            .forEach(child => select.add(new Option(`　└ ${child.categoryEmoji ?? ''} ${child.categoryName}`, child.categoryNum)));
    });
}

function fillStaticOptions() {
    ['accountNum', 'fromAccountNum', 'toAccountNum'].forEach(id => {
        const select = document.getElementById(id);
        select.innerHTML = '';
        accounts.forEach(a => select.add(new Option(a.accountName, a.accountNum)));
    });
    if (accounts.length > 1) document.getElementById('toAccountNum').value = accounts[1].accountNum;

    const month = document.getElementById('repeatMonth');
    const day = document.getElementById('repeatDay');
    month.innerHTML = '';
    day.innerHTML = '';
    for (let m = 1; m <= 12; m++) month.add(new Option(`${m}월`, m));
    for (let d = 1; d <= 31; d++) day.add(new Option(`${d}일`, d));
}

function openModal(id) {
    form.reset();
    resultMessage.textContent = '';
    editingNum = id;
    fillStaticOptions();
    document.getElementById('startDate').min = todayString();

    const r = id ? rules.find(x => x.ruleNum === id) : null;
    document.getElementById('modalTitle').textContent = r ? '반복 수정' : '반복 추가';
    setRuleType(r ? r.kind : 'E');

    if (r) {
        if (r.kind === 'T') {
            document.getElementById('fromAccountNum').value = r.fromAccountNum;
            document.getElementById('toAccountNum').value = r.toAccountNum;
        } else {
            document.getElementById('categoryNum').value = r.categoryNum;
            document.getElementById('accountNum').value = r.accountNum;
        }
        document.getElementById('ruleAmount').value = r.ruleAmount;
        repeatType.value = r.repeatType;
        if (r.repeatMonth) document.getElementById('repeatMonth').value = r.repeatMonth;
        document.getElementById('repeatDay').value = r.repeatDay;
        // 지난 시작 날짜는 그대로 둔다(서버가 바뀐 경우만 오늘 이후인지 본다)
        document.getElementById('startDate').min = '';
        document.getElementById('startDate').value = r.startDate;
        document.getElementById('endDate').value = r.endDate ?? '';
        document.getElementById('ruleMemo').value = r.ruleMemo ?? '';
    } else {
        const today = new Date();
        document.getElementById('repeatDay').value = today.getDate();
        document.getElementById('repeatMonth').value = today.getMonth() + 1;
        document.getElementById('startDate').value = todayString();
    }
    applyRepeatType();
    modal.hidden = false;
}

function closeModal() {
    modal.hidden = true;
    editingNum = null;
    resultMessage.textContent = '';
}

function todayString() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const isTransfer = ruleType === 'T';
    const isYearly = repeatType.value === 'Y';
    const body = {
        categoryNum: isTransfer ? null : Number(document.getElementById('categoryNum').value),
        accountNum: isTransfer ? null : Number(document.getElementById('accountNum').value),
        fromAccountNum: isTransfer ? Number(document.getElementById('fromAccountNum').value) : null,
        toAccountNum: isTransfer ? Number(document.getElementById('toAccountNum').value) : null,
        ruleAmount: Number(document.getElementById('ruleAmount').value),
        ruleMemo: document.getElementById('ruleMemo').value.trim() || null,
        repeatType: repeatType.value,
        repeatMonth: isYearly ? Number(document.getElementById('repeatMonth').value) : null,
        repeatDay: Number(document.getElementById('repeatDay').value),
        startDate: document.getElementById('startDate').value || null,
        endDate: document.getElementById('endDate').value || null,
    };

    await withButtonLock(document.getElementById('submitBtn'), async () => {
        try {
            if (editingNum) {
                await apiRequest(`/api/recurring/${editingNum}`, 'PUT', body);
            } else {
                await apiRequest('/api/recurring', 'POST', body);
            }
            closeModal();
            await loadRules();
        } catch (error) {
            resultMessage.textContent = error.message;
        }
    });
});

// 지우면 남은 예정이 사라진다. 이미 확인한 거래 · 이체는 그대로
async function removeRule(id) {
    if (!confirm('이 반복을 지울까요? 이미 기록된 거래는 그대로 남아요.')) return;
    try {
        await apiRequest(`/api/recurring/${id}`, 'DELETE');
        await loadRules();
    } catch (error) {
        alert(error.message);
    }
}

loadRules();
