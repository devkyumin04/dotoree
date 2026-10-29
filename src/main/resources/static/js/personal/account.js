requireLogin();

// 계좌 화면 — 순자산 · 계좌 목록 · 추가/수정/닫기 (ADR-065)
// 잔액 · 순자산은 전부 서버 값 그대로(계산 없음, ADR-038)
let accounts = [];
let rules = [];          // 반복 — 닫을 때 "이 계좌를 쓰는 반복도 같이 끝낼까요?" (ADR-067)
let editingNum = null;

const TYPE_LABEL = { C: '현금', B: '통장', M: '마이너스통장', K: '카드' };
// 종류별 안내 — 규칙 판단은 서버, 화면은 미리 알려 주기만
const TYPE_HINT = {
    C: '현금은 시작 잔액이 0 이상이에요.',
    B: '통장은 시작 잔액이 0 이상이에요. 마이너스가 되는 통장이면 "마이너스통장"을 고르세요.',
    M: '마이너스통장은 시작 잔액을 마이너스로 넣을 수 있어요(한도까지).',
    K: '카드는 갚을 돈을 마이너스로 넣어요. 예) 이번 달 쓴 20만원 → -200000. 카드로 쓴 건 그날 지출, 카드값은 통장 → 카드 이체로.',
};

const listEl = document.getElementById('accountList');
const modal = document.getElementById('accountModal');
const form = document.getElementById('accountForm');
const resultMessage = document.getElementById('resultMessage');
const typeSelect = document.getElementById('accountType');

async function loadAccounts() {
    const [list, summary, forecast, ruleList] = await Promise.all([
        apiRequest('/api/accounts', 'GET'),
        apiRequest('/api/accounts/summary', 'GET'),
        apiRequest('/api/recurring/forecast', 'GET'),
        apiRequest('/api/recurring', 'GET'),
    ]);
    if (!list || !summary || !forecast || !ruleList) return;
    accounts = list;
    rules = ruleList;
    render(summary);
    renderForecastWarning(forecast);
}

// 예상 잔액은 모자랄 때만 화면 맨 위에 경고 한 줄(10/28) — 계좌마다 "11/12부터 모자라요 (최저 −2,510,700)".
// 모자라는 계좌가 없거나 반복이 하나도 없으면 아무것도 안 보인다. 계산은 서버, 여기선 표시만
function renderForecastWarning(f) {
    const box = document.getElementById('forecastWarning');
    box.innerHTML = '';
    const short = rules.length === 0 ? [] : f.accounts.filter(a => a.shortage);
    box.hidden = short.length === 0;
    short.forEach(a => {
        const p = document.createElement('p');
        p.textContent = `⚠ ${a.accountName} ${a.shortageDate.slice(5).replace('-', '/')}부터 모자라요 (최저 ${won(a.lowestBalance)})`;
        box.appendChild(p);
    });
}

// 이 계좌를 쓰는 반복 수
function rulesUsing(accountNum) {
    return rules.filter(r => r.accountNum === accountNum || r.fromAccountNum === accountNum || r.toAccountNum === accountNum).length;
}

function render(summary) {
    document.getElementById('totalBalance').textContent = won(summary.totalBalance);

    listEl.innerHTML = '';
    accounts.forEach(a => listEl.appendChild(createItem(a)));
}

function createItem(a) {
    const li = document.createElement('li');
    li.className = 'account-item';
    const balanceClass = a.balance < 0 ? 'amount-expense' : '';
    const limit = a.creditLimit ? `<small>한도 ${won(a.creditLimit)}</small>` : '';
    li.innerHTML = `
        <span class="account-name">${escapeHtml(a.accountName)} <small>${TYPE_LABEL[a.accountType]}</small></span>
        ${limit}
        <span class="account-balance ${balanceClass}">${won(a.balance)}</span>
        <span class="actions">
            <button data-action="adjust" data-id="${a.accountNum}">잔액 맞추기</button>
            <button data-action="edit" data-id="${a.accountNum}">수정</button>
            ${a.isDefault ? '' : `<button data-action="close" data-id="${a.accountNum}">닫기</button>`}
        </span>
    `;
    return li;
}

// ───── 모달
document.getElementById('addBtn').addEventListener('click', () => openModal(null));
document.getElementById('cancelBtn').addEventListener('click', closeModal);
typeSelect.addEventListener('change', applyType);

listEl.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-action]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    if (btn.dataset.action === 'adjust') openAdjustModal(id);
    if (btn.dataset.action === 'edit') openModal(id);
    if (btn.dataset.action === 'close') closeAccount(id);
});

// 한도 칸은 마이너스통장·카드만 보인다
function applyType() {
    const canBeNegative = typeSelect.value === 'M' || typeSelect.value === 'K';
    document.getElementById('creditLimitField').hidden = !canBeNegative;
    if (!canBeNegative) document.getElementById('creditLimit').value = '';
    document.getElementById('typeHint').textContent = TYPE_HINT[typeSelect.value];
}

function openModal(id) {
    form.reset();
    resultMessage.textContent = '';
    editingNum = id;
    document.getElementById('openingDate').max = todayString();

    const a = id ? accounts.find(x => x.accountNum === id) : null;
    document.getElementById('modalTitle').textContent = a ? '계좌 수정' : '계좌 추가';

    if (a) {
        document.getElementById('accountName').value = a.accountName;
        typeSelect.value = a.accountType;
        document.getElementById('openingBalance').value = a.openingBalance;
        // 기본 '현금'은 시작 날짜가 없다(처음부터) — 비워 두면 서버가 그대로 둔다
        document.getElementById('openingDate').value = a.openingDate ?? '';
        document.getElementById('openingDate').required = !!a.openingDate;
        document.getElementById('creditLimit').value = a.creditLimit ?? '';
    } else {
        typeSelect.value = 'B';
        document.getElementById('openingBalance').value = 0;
        document.getElementById('openingDate').value = todayString();
        document.getElementById('openingDate').required = true;
    }
    applyType();
    document.getElementById('accountMore').open = !!a;   // 추가할 땐 접고, 수정할 땐 펼친다
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

    const limit = document.getElementById('creditLimit').value;
    const body = {
        accountName: document.getElementById('accountName').value,
        accountType: typeSelect.value,
        openingBalance: Number(document.getElementById('openingBalance').value),
        openingDate: document.getElementById('openingDate').value || null,
        creditLimit: limit ? Number(limit) : null,
    };

    await withButtonLock(document.getElementById('submitBtn'), async () => {
        try {
            if (editingNum) {
                await apiRequest(`/api/accounts/${editingNum}`, 'PUT', body);
            } else {
                await apiRequest('/api/accounts', 'POST', body);
            }
            closeModal();
            await loadAccounts();
        } catch (error) {
            resultMessage.textContent = error.message;
        }
    });
});

// ───── 잔액 맞추기 (ADR-066) — 차이 계산은 서버. 화면은 실제 잔액만 보낸다
const adjustModal = document.getElementById('adjustModal');
const adjustForm = document.getElementById('adjustForm');
const adjustMessage = document.getElementById('adjustMessage');
let adjustingNum = null;

document.getElementById('adjustCancelBtn').addEventListener('click', closeAdjustModal);

function openAdjustModal(id) {
    const a = accounts.find(x => x.accountNum === id);
    adjustForm.reset();
    adjustMessage.textContent = '';
    adjustingNum = id;
    document.getElementById('adjustHint').textContent = `'${a.accountName}' 앱 잔액 ${won(a.balance)}원`;
    document.getElementById('actualBalance').value = a.balance;
    adjustModal.hidden = false;
}

function closeAdjustModal() {
    adjustModal.hidden = true;
    adjustingNum = null;
}

adjustForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const body = { actualBalance: Number(document.getElementById('actualBalance').value) };
    await withButtonLock(document.getElementById('adjustSubmitBtn'), async () => {
        try {
            await apiRequest(`/api/accounts/${adjustingNum}/adjustments`, 'POST', body);
            closeAdjustModal();
            await loadAccounts();
        } catch (error) {
            adjustMessage.textContent = error.message;
        }
    });
});

// 닫기 — 잔액 0 일 때만(서버가 판단). 기록은 그 계좌에 그대로 남는다
// 잔액이 남아 있으면 "0원으로 맞추고 닫기"를 한 번에 — 잔액 맞추기(실제 잔액 0) 뒤 닫기
async function closeAccount(id) {
    const a = accounts.find(x => x.accountNum === id);
    const usedBy = rulesUsing(id);
    const question = [
        a.balance === 0
            ? `'${a.accountName}' 계좌를 닫을까요?`
            : `'${a.accountName}'에 ${won(a.balance)}원이 남아 있어요. 0원으로 맞추고 닫을까요?`,
        usedBy > 0 ? `이 계좌를 쓰는 반복 ${usedBy}개도 같이 끝나요.` : '',
        '지난 거래는 그대로 남아요.',
    ].filter(Boolean).join(' ');
    if (!confirm(question)) return;
    try {
        if (a.balance !== 0) {
            await apiRequest(`/api/accounts/${id}/adjustments`, 'POST', { actualBalance: 0 });
        }
        await apiRequest(`/api/accounts/${id}${usedBy > 0 ? '?endRecurring=true' : ''}`, 'DELETE');
        await loadAccounts();
    } catch (error) {
        alert(error.message);
        await loadAccounts();
    }
}

loadAccounts();
