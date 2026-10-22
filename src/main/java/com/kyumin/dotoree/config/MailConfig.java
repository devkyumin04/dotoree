package com.kyumin.dotoree.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.kyumin.dotoree.mail.EmailSender;
import com.kyumin.dotoree.mail.LogEmailSender;
import com.kyumin.dotoree.mail.SesEmailSender;

// 메일 보내는 쪽 고르기 (ADR-068) — dotoree.mail.sender
//  · ses (기본값) — 운영. 값을 빠뜨려도 운영이 "로그로만 찍는" 쪽으로 새지 않게 기본을 ses 로 둔다
//  · log — 로컬(application-local.yml). 코드를 콘솔에서 본다
// 그 밖의 값은 기동 때 바로 실패 — 오타가 조용히 다른 쪽으로 가지 않게
// 테스트는 기록만 하는 가짜(RecordingEmailSender, @Primary)가 이 빈을 가린다
@Configuration
public class MailConfig {

	@Bean
	public EmailSender emailSender(@Value("${dotoree.mail.sender:ses}") String sender,
								   @Value("${dotoree.mail.region}") String region,
								   @Value("${dotoree.mail.from}") String from) {
		return switch (sender) {
			case "ses" -> new SesEmailSender(region, from);
			case "log" -> new LogEmailSender();
			default -> throw new IllegalStateException("dotoree.mail.sender 는 ses · log 중 하나여야 합니다: " + sender);
		};
	}
}
