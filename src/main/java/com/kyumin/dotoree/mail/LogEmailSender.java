package com.kyumin.dotoree.mail;

import lombok.extern.slf4j.Slf4j;

// 로컬 — 메일을 보내지 않고 콘솔 로그에 찍는다. 인증 코드는 STS 콘솔에서 본다 (application-local.yml 의 dotoree.mail.sender: log)
// 운영에서 켜면 코드가 서버 로그에 남는다 — 운영은 ses 그대로 둔다
@Slf4j
public class LogEmailSender implements EmailSender {

	@Override
	public void send(String to, String subject, String body) {
		log.info("[메일 — 로컬이라 보내지 않음] to={} subject={}\n{}", to, subject, body);
	}
}
