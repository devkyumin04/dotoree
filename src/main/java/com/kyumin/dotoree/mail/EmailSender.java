package com.kyumin.dotoree.mail;

// 메일 한 통 보내기 — 운영은 SES(SesEmailSender), 로컬은 로그로(LogEmailSender). 고르는 건 dotoree.mail.sender (ADR-068)
// 보내지 못하면 BusinessException(EMAIL_SEND_FAILED) 을 던진다 — 부른 쪽 트랜잭션이 롤백돼 "보낸 것으로 세는 줄"도 안 남는다
public interface EmailSender {

	void send(String to, String subject, String body);
}
