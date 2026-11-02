// 로그인이 필요한 화면의 첫 줄에서 부른다. 토큰이 없으면 이유를 알리고 로그인으로 보낸다
// 메인이 아니라 로그인인 이유 — 이 주소로 온 사람은 그 기능을 쓰려는 사람이라 한 단계라도 짧게 (2026-09-23 판단)
// (메인은 공개 화면이라 이걸 부르지 않는다 — 메인의 안내는 main.js)
// Access 는 메모리라 화면이 뜬 직후엔 비어 있다 → 여기선 "로그인한 적 있나"(hasSession)만 동기로 보고,
// 실제 토큰은 첫 API 호출이 쿠키로 재발급해 채운다 (apiClient.js, ADR-071). 쿠키가 끝났으면 그때 로그인으로 보낸다
function requireLogin() {
    if (!hasSession()) {
        alert('로그인이 필요합니다.');
        window.location.href = '/views/user/login.html';
    }
}
