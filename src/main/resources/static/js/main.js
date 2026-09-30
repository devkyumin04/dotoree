// 메인은 공개 화면 — 로그인 안 한 사람(처음 온 사람·SES 심사자)도 소개를 본다.
// 기본 HTML 은 비로그인 모양이라 JS 가 안 돌아도 소개가 보인다. 토큰이 있을 때만 환영 문구·마이페이지·로그아웃
const token = localStorage.getItem('accessToken');
const welcomeMessage = document.getElementById('welcomeMessage');
const logoutBtn = document.getElementById('logoutBtn');
const loginLink = document.getElementById('loginLink');
const signupLink = document.getElementById('signupLink');
const mypageLink = document.getElementById('mypageLink');
const loginNotice = document.getElementById('loginNotice');

async function loadMyInfo() {
    try {
        const data = await apiRequest('/api/users/me', 'GET', null, { silentExpire: true });
        if (!data) return;
        welcomeMessage.textContent = data.nickname + '님, 환영합니다';
    } catch (error) {
        if (error.expired) {
            // 만료 토큰 — 메인은 공개 화면이라 로그인으로 튕기지 않고 비로그인 모습으로 (apiClient 가 토큰은 이미 지웠다)
            showGuest();
            return;
        }
        console.error('내 정보 조회 실패', error);
    }
}

// 비로그인 모습 — 처음 온 사람, 그리고 토큰이 만료된 사람
function showGuest() {
    loginLink.hidden = false;
    signupLink.hidden = false;
    mypageLink.hidden = true;
    logoutBtn.hidden = true;

    // 비로그인 상태에서 가계부·카테고리·통계로 가는 링크를 누르면 — 말없이 튕기지 않고 알린 뒤 로그인으로
    document.querySelectorAll('a[href^="/views/personal/"]').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            loginNotice.hidden = false;
            loginNotice.scrollIntoView({ block: 'nearest' });
            setTimeout(() => {
                window.location.href = '/views/user/login.html';
            }, 1200);
        });
    });
}

if (token) {
    loginLink.hidden = true;
    signupLink.hidden = true;
    mypageLink.hidden = false;
    logoutBtn.hidden = false;
    loadMyInfo();
} else {
    showGuest();
}

// 예시 화면의 막대 너비 — CSP 가 HTML 의 style 속성을 막으므로 JS 로 준다
document.querySelectorAll('.bar[data-width]').forEach(bar => {
    bar.style.width = bar.dataset.width + '%';
});

// 로그아웃 — 서버 호출·Access 삭제는 apiClient 의 logoutRequest. 실패하면 거기서 아무것도 지우지 않고 던진다
logoutBtn.addEventListener('click', async () => {
    try {
        await logoutRequest();
    } catch (error) {
        console.error('로그아웃 실패', error);
        alert('로그아웃하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        return;
    }
    window.location.href = '/views/main.html';
});
