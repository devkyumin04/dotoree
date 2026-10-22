const form = document.getElementById('signupForm');
const submitBtn = document.getElementById('submitBtn');
const resultMessage = document.getElementById('resultMessage');
const emailInput = document.getElementById('email');
const codeInput = document.getElementById('code');

// ① 코드 받기 → ② 코드 + 비번 + 닉네임으로 가입 — 가입하면 바로 로그인 (ADR-068)
setupEmailCodeButton({
    button: document.getElementById('codeBtn'),
    emailInput,
    message: document.getElementById('codeMessage'),
    purpose: 'S',
    codeInput,
});

form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const email = emailInput.value.trim();
    const code = codeInput.value.trim();
    const password = document.getElementById('password').value;
    const nickname = document.getElementById('nickname').value;

    // 남아 있던 옛 토큰을 지우고 시작 — 로그인 화면과 같은 이유 (apiClient.js)
    localStorage.removeItem('accessToken');

    await withButtonLock(submitBtn, async () => {
        try {
            const result = await apiRequest('/api/users/signup', 'POST', { email, code, password, nickname });
            localStorage.setItem('accessToken', result.accessToken);
            window.location.href = '/views/main.html';
        } catch (error) {
            // 코드가 틀려도 비번 · 닉네임은 그대로 둔다 — 코드 칸만 고치면 된다
            resultMessage.textContent = error.message;
        }
    });
});
