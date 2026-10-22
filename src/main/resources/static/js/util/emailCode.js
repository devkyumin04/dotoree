// 인증 코드 받기 버튼 — 가입 · 비밀번호 재설정 화면이 같이 쓴다 (ADR-068)
// 보내면 60초 동안 버튼을 잠그고 남은 초를 보여 준다(서버도 60초 간격을 지킨다 — 화면은 안내용)
// purpose: 'S' 가입 · 'P' 재설정
function setupEmailCodeButton({ button, emailInput, message, purpose, codeInput }) {
    const label = button.textContent;
    let timer = null;

    function lock(seconds) {
        let left = seconds;
        button.disabled = true;
        button.textContent = `다시 받기 (${left})`;
        clearInterval(timer);
        timer = setInterval(() => {
            left -= 1;
            if (left <= 0) {
                clearInterval(timer);
                button.disabled = false;
                button.textContent = '다시 받기';
                return;
            }
            button.textContent = `다시 받기 (${left})`;
        }, 1000);
    }

    function show(text) {
        message.textContent = text;
        message.hidden = false;
    }

    button.addEventListener('click', async () => {
        const email = emailInput.value.trim();
        if (!emailInput.checkValidity() || !email) {
            show('이메일을 먼저 바르게 입력해 주세요.');
            emailInput.focus();
            return;
        }

        button.disabled = true;
        try {
            await apiRequest('/api/auth/email-codes', 'POST', { email, purpose });
            show('메일을 보냈습니다. 10분 안에 코드를 입력해 주세요. 안 보이면 스팸함도 확인해 주세요.');
            lock(60);
            codeInput.focus();
        } catch (error) {
            show(error.message);
            // 60초 안 거절(429)이면 서버가 알려 준 남은 초만큼 잠근다. 그 밖의 오류는 바로 다시 누를 수 있게
            const seconds = Number((error.message || '').match(/(\d+)초 뒤/)?.[1]);
            if (error.status === 429 && seconds > 0) {
                lock(seconds);
            } else {
                button.disabled = false;
                button.textContent = label;
            }
        }
    });
}
