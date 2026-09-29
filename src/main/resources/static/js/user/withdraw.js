requireLogin();

const form = document.getElementById('withdrawForm');
const submitBtn = document.getElementById('submitBtn');
const resultMessage = document.getElementById('resultMessage');

form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const password = document.getElementById('password').value;

    await withButtonLock(submitBtn, async () => {
        try {
            await apiRequest('/api/users/withdraw', 'POST', { password });
            // 서버는 204 + 이 사용자의 Refresh 카드 전부 폐기. 남은 Access 는 이 브라우저에서 지운다 (다른 기기의 Access 는 만료 15분까지 — ADR-052 감수)
            localStorage.removeItem('accessToken');
            resultMessage.textContent = '탈퇴 처리되었습니다. 30일 안에 로그인하면 취소할 수 있습니다.';
            setTimeout(() => {
                window.location.href = '/views/main.html';
            }, 2500);
        } catch (error) {
            resultMessage.textContent = error.message;
        }
    });
});
