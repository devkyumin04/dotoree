requireLogin();

const gate = document.getElementById('gate');
const gateForm = document.getElementById('gateForm');
const gateBtn = document.getElementById('gateBtn');
const gateMessage = document.getElementById('gateMessage');
const mypageBody = document.getElementById('mypageBody');

const emailEl = document.getElementById('email');
const nicknameInput = document.getElementById('nickname');
const nicknameForm = document.getElementById('nicknameForm');
const nicknameBtn = document.getElementById('nicknameBtn');
const nicknameMessage = document.getElementById('nicknameMessage');
const passwordForm = document.getElementById('passwordForm');
const passwordBtn = document.getElementById('passwordBtn');
const passwordMessage = document.getElementById('passwordMessage');

// 관문에서 확인한 비밀번호 — 비밀번호 변경의 "현재 비밀번호"로 다시 쓴다(두 번 묻지 않게).
// 이 페이지 메모리에만 있다. 저장하지 않고, 새로고침하면 사라져 관문부터 다시
let confirmedPassword = null;

// ── 1단계: 비밀번호 확인
gateForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const password = document.getElementById('gatePassword').value;

    await withButtonLock(gateBtn, async () => {
        try {
            await apiRequest('/api/users/me/verify-password', 'POST', { password });
            confirmedPassword = password;
            gateForm.reset();
            gate.hidden = true;
            mypageBody.hidden = false;
            await loadMyInfo();
        } catch (error) {
            gateMessage.textContent = error.message;
        }
    });
});

// ── 2단계: 내 정보
async function loadMyInfo() {
    try {
        const data = await apiRequest('/api/users/me', 'GET');
        if (!data) return;
        emailEl.textContent = data.email;
        nicknameInput.value = data.nickname;
    } catch (error) {
        nicknameMessage.textContent = '내 정보를 불러오지 못했습니다.';
    }
}

nicknameForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const nickname = nicknameInput.value;

    await withButtonLock(nicknameBtn, async () => {
        try {
            const data = await apiRequest('/api/users/me/nickname', 'PUT', { nickname });
            if (!data) return;
            nicknameInput.value = data.nickname;   // 서버가 저장한 값 그대로
            nicknameMessage.textContent = '닉네임을 변경했습니다.';
        } catch (error) {
            nicknameMessage.textContent = error.message;
        }
    });
});

passwordForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const newPassword = document.getElementById('newPassword').value;
    const newPasswordCheck = document.getElementById('newPasswordCheck').value;

    // 두 칸이 같은지는 이 화면에서만 의미 있는 확인 — 서버로 보내지 않는다 (ADR-040 뷰의 몫)
    if (newPassword !== newPasswordCheck) {
        passwordMessage.textContent = '새 비밀번호가 서로 다릅니다.';
        return;
    }
    // 1차 방어 — 서버도 같은 검사를 한다(진실은 서버)
    if (newPassword === confirmedPassword) {
        passwordMessage.textContent = '현재 비밀번호와 다른 비밀번호를 입력해주세요.';
        return;
    }

    await withButtonLock(passwordBtn, async () => {
        try {
            await apiRequest('/api/users/me/password', 'PUT', { currentPassword: confirmedPassword, newPassword });
            // 서버가 이 기기 카드까지 전부 폐기했다(전 세션 폐기) — 남은 Access 도 지우고 새 비번으로 다시 로그인
            localStorage.removeItem('accessToken');
            passwordForm.reset();
            passwordMessage.textContent = '비밀번호를 변경했습니다. 다시 로그인해 주세요.';
            setTimeout(() => {
                window.location.href = '/views/user/login.html';
            }, 2000);
        } catch (error) {
            passwordMessage.textContent = error.message;
        }
    });
});
