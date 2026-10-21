requireLogin();

const now = new Date();
let year = now.getFullYear();
let month = now.getMonth() + 1;

const monthLabel = document.getElementById('monthLabel');

// ───── 조회
async function loadStatistics() {
    const data = await apiRequest(`/api/statistics?year=${year}&month=${month}`, 'GET');
    if (!data) return;
    render(data);
}

// ───── 그리기 — 계산 없이 표시만 (ADR-038). 합계·비율·잔액 전부 서버 값
function render(s) {
    monthLabel.textContent = `${year}년 ${month}월`;

    document.getElementById('totalIncome').textContent = won(s.totalIncome);
    document.getElementById('totalExpense').textContent = won(s.totalExpense);
    document.getElementById('balance').textContent = won(s.balance);
    // 저축률 — 수입 0 이면 서버가 null (계산 불가 판단은 서버 몫 — ADR-039)
    document.getElementById('savingsRate').textContent = percent(s.savingsRate);

    renderEnvelopes('expenseList', 'expenseEmpty', s.expenseList);
    renderEnvelopes('incomeList', 'incomeEmpty', s.incomeList);
    renderTrend(s.monthlyList);
}

function renderEnvelopes(listId, emptyId, list) {
    const listEl = document.getElementById(listId);
    listEl.innerHTML = '';
    list.forEach(p => listEl.appendChild(createEnvelope(p)));
    document.getElementById(emptyId).hidden = list.length > 0;
}

// 대분류 한 줄. 소분류가 있으면 <details> 로 감싸 클릭하면 펼쳐진다 (JS 토글 불필요)
// 서버는 대분류 직접 거래도 children 에 자기 자신(같은 categoryNum)으로 넣는다 → children 은 비지 않는다.
// 그래서 "소분류가 있는가" 는 길이가 아니라 자기 자신이 아닌 줄이 있는지로 본다
function createEnvelope(p) {
    const row = `
        <span class="stat-name">${escapeHtml(p.categoryEmoji ?? '')} ${escapeHtml(p.categoryName)}</span>
        <span class="stat-ratio">${percent(p.ratio)}</span>
        <span class="stat-amount">${won(p.amount)}</span>
    `;

    if (!p.children.some(c => c.categoryNum !== p.categoryNum)) {
        const div = document.createElement('div');
        div.className = 'stat-item';
        div.innerHTML = row;
        return div;
    }

    const details = document.createElement('details');
    details.className = 'stat-envelope';
    details.innerHTML = `<summary class="stat-item">${row}</summary>`;
    p.children.forEach(c => {
        const div = document.createElement('div');
        div.className = 'stat-item child';
        div.innerHTML = `
            <span class="stat-name">${escapeHtml(c.categoryEmoji ?? '')} ${escapeHtml(c.categoryName)}</span>
            <span class="stat-ratio">${percent(c.ratio)}</span>
            <span class="stat-amount">${won(c.amount)}</span>
        `;
        details.appendChild(div);
    });
    return details;
}

// 막대 길이만 화면이 정한다 — 6개월 중 가장 큰 금액을 100% 로 (표시 스케일이지 업무 계산이 아님)
function renderTrend(list) {
    const listEl = document.getElementById('monthlyList');
    listEl.innerHTML = '';

    const max = Math.max(1, ...list.map(m => Math.max(m.totalIncome, m.totalExpense)));

    list.forEach(m => {
        const div = document.createElement('div');
        div.className = 'trend-item';
        div.innerHTML = `
            <span class="trend-label">${m.yearMonth}</span>
            <span class="trend-bars">
                <span class="bar bar-income"></span>
                <span class="bar bar-expense"></span>
            </span>
            <span class="trend-balance">${won(m.balance)}</span>
        `;
        // 너비는 innerHTML 의 style 속성이 아니라 DOM 으로 준다 — CSP 가 인라인 style 속성을 막는다
        div.querySelector('.bar-income').style.width = `${m.totalIncome / max * 100}%`;
        div.querySelector('.bar-expense').style.width = `${m.totalExpense / max * 100}%`;
        listEl.appendChild(div);
    });
}

// 서버는 소수 1자리 BigDecimal 이지만 JSON 숫자로 오면 75.0 → 75 가 된다. 66.7% 와 자릿수를 맞춘다
function percent(r) {
    return r === null ? '-' : `${r.toFixed(1)}%`;
}

// ───── 월 이동 (12월 → 다음 해 1월)
function changeMonth(delta) {
    month += delta;
    if (month > 12) { month = 1; year++; }
    if (month < 1) { month = 12; year--; }
    loadStatistics();
}

document.getElementById('prevMonth').addEventListener('click', () => changeMonth(-1));
document.getElementById('nextMonth').addEventListener('click', () => changeMonth(1));

// ───── 시작
loadStatistics();
