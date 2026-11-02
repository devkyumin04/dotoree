const form = document.getElementById('loginForm');
const submitBtn = document.getElementById('submitBtn');
const resultMessage = document.getElementById('resultMessage');

form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const email = document.getElementById('email').value;
    const password = document.getElementById('password').value;

    // 남아 있던 옛 세션을 지우고 시작 (apiClient.js)
    clearSession();

    await withButtonLock(submitBtn, async () => {
        try {
			const result = await apiRequest('/api/users/login', 'POST', { email, password }, { anonymous: true });
			startSession(result.accessToken);
			window.location.href = '/views/main.html';
        } catch (error) {
            resultMessage.textContent = error.message;
            // 이메일은 남기고 비밀번호만 비운다 — 틀린 값 위에 이어 치지 않게, 화면에 비번이 남지 않게
            const passwordInput = document.getElementById('password');
            passwordInput.value = '';
            passwordInput.focus();
        }
    });
});

// 데모로 둘러보기 — 비밀번호 없이 읽기 전용 데모 계정으로 들어간다(서버: POST /api/auth/demo-login). 쓰기는 서버가 막는다
const demoBtn = document.getElementById('demoBtn');
demoBtn.addEventListener('click', async () => {
    clearSession();
    await withButtonLock(demoBtn, async () => {
        try {
            const result = await apiRequest('/api/auth/demo-login', 'POST', null, { anonymous: true });
            startSession(result.accessToken);
            window.location.href = '/views/personal/personal-main.html';
        } catch (error) {
            resultMessage.textContent = error.message;
        }
    });
});
