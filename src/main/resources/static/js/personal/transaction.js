requireLogin();

// 서버 응답을 그대로 들고 있는다. version 이 여기 담겨 있고,
// 수정·삭제할 때 번호로 찾아 꺼내 쓴다 (DOM 에는 표시만 맡김)
let transactions = [];
let transfers = [];      // 이체 — 수입·지출이 아니라 계좌 사이에서 자리만 옮기는 돈 (ADR-065)
let adjustments = [];    // 잔액 보정 — 실제 잔액에 맞춘 차이. 통계엔 안 들어간다 (ADR-066)
let pending = [];        // 반복 예정 — 아직 기록 안 된 돈. [확인]해야 거래 · 이체가 된다 (ADR-067)
let categories = [];
let accounts = [];       // 열린 계좌 — 기본 '현금'이 맨 앞 (서버 정렬)

// 화면 위 수입/지출/이번 달 증감. 목록을 직접 더하지 않고 통계 API 값을 그대로 표시한다.
// 돈 합계는 계산하는 곳이 한 군데여야 통계 화면과 어긋나지 않는다 (ADR-038, 040). 이체는 통계에 안 들어간다
let summary = null;

// 모달의 지출/수입/이체 토글. E·I 는 카테고리 목록을 거르는 용도, T 는 이체 입력칸으로 바꾼다
let modalType = 'E';

// 지금 수정 중인 항목. 추가 모드면 null — { kind: 'trans' | 'transfer', num }
// DOM(hidden input) 대신 JS 가 들고 있는다 — 상태는 JS 소유, DOM 은 표시만
let editing = null;

const now = new Date();
let year = now.getFullYear();
let month = now.getMonth() + 1;

const listEl = document.getElementById('transactionList');
const emptyEl = document.getElementById('emptyMessage');
const monthLabel = document.getElementById('monthLabel');
const modal = document.getElementById('transactionModal');
const form = document.getElementById('transactionForm');
const resultMessage = document.getElementById('resultMessage');
const countList = document.getElementById('countList');

// ───── 조회
// 카테고리·계좌는 다른 탭에서 바뀔 수 있어 목록을 새로 받을 때 같이 받는다(지워진 것으로 저장하면 404).
// 여섯 요청은 서로의 결과가 필요 없어 동시에 보낸다 — 요청 수는 늘어도 기다리는 시간은 한 번 (ADR-040)
async function loadTransactions() {
    const [cats, accs, list, moves, fixes, stats] = await Promise.all([
        apiRequest('/api/categories', 'GET'),
        apiRequest('/api/accounts', 'GET'),
        apiRequest(`/api/transactions?year=${year}&month=${month}`, 'GET'),
        apiRequest(`/api/transfers?year=${year}&month=${month}`, 'GET'),
        apiRequest(`/api/adjustments?year=${year}&month=${month}`, 'GET'),
        apiRequest(`/api/statistics?year=${year}&month=${month}`, 'GET'),
    ]);
    if (!cats || !accs || !list || !moves || !fixes || !stats) return;
    categories = cats;
    accounts = accs;
    transactions = list;
    transfers = moves;
    adjustments = fixes;
    summary = stats;
    await loadPending();
    render();
}

// 예정은 이번 달을 볼 때만 — 이번 달 말까지 남은 예정 + 밀린 예정(서버가 같이 준다)
async function loadPending() {
    const today = new Date();
    const isThisMonth = year === today.getFullYear() && month === today.getMonth() + 1;
    if (!isThisMonth) {
        pending = [];
        return;
    }
    const lastDay = new Date(year, month, 0).getDate();
    const until = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;
    pending = (await apiRequest(`/api/recurring/pending?until=${until}`, 'GET')) ?? [];
}

// ───── 그리기
function render() {
    monthLabel.textContent = `${year}년 ${month}월`;

    document.getElementById('sumIncome').textContent = won(summary.totalIncome);
    document.getElementById('sumExpense').textContent = won(summary.totalExpense);
    document.getElementById('sumBalance').textContent = won(summary.balance);

    // 거래와 이체를 날짜순(최근 먼저)으로 한 목록에 — 그날 돈 흐름이 한눈에 (10/21 결정)
    // 정렬은 표시 순서일 뿐 계산이 아니다(ADR-038). 같은 날짜면 거래 먼저
    const rows = [
        ...transactions.map(t => ({ date: t.transDate, order: 0, num: t.transNum, el: () => createItem(t) })),
        ...transfers.map(r => ({ date: r.transferDate, order: 1, num: r.transferNum, el: () => createTransferItem(r) })),
        ...adjustments.map(j => ({ date: j.adjustmentDate, order: 2, num: j.adjustmentNum, el: () => createAdjustmentItem(j) })),
    ].sort((a, b) => b.date.localeCompare(a.date) || a.order - b.order || a.num - b.num);

    listEl.innerHTML = '';
    rows.forEach(row => listEl.appendChild(row.el()));

    pendingList.innerHTML = '';
    pending.forEach(p => pendingList.appendChild(createPendingItem(p)));
    pendingSection.hidden = pending.length === 0;
    emptyEl.hidden = rows.length > 0;

    // 월 이동·저장·삭제 모두 render() 를 거치므로 여기 한 줄이면 전부 갱신된다
    const extras = [
        transfers.length > 0 ? `이체 ${transfers.length}건` : '',
        adjustments.length > 0 ? `보정 ${adjustments.length}건` : '',
    ].filter(Boolean);
    countList.textContent = [`${transactions.length}건`, ...extras].join(' · ');
}

function createItem(t) {
    const li = document.createElement('li');
    li.className = 'transaction-item';
    const sign = t.transType === 'I' ? '+' : '-';
    const amountClass = t.transType === 'I' ? 'amount-income' : 'amount-expense';
    li.innerHTML = `
        <span class="trans-date">${t.transDate.slice(5)}</span>
        <span class="trans-category">${escapeHtml(t.categoryEmoji ?? '')} ${escapeHtml(t.categoryName)}</span>
        <span class="trans-memo">${escapeHtml(t.transMemo ?? '')}<small class="trans-account">${escapeHtml(t.accountName ?? '')}</small></span>
        <span class="trans-amount ${amountClass}">${sign}${won(t.transAmount)}</span>
        <span class="actions">
            <button data-action="edit" data-kind="trans" data-id="${t.transNum}">수정</button>
        </span>
    `;
    return li;
}

// 이체 줄 — 부호 없음(수입도 지출도 아니다), 회색
function createTransferItem(r) {
    const li = document.createElement('li');
    li.className = 'transaction-item transfer-item';
    li.innerHTML = `
        <span class="trans-date">${r.transferDate.slice(5)}</span>
        <span class="trans-category">↔ 이체</span>
        <span class="trans-memo">${escapeHtml(r.fromAccountName)} → ${escapeHtml(r.toAccountName)}${r.transferMemo ? ' · ' + escapeHtml(r.transferMemo) : ''}</span>
        <span class="trans-amount">${won(r.transferAmount)}</span>
        <span class="actions">
            <button data-action="edit" data-kind="transfer" data-id="${r.transferNum}">수정</button>
        </span>
    `;
    return li;
}

// 잔액 보정 줄 — 회색(이체와 같은 자리). 고치기는 없고 지우기만 — 다시 맞추려면 계좌 화면에서 한 번 더
function createAdjustmentItem(j) {
    const li = document.createElement('li');
    li.className = 'transaction-item transfer-item';
    const sign = j.adjustmentAmount > 0 ? '+' : '-';
    li.innerHTML = `
        <span class="trans-date">${j.adjustmentDate.slice(5)}</span>
        <span class="trans-category">⚖ 잔액 보정</span>
        <span class="trans-memo">${escapeHtml(j.accountName)}</span>
        <span class="trans-amount">${sign}${won(Math.abs(j.adjustmentAmount))}</span>
        <span class="actions">
            <button data-action="delete" data-kind="adjustment" data-id="${j.adjustmentNum}">삭제</button>
        </span>
    `;
    return li;
}

// 예정 줄 — 점선, 밀린 건 "밀림". 버튼은 [확인] · [건너뛰기]
function createPendingItem(p) {
    const r = p.rule;
    const li = document.createElement('li');
    li.className = r.kind === 'T' ? 'transaction-item pending-item transfer-item' : 'transaction-item pending-item';
    const sign = r.kind === 'I' ? '+' : (r.kind === 'E' ? '-' : '');
    const amountClass = r.kind === 'I' ? 'amount-income' : (r.kind === 'E' ? 'amount-expense' : '');
    const what = r.kind === 'T' ? '↔ 이체' : `${escapeHtml(r.categoryEmoji ?? '')} ${escapeHtml(r.categoryName)}`;
    // 이체 줄과 같은 모양 — 이체는 "국민통장 → 신한카드 · 메모", 거래는 "메모 + 작은 계좌 이름"
    const memo = escapeHtml(r.ruleMemo ?? '');
    const memoAndWhere = r.kind === 'T'
        ? `${escapeHtml(r.fromAccountName)} → ${escapeHtml(r.toAccountName)}${memo ? ' · ' + memo : ''}`
        : `${memo}<small class="trans-account">${escapeHtml(r.accountName)}</small>`;
    li.innerHTML = `
        <span class="trans-date">${p.scheduledDate.slice(5)}${p.overdue ? '<span class="overdue">밀림</span>' : ''}</span>
        <span class="trans-category">${what}</span>
        <span class="trans-memo">${memoAndWhere}</span>
        <span class="trans-amount ${amountClass}">${sign}${won(r.ruleAmount)}</span>
        <span class="actions">
            <button data-action="confirm" data-rule="${r.ruleNum}" data-date="${p.scheduledDate}">확인</button>
            <button data-action="skip" data-rule="${r.ruleNum}" data-date="${p.scheduledDate}">건너뛰기</button>
        </span>
    `;
    return li;
}

// ───── 예정 확인 · 건너뛰기 (ADR-067)
const pendingSection = document.getElementById('pendingSection');
const pendingList = document.getElementById('pendingList');
const confirmModal = document.getElementById('confirmModal');
const confirmForm = document.getElementById('confirmForm');
const confirmMessage = document.getElementById('confirmMessage');
let confirming = null;     // { ruleNum, scheduledDate }

pendingList.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-action]');
    if (!btn) return;
    const target = { ruleNum: Number(btn.dataset.rule), scheduledDate: btn.dataset.date };
    if (btn.dataset.action === 'confirm') openConfirmModal(target);
    if (btn.dataset.action === 'skip') skipPending(target);
});

document.getElementById('confirmCancelBtn').addEventListener('click', () => { confirmModal.hidden = true; });

// 금액은 규칙 금액, 날짜는 예정일(미래면 오늘 — 미리 낸 것). 둘 다 고칠 수 있다
function openConfirmModal(target) {
    const p = pending.find(x => x.rule.ruleNum === target.ruleNum && x.scheduledDate === target.scheduledDate);
    const r = p.rule;
    confirming = target;
    confirmForm.reset();
    confirmMessage.textContent = '';
    const name = r.kind === 'T' ? `${r.fromAccountName} → ${r.toAccountName}` : `${r.categoryName} · ${r.accountName}`;
    document.getElementById('confirmHint').textContent = `${name} · 예정일 ${p.scheduledDate.slice(5)}. 실제와 다르면 고쳐 주세요.`;
    document.getElementById('confirmAmount').value = r.ruleAmount;
    const today = todayString();
    document.getElementById('confirmDate').max = today;
    document.getElementById('confirmDate').value = p.scheduledDate > today ? today : p.scheduledDate;
    confirmModal.hidden = false;
}

confirmForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const body = {
        scheduledDate: confirming.scheduledDate,
        amount: Number(document.getElementById('confirmAmount').value),
        actualDate: document.getElementById('confirmDate').value,
    };
    await withButtonLock(document.getElementById('confirmSubmitBtn'), async () => {
        try {
            await apiRequest(`/api/recurring/${confirming.ruleNum}/confirm`, 'POST', body);
            confirmModal.hidden = true;
            await loadTransactions();
        } catch (error) {
            confirmMessage.textContent = error.message;
        }
    });
});

async function skipPending(target) {
    if (!confirm('이번 예정만 건너뛸까요? 다음 예정은 그대로 떠요.')) return;
    try {
        await apiRequest(`/api/recurring/${target.ruleNum}/skip`, 'POST', { scheduledDate: target.scheduledDate });
        await loadTransactions();
    } catch (error) {
        alert(error.message);
        await loadTransactions();
    }
}

// ───── 월 이동 (12월 → 다음 해 1월)
function changeMonth(delta) {
    month += delta;
    if (month > 12) { month = 1; year++; }
    if (month < 1) { month = 12; year--; }
    loadTransactions();
}

document.getElementById('prevMonth').addEventListener('click', () => changeMonth(-1));
document.getElementById('nextMonth').addEventListener('click', () => changeMonth(1));

// ───── 모달
document.getElementById('addBtn').addEventListener('click', () => openModal(null));
document.getElementById('cancelBtn').addEventListener('click', closeModal);

// 삭제는 목록이 아니라 수정 창 안에 둔다 — 줄마다 수정 옆에 붙어 있으면 잘못 누르기 쉽다(10/28)
const deleteBtn = document.getElementById('deleteBtn');
deleteBtn.addEventListener('click', () => { if (editing) removeItem(editing); });

listEl.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-action]');
    if (!btn) return;
    const target = { kind: btn.dataset.kind, num: Number(btn.dataset.id) };
    if (btn.dataset.action === 'edit') openModal(target);
    if (btn.dataset.action === 'delete') removeItem(target);
});

modal.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
        // 수정 중엔 거래 ↔ 이체를 못 바꾼다 — 다른 API·다른 기록이라 "바꾸기"가 아니라 지우고 새로 적기
        if (editing && (tab.dataset.type === 'T') !== (editing.kind === 'transfer')) return;
        setModalType(tab.dataset.type);
    });
});

function setModalType(type) {
    modalType = type;
    modal.querySelectorAll('.tab').forEach(x => x.classList.toggle('active', x.dataset.type === type));
    const isTransfer = type === 'T';
    document.getElementById('transactionFields').hidden = isTransfer;
    document.getElementById('transferFields').hidden = !isTransfer;
    document.getElementById('categoryNum').required = !isTransfer;
    if (!isTransfer) fillCategoryOptions();
}

function openModal(target) {
    form.reset();
    resultMessage.textContent = '';
    editing = target;

    // 미래 날짜 1차 방어. 서버 @PastOrPresent 는 그대로 두고 왕복만 아낀다
    document.getElementById('transDate').max = todayString();

    const t = target?.kind === 'trans' ? transactions.find(x => x.transNum === target.num) : null;
    const r = target?.kind === 'transfer' ? transfers.find(x => x.transferNum === target.num) : null;

    setModalType(r ? 'T' : (t ? t.transType : 'E'));
    fillAccountOptions(t);

    document.getElementById('modalTitle').textContent = r ? '이체 수정' : (t ? '거래 수정' : '추가');
    deleteBtn.hidden = !target;

    if (t) {
        document.getElementById('categoryNum').value = t.categoryNum;
        document.getElementById('transAmount').value = t.transAmount;
        document.getElementById('transDate').value = t.transDate;
        document.getElementById('transMemo').value = t.transMemo ?? '';
    } else if (r) {
        document.getElementById('fromAccountNum').value = r.fromAccountNum;
        document.getElementById('toAccountNum').value = r.toAccountNum;
        document.getElementById('transAmount').value = r.transferAmount;
        document.getElementById('transDate').value = r.transferDate;
        document.getElementById('transMemo').value = r.transferMemo ?? '';
    } else {
        document.getElementById('transDate').value = todayString();
    }
    modal.hidden = false;
}

function closeModal() {
    modal.hidden = true;
    editing = null;
    resultMessage.textContent = '';
}

// 미래 날짜는 서버가 400 으로 막는다 (@PastOrPresent). 화면에서도 오늘을 상한으로
function todayString() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function fillCategoryOptions() {
    const select = document.getElementById('categoryNum');
    select.innerHTML = '';

    const list = categories.filter(c => c.categoryType === modalType);
    list.filter(c => c.parentCategoryNum === null).forEach(p => {
        select.add(new Option(`${p.categoryEmoji ?? ''} ${p.categoryName}`, p.categoryNum));
        list.filter(c => c.parentCategoryNum === p.categoryNum)
            .forEach(child => select.add(new Option(`　└ ${child.categoryEmoji ?? ''} ${child.categoryName}`, child.categoryNum)));
    });
}

// 계좌 선택지 — 열린 계좌만(서버 정렬: 기본 '현금' 먼저 → 새로 추가할 땐 첫 항목이 기본 계좌)
// 수정하는 거래가 닫힌 계좌에 있으면 그 계좌를 "(닫힘)"으로 하나 더 넣고 값은 비운다 — 비워 보내면 서버가 지금 계좌를 그대로 둔다
function fillAccountOptions(t) {
    ['accountNum', 'fromAccountNum', 'toAccountNum'].forEach(id => {
        const select = document.getElementById(id);
        select.innerHTML = '';
        accounts.forEach(a => select.add(new Option(a.accountName, a.accountNum)));
    });
    if (t && t.accountNum && !accounts.some(a => a.accountNum === t.accountNum)) {
        const select = document.getElementById('accountNum');
        select.add(new Option(`${t.accountName} (닫힘)`, ''), 0);
        select.value = '';
    } else if (t && t.accountNum) {
        document.getElementById('accountNum').value = t.accountNum;
    } else if (!t) {
        // 새로 추가할 땐 마지막에 고른 계좌 — 카드를 주로 쓰면 매번 '현금'에서 바꾸지 않게. 닫혔거나 남의 계좌면 기본 그대로
        const last = loadLastAccount();
        if (accounts.some(a => a.accountNum === last)) document.getElementById('accountNum').value = last;
    }
    // 이체는 두 계좌가 달라야 하니 받는 계좌의 처음 값은 두 번째 계좌로
    if (accounts.length > 1) document.getElementById('toAccountNum').value = accounts[1].accountNum;
}

// 마지막에 고른 계좌 — 이 브라우저에만 남는 편의값. 저장소가 막힌 환경(사생활 보호 창 등)에선 조용히 기본 계좌로
const LAST_ACCOUNT_KEY = 'lastAccountNum';

function loadLastAccount() {
    try {
        return Number(localStorage.getItem(LAST_ACCOUNT_KEY)) || null;
    } catch {
        return null;
    }
}

function saveLastAccount(accountNum) {
    try {
        localStorage.setItem(LAST_ACCOUNT_KEY, String(accountNum));
    } catch {
        // 못 남겨도 다음에 기본 계좌로 시작할 뿐이다
    }
}

// ───── 저장 (추가 / 수정)
form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const amount = Number(document.getElementById('transAmount').value);
    const date = document.getElementById('transDate').value;
    const memo = document.getElementById('transMemo').value.trim() || null;

    let url;
    let method;
    let body;
    if (modalType === 'T') {
        body = {
            fromAccountNum: Number(document.getElementById('fromAccountNum').value),
            toAccountNum: Number(document.getElementById('toAccountNum').value),
            transferAmount: amount,
            transferDate: date,
            transferMemo: memo,
        };
        const r = editing ? transfers.find(x => x.transferNum === editing.num) : null;
        url = r ? `/api/transfers/${r.transferNum}?version=${r.version}` : '/api/transfers';
        method = r ? 'PUT' : 'POST';
    } else {
        // transType 은 보내지 않는다. 서버가 categoryNum 으로 파생 (ADR-008)
        const accountValue = document.getElementById('accountNum').value;
        body = {
            categoryNum: Number(document.getElementById('categoryNum').value),
            accountNum: accountValue ? Number(accountValue) : null,
            transAmount: amount,
            transDate: date,
            transMemo: memo,
        };
        const t = editing ? transactions.find(x => x.transNum === editing.num) : null;
        url = t ? `/api/transactions/${t.transNum}?version=${t.version}` : '/api/transactions';
        method = t ? 'PUT' : 'POST';
    }

    await withButtonLock(document.getElementById('submitBtn'), async () => {
        try {
            await apiRequest(url, method, body);
            if (modalType !== 'T' && body.accountNum) saveLastAccount(body.accountNum);
            closeModal();
            await loadTransactions();
        } catch (error) {
            if (isStale(error)) return handleStale(error);
            resultMessage.textContent = error.message;
        }
    });
});

// ───── 삭제
async function removeItem(target) {
    if (!confirm('삭제하시겠습니까?')) return;

    let url;
    if (target.kind === 'adjustment') {
        url = `/api/adjustments/${target.num}`;
    } else if (target.kind === 'transfer') {
        url = `/api/transfers/${target.num}?version=${transfers.find(x => x.transferNum === target.num).version}`;
    } else {
        url = `/api/transactions/${target.num}?version=${transactions.find(x => x.transNum === target.num).version}`;
    }
    try {
        await apiRequest(url, 'DELETE');
        closeModal();
        await loadTransactions();
    } catch (error) {
        if (isStale(error)) return handleStale(error);
        // 삭제는 모달을 거치지 않으므로 resultMessage(모달 안)에 쓰면 보이지 않는다.
        // 메시지 표시용 UI 를 만들기 전까지는 alert 로 (뷰 대공사 때 바꾼다)
        alert(error.message);
    }
}

// 409(다른 곳에서 수정됨)와 404(다른 곳에서 삭제됨)는 둘 다 "화면이 서버보다 낡았다"는 신호다.
// 400 처럼 사용자가 입력을 고쳐 다시 시도할 수 있는 에러가 아니므로 — 몇 번을 눌러도 같은 결과다 —
// 화면을 최신 상태로 되돌리는 것 말고 방법이 없다 (ADR-006).
// loadTransactions() 가 카테고리·계좌까지 다시 받아오므로 드롭다운의 낡은 항목도 함께 정리된다.
function isStale(error) {
    return error.status === 409 || error.status === 404;
}

function handleStale(error) {
    alert(error.status === 409
        ? '다른 곳에서 이미 수정된 내역입니다. 최신 내용을 불러왔습니다.'
        : '다른 곳에서 삭제된 항목입니다. 최신 내용을 불러왔습니다.');
    closeModal();
    loadTransactions();
}

// ───── 시작
loadTransactions();
