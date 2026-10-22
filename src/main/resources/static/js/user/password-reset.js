const form = document.getElementById('resetForm');
const submitBtn = document.getElementById('submitBtn');
const resultMessage = document.getElementById('resultMessage');
const emailInput = document.getElementById('email');
const codeInput = document.getElementById('code');

// ① 코드 받기 → ② 코드 + 새 비번 → 로그인 화면으로 (ADR-068)
// 없는 주소여도 "보냈습니다"로 같게 보인다 — 서버가 가입 여부를 알려 주지 않는다
setupEmailCodeButton({
    button: document.getElementById('codeBtn'),
    emailInput,
    message: document.getElementById('codeMessage'),
    purpose: 'P',
    codeInput,
});

form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const email = emailInput.value.trim();
    const code = codeInput.value.trim();
    const newPassword = document.getElementById('newPassword').value;

    await withButtonLock(submitBtn, async () => {
        try {
            await apiRequest('/api/auth/password-reset', 'POST', { email, code, newPassword });
            // 이 브라우저의 옛 토큰도 서버에서 이미 끝났다(전 세션 폐기) — 남은 것을 지우고 로그인으로
            localStorage.removeItem('accessToken');
            resultMessage.textContent = '비밀번호를 바꿨습니다. 새 비밀번호로 로그인해 주세요.';
            setTimeout(() => {
                window.location.href = '/views/user/login.html';
            }, 1500);
        } catch (error) {
            resultMessage.textContent = error.message;
        }
    });
});
