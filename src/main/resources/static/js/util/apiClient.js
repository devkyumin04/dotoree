// 모든 API 요청의 입구. Access 는 localStorage, Refresh 는 HttpOnly 쿠키(JS 는 못 읽는다 — 브라우저가 /api/auth 로만 실어 보낸다)
//
// 401 은 두 가지다
//  · 토큰 없이 401 → 로그인 실패(이메일·비번 틀림) → 에러로 던져 화면이 문구를 보여 주게 (2026-09-23 화면 QA)
//  · 토큰을 보냈는데 401 → Access 만료 → Refresh 로 새 Access 를 받아 원래 요청을 한 번만 다시 (Refresh 7단계, ADR-055)
//    Refresh 도 401(카드 폐기·만료)이면 그때가 진짜 만료 → 로그인 화면으로
//
// options.silentExpire — 진짜 만료여도 로그인 화면으로 보내지 않고 { status: 401, expired: true } 를 던진다.
// 공개 화면(메인)용: 만료면 조용히 비로그인 모습으로. Access 만료만으로는 여기까지 오지 않는다(재발급이 먼저)
async function apiRequest(url, method, body, options = {}) {
    const sentToken = localStorage.getItem('accessToken');
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
        const errorText = await response.text();
        throw { status: response.status, message: errorText };
    }

    if (response.status === 204) {
        return null;
    }

    return await response.json();
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

// 진짜 만료 — Access 를 지우고 로그인 화면으로(쓰던 중이니 다시 로그인해서 이어 가게). 메인은 silentExpire
function expireSession(options) {
    localStorage.removeItem('accessToken');
    if (options.silentExpire) {
        throw { status: 401, expired: true };
    }
    window.location.href = '/views/user/login.html';
}

// 진행 중인 재발급. 한 화면의 동시 요청(거래 화면 Promise.all 등)이 함께 401 을 받아도 재발급은 한 번 — 나머지는 이것을 같이 기다린다
// (single-flight, ADR-055 선택 10). 변수는 탭마다 따로라 탭 두 개의 동시 재발급은 못 막는다 → 서버 유예 30초가 맡는다
let refreshing = null;

// 새 Access 를 돌려준다. 카드가 무효(401)면 null. 서버 오류·네트워크 실패는 던진다 — 일시 장애로 로그아웃시키지 않는다
async function refreshAccessToken(sentToken) {
    // 보낸 토큰과 지금 토큰이 다르다 = 다른 요청이 이미 재발급을 끝냈다 → 또 부르지 않고 새 토큰으로 재시도
    // (재발급이 끝난 뒤에 도착한 늦은 401 — refreshing 은 이미 비었다)
    const current = localStorage.getItem('accessToken');
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

// apiRequest 를 거치지 않는다 — Access 가 필요 없고(쿠키가 증명), 여기서 401 이 나면 또 재발급을 부르는 순환이 된다
async function requestRefresh() {
    const response = await fetch('/api/auth/refresh', { method: 'POST' });
    if (response.status === 401) {
        return null;
    }
    if (!response.ok) {
        throw { status: response.status, message: await response.text() };
    }
    const data = await response.json();
    localStorage.setItem('accessToken', data.accessToken);
    return data.accessToken;
}

// 로그아웃 — 서버가 장부(Refresh 일행) 폐기 + 지우는 쿠키(HttpOnly 라 JS 는 못 지운다). 성공해야 Access 도 지운다
// 실패면 아무것도 지우지 않고 던진다 — 장부가 살아 있는데 로그아웃된 것처럼 보이면 다시 누를 이유가 사라진다
// apiRequest 를 거치지 않는 이유는 requestRefresh 와 같다 (401 → 재발급·로그인 이동이 끼면 안 된다)
async function logoutRequest() {
    const response = await fetch('/api/auth/logout', { method: 'POST' });
    if (!response.ok) {
        throw { status: response.status, message: await response.text() };
    }
    localStorage.removeItem('accessToken');
}
