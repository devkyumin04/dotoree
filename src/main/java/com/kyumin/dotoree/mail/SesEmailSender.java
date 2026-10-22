package com.kyumin.dotoree.mail;

import org.springframework.beans.factory.DisposableBean;

import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

// 운영 — Amazon SES(서울)로 보낸다. dotoree.mail.sender: ses (기본값). 빈은 MailConfig 가 만든다
// 키 파일 · 비밀값이 없다 — 서버의 인스턴스 역할(IMDSv2) 임시 자격으로 인증한다(SDK 기본 자격 체인). 역할에 ses:SendEmail 이 있어야 한다
// 보낸 사람은 인증된 도메인 dotoree.app 의 주소(dotoree.mail.from). 이름은 영문 — 한글 이름은 MIME 인코딩이 따로 필요하다
@Slf4j
public class SesEmailSender implements EmailSender, DisposableBean {

	private static final String UTF_8 = "UTF-8";

	private final SesV2Client client;
	private final String from;

	public SesEmailSender(String region, String from) {
		this.client = SesV2Client.builder().region(Region.of(region)).build();
		this.from = from;
	}

	@Override
	public void send(String to, String subject, String body) {
		SendEmailRequest request = SendEmailRequest.builder()
				.fromEmailAddress(from)
				.destination(Destination.builder().toAddresses(to).build())
				.content(EmailContent.builder()
						.simple(Message.builder()
								.subject(Content.builder().data(subject).charset(UTF_8).build())
								.body(Body.builder().text(Content.builder().data(body).charset(UTF_8).build()).build())
								.build())
						.build())
				.build();
		try {
			client.sendEmail(request);
		} catch (SdkException e) {
			// 주소는 로그에 안 남긴다 — 원인(권한 · 금지 목록 · 네트워크)만
			log.error("SES 발송 실패", e);
			throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
		}
	}

	@Override
	public void destroy() {
		client.close();
	}
}
