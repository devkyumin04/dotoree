requireLogin();

// 서버 응답을 그대로 들고 있는다. version 이 여기 담겨 있고,
// 수정·삭제할 때 transNum 으로 찾아 꺼내 쓴다 (DOM 에는 표시만 맡김)
let transactions = [];
let categories = [];

// 화면 위 수입/지출/잔액. 목록을 직접 더하지 않고 통계 API 값을 그대로 표시한다.
// 돈 합계는 계산하는 곳이 한 군데여야 통계 화면과 어긋나지 않는다 (ADR-038, 040)
let summary = null;

// 모달의 지출/수입 토글. 서버로 보내는 값이 아니라 카테고리 목록을 거르는 용도
let modalType = 'E';

// 지금 수정 중인 거래 번호. 추가 모드면 null
// DOM(hidden input) 대신 JS 가 들고 있는다 — 상태는 JS 소유, DOM 은 표시만
let editingNum = null;

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
async function loadCategories() {
    const data = await apiRequest('/api/categories', 'GET');
    if (!data) return;
    categories = data;
}

// 카테고리는 시작할 때 한 번만 불러오면 다른 탭에서 추가·삭제한 것을 모른다.
// 거래와 달리 version 이 없어 충돌(409)로 걸러지지도 않고, 지워진 카테고리로
// 저장하면 404 가 난다. 그래서 목록을 새로 받을 때 카테고리도 같이 받는다.
// 세 요청은 서로의 결과가 필요 없어 동시에 보낸다 — 요청 수는 늘어도 기다리는 시간은 한 번 (ADR-040)
async function loadTransactions() {
    const [, list, stats] = await Promise.all([
        loadCategories(),
        apiRequest(`/api/transactions?year=${year}&month=${month}`, 'GET'),
        apiRequest(`/api/statistics?year=${year}&month=${month}`, 'GET'),
    ]);
    if (!list || !stats) return;
    transactions = list;
    summary = stats;
    render();
}

// ───── 그리기
function render() {
    monthLabel.textContent = `${year}년 ${month}월`;

    document.getElementById('sumIncome').textContent = won(summary.totalIncome);
    document.getElementById('sumExpense').textContent = won(summary.totalExpense);
    document.getElementById('sumBalance').textContent = won(summary.balance);

    listEl.innerHTML = '';
    transactions.forEach(t => listEl.appendChild(createItem(t)));
    emptyEl.hidden = transactions.length > 0;

    // 월 이동·저장·삭제 모두 render() 를 거치므로 여기 한 줄이면 전부 갱신된다
    countList.textContent = `${transactions.length}건`;
}

function createItem(t) {
    const li = document.createElement('li');
    li.className = 'transaction-item';
    const sign = t.transType === 'I' ? '+' : '-';
    const amountClass = t.transType === 'I' ? 'amount-income' : 'amount-expense';
    li.innerHTML = `
        <span class="trans-date">${t.transDate.slice(5)}</span>
        <span class="trans-category">${escapeHtml(t.categoryEmoji ?? '')} ${escapeHtml(t.categoryName)}</span>
        <span class="trans-memo">${escapeHtml(t.transMemo ?? '')}</span>
        <span class="trans-amount ${amountClass}">${sign}${won(t.transAmount)}</span>
        <span class="actions">
            <button data-action="edit" data-id="${t.transNum}">수정</button>
            <button data-action="delete" data-id="${t.transNum}">삭제</button>
        </span>
    `;
    return li;
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

listEl.addEventListener('click', (e) => {
    const btn = e.target.closest('button[data-action]');
    if (!btn) return;
    const id = Number(btn.dataset.id);
    if (btn.dataset.action === 'edit') openModal(id);
    if (btn.dataset.action === 'delete') removeTransaction(id);
});

modal.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
        modalType = tab.dataset.type;
        modal.querySelectorAll('.tab').forEach(x => x.classList.toggle('active', x === tab));
        fillCategoryOptions();
    });
});

function openModal(id) {
    form.reset();
    resultMessage.textContent = '';

    // 미래 날짜 1차 방어. 서버 @PastOrPresent 는 그대로 두고 왕복만 아낀다
    document.getElementById('transDate').max = todayString();

    const t = id ? transactions.find(x => x.transNum === id) : null;

    modalType = t ? t.transType : 'E';
    modal.querySelectorAll('.tab').forEach(x => x.classList.toggle('active', x.dataset.type === modalType));
    fillCategoryOptions();

    editingNum = id;
    document.getElementById('modalTitle').textContent = id ? '거래 수정' : '거래 추가';

    if (t) {
        document.getElementById('categoryNum').value = t.categoryNum;
        document.getElementById('transAmount').value = t.transAmount;
        document.getElementById('transDate').value = t.transDate;
        document.getElementById('transMemo').value = t.transMemo ?? '';
    } else {
        document.getElementById('transDate').value = todayString();
    }
    modal.hidden = false;
}

function closeModal() {
    modal.hidden = true;
    editingNum = null;
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

// ───── 저장 (추가 / 수정)
form.addEventListener('submit', async (e) => {
    e.preventDefault();


    // transType 은 보내지 않는다. 서버가 categoryNum 으로 파생 (ADR-008)
    const body = {
        categoryNum: Number(document.getElementById('categoryNum').value),
        transAmount: Number(document.getElementById('transAmount').value),
        transDate: document.getElementById('transDate').value,
        transMemo: document.getElementById('transMemo').value.trim() || null,
    };

    await withButtonLock(document.getElementById('submitBtn'), async () => {
        try {
            if (editingNum) {
                const t = transactions.find(x => x.transNum === editingNum);
                await apiRequest(`/api/transactions/${editingNum}?version=${t.version}`, 'PUT', body);
            } else {
                await apiRequest('/api/transactions', 'POST', body);
            }
            closeModal();
            await loadTransactions();
        } catch (error) {
            if (isStale(error)) return handleStale(error);
            resultMessage.textContent = error.message;
        }
    });
});

// ───── 삭제
async function removeTransaction(id) {
    if (!confirm('삭제하시겠습니까?')) return;

    const t = transactions.find(x => x.transNum === id);
    try {
        await apiRequest(`/api/transactions/${id}?version=${t.version}`, 'DELETE');
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
// loadTransactions() 가 카테고리까지 다시 받아오므로 드롭다운의 낡은 항목도 함께 정리된다.
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
// loadTransactions() 가 카테고리까지 받아온다
loadTransactions();
