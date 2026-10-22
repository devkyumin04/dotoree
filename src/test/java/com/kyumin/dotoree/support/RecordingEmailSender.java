package com.kyumin.dotoree.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Primary;

import com.kyumin.dotoree.mail.EmailSender;

// 테스트용 메일함 — 보내지 않고 주소별 마지막 메일만 기억한다. 테스트는 여기서 인증 코드를 꺼내 가입 · 재설정에 쓴다 (ADR-068)
// @Primary — MailConfig 가 만든 진짜 EmailSender 를 가린다. IntegrationTestSupport 가 @Import 한다
@Primary
public class RecordingEmailSender implements EmailSender {

	private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

	private final Map<String, String> lastSubjects = new ConcurrentHashMap<>();

	@Override
	public void send(String to, String subject, String body) {
		lastSubjects.put(to, subject);
	}

	// 이 주소로 마지막에 보낸 코드 — 제목 "[도토리] 인증 코드 482913" 에서. 보낸 적 없으면 null
	public String lastCode(String to) {
		String subject = lastSubjects.get(to);
		if (subject == null) {
			return null;
		}
		Matcher matcher = CODE.matcher(subject);
		return matcher.find() ? matcher.group(1) : null;
	}

	public boolean hasMailFor(String to) {
		return lastSubjects.containsKey(to);
	}

	public void clear(String to) {
		lastSubjects.remove(to);
	}
}
