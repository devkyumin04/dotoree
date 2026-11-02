// 모든 API 요청의 입구. Access 는 메모리(아래 accessToken 변수), Refresh 는 HttpOnly 쿠키(JS 는 못 읽는다 — 브라우저가 /api/auth 로만 실어 보낸다)
//
// Access 를 메모리에 두는 이유 — localStorage 는 XSS 가 한 줄로 꺼내 가지만 변수는 저장된 것을 꺼내 갈 수 없다 (ADR-071)
// 대신 화면(.html)을 옮기면 변수가 사라진다 → 새 화면의 첫 API 호출이 쿠키로 재발급 1번을 먼저 한다
// "로그인한 적 있나"는 localStorage 의 hasSession 표시 하나로 본다 — 토큰이 아니라 훔쳐도 쓸 데가 없고,
// 이게 없으면 재발급을 시도하지 않아 처음 온 방문자에게 401 이 쌓이지 않는다
//
// 401 은 두 가지다
//  · 토큰 없이 401 → 로그인 실패(이메일·비번 틀림) → 에러로 던져 화면이 문구를 보여 주게 (2026-09-23 화면 QA)
//  · 토큰을 보냈는데 401 → Access 만료 → Refresh 로 새 Access 를 받아 원래 요청을 한 번만 다시 (Refresh 7단계, ADR-055)
//    Refresh 도 401(카드 폐기·만료)이면 그때가 진짜 만료 → 로그인 화면으로
//
// options.silentExpire — 진짜 만료여도 로그인 화면으로 보내지 않고 { status: 401, expired: true } 를 던진다.
// 공개 화면(메인)용: 만료면 조용히 비로그인 모습으로. Access 만료만으로는 여기까지 오지 않는다(재발급이 먼저)
//
// options.anonymous — 로그인·가입·비번 재설정처럼 토큰 없이 부르는 공개 요청. 토큰을 붙이지도, 재발급하지도 않는다(401 = 그냥 실패)
async function apiRequest(url, method, body, options = {}) {
    // 이 화면에서 처음 부르는 API — 메모리가 비었는데 로그인한 적이 있으면 쿠키로 새 Access 를 먼저 받는다
    // 같은 화면의 동시 요청(거래 화면 Promise.all 등)은 refreshAccessToken 의 single-flight 가 한 번으로 묶는다
    if (!options.anonymous && !accessToken && hasSession()) {
        if (!await refreshAccessToken(null)) {
            return expireSession(options);
        }
    }

    const sentToken = options.anonymous ? null : accessToken;
    let response = await sendRequest(url, method, body, sentToken);

    if (response.status === 401 && sentToken) {
        const newToken = await refreshAccessToken(sentToken);
        if (!newToken) {
            return expireSession(options);
        }
        // 재시도는 한 번만 — 새 Access 로도 401 이면(그사이 탈퇴 등) 만료로. 다시 재발급하면 무한 반복이 된다
        response = await sendRequest(url, method, body, newToken);
        if (response.status === 401) {
            return expireSession(options);
        }
    }

    if (!response.ok) {
        throw { status: response.status, message: await readErrorMessage(response) };
    }

    if (response.status === 204) {
        return null;
    }

    return await response.json();
}

// 에러 본문은 ProblemDetail(RFC 9457) JSON — 보여 줄 문구는 detail (ADR-061)
// JSON 이 아니면(Nginx 502 같은 서버 밖 에러) 본문 그대로. 화면은 지금처럼 error.message 만 쓴다
async function readErrorMessage(response) {
    const text = await response.text();
    try {
        return JSON.parse(text).detail ?? text;
    } catch {
        return text;
    }
}

function sendRequest(url, method, body, token) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) {
        headers['Authorization'] = 'Bearer ' + token;
    }
    return fetch(url, {
        method: method,
        headers: headers,
        body: body ? JSON.stringify(body) : null,
    });
}

// 진짜 만료 — 세션을 지우고 로그인 화면으로(쓰던 중이니 다시 로그인해서 이어 가게). 메인은 silentExpire
function expireSession(options) {
    clearSession();
    if (options.silentExpire) {
        throw { status: 401, expired: true };
    }
    window.location.href = '/views/user/login.html';
}

// 메모리 Access — 화면(.html)을 옮기면 사라진다. 탭마다 따로다
let accessToken = null;
const SESSION_KEY = 'hasSession';

// 옛 방식(Access 를 localStorage 에)으로 로그인해 둔 브라우저 — 토큰 원문은 지우고 표시만 남긴다(쿠키가 살아 있으면 로그인이 이어진다)
// 지우지 않으면 XSS 가 옛 토큰을 15분 동안 꺼내 갈 수 있다. 배포 뒤 한동안만 필요한 줄 (ADR-071)
if (localStorage.getItem('accessToken')) {
    localStorage.removeItem('accessToken');
    localStorage.setItem(SESSION_KEY, '1');
}

// 로그인한 적 있나 — 토큰이 아니라 표시(값 '1')다. 로그인·가입이 켜고, 로그아웃·탈퇴·만료가 끈다
function hasSession() {
    return localStorage.getItem(SESSION_KEY) === '1';
}

// 로그인·가입 성공 — 응답의 Access 를 메모리에 두고 표시를 켠다(다음 화면은 쿠키로 재발급해 이어 간다)
function startSession(token) {
    accessToken = token;
    localStorage.setItem(SESSION_KEY, '1');
}

function clearSession() {
    accessToken = null;
    localStorage.removeItem(SESSION_KEY);
}

// 진행 중인 재발급. 한 화면의 동시 요청(거래 화면 Promise.all 등)이 함께 401 을 받아도 재발급은 한 번 — 나머지는 이것을 같이 기다린다
// (single-flight, ADR-055 선택 10). 변수는 탭마다 따로라 탭 두 개의 동시 재발급은 못 막는다 → 아래 Web Locks 로 탭끼리 한 번에 하나씩, 못 막은 나머지는 서버 유예 30초가 맡는다
let refreshing = null;

// 새 Access 를 돌려준다. 카드가 무효(401)면 null. 서버 오류·네트워크 실패는 던진다 — 일시 장애로 로그아웃시키지 않는다
async function refreshAccessToken(sentToken) {
    // 보낸 토큰과 지금 토큰이 다르다 = 다른 요청이 이미 재발급을 끝냈다 → 또 부르지 않고 새 토큰으로 재시도
    // (재발급이 끝난 뒤에 도착한 늦은 401 — refreshing 은 이미 비었다)
    const current = accessToken;
    if (current && current !== sentToken) {
        return current;
    }

    if (!refreshing) {
        refreshing = requestRefresh().finally(() => {
            refreshing = null;
        });
    }
    return refreshing;
}

// 탭끼리 한 번에 하나씩 — 새 탭 두 개가 같은 카드로 동시에 재발급하면 응답 순서가 뒤집혀 폐기된 카드가 쿠키에 남을 수 있다 (ADR-071)
// 락 안에서 응답(Set-Cookie 포함)까지 끝난 뒤에야 다음 탭이 요청하므로 다음 탭은 새 카드로 정상 로테이션한다
function requestRefresh() {
    if (navigator.locks) {
        return navigator.locks.request('dotoree-refresh', doRefresh);
    }
    return doRefresh();
}

// apiRequest 를 거치지 않는다 — Access 가 필요 없고(쿠키가 증명), 여기서 401 이 나면 또 재발급을 부르는 순환이 된다
async function doRefresh() {
    const response = await fetch('/api/auth/refresh', { method: 'POST' });
    if (response.status === 401) {
        return null;
    }
    if (!response.ok) {
        throw { status: response.status, message: await readErrorMessage(response) };
    }
    const data = await response.json();
    accessToken = data.accessToken;
    return accessToken;
}

// 로그아웃 — 서버가 장부(Refresh 일행) 폐기 + 지우는 쿠키(HttpOnly 라 JS 는 못 지운다). 성공해야 Access 도 지운다
// 실패면 아무것도 지우지 않고 던진다 — 장부가 살아 있는데 로그아웃된 것처럼 보이면 다시 누를 이유가 사라진다
// apiRequest 를 거치지 않는 이유는 requestRefresh 와 같다 (401 → 재발급·로그인 이동이 끼면 안 된다)
async function logoutRequest() {
    const response = await fetch('/api/auth/logout', { method: 'POST' });
    if (!response.ok) {
        throw { status: response.status, message: await readErrorMessage(response) };
    }
    clearSession();
}
