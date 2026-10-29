requireLogin();

// 로그아웃 — 서버 호출·Access 삭제는 apiClient 의 logoutRequest. 실패하면 거기서 아무것도 지우지 않고 던진다(메인 화면과 같은 방식)
document.getElementById('logoutBtn').addEventListener('click', async () => {
    try {
        await logoutRequest();
    } catch (error) {
        console.error('로그아웃 실패', error);
        alert('로그아웃하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        return;
    }
    window.location.href = '/views/main.html';
});
