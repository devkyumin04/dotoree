package com.kyumin.dotoree.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kyumin.dotoree.config.DemoAccount;
import com.kyumin.dotoree.domain.AuthVerification;
import com.kyumin.dotoree.dto.EmailCodeRequestDto;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mail.EmailSender;
import com.kyumin.dotoree.mapper.AuthVerificationMapper;
import com.kyumin.dotoree.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

// 이메일 인증 코드 — 보내기 · 확인 (ADR-068)
// 흐름: ① 코드 받기(sendCode) → ② 가입 · 재설정 요청에 코드를 같이 실어 보낸다 → 그 트랜잭션 안에서 verifyAndUse
// 서버가 "확인됨"을 따로 기억하지 않는다 — 코드 확인과 가입(재설정)이 한 요청 · 한 트랜잭션이라 둘 중 하나만 되는 일이 없다
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

	// 규칙 — 값이 환경마다 다르지 않고 비밀도 아니라 yml 이 아니라 상수 (ADR-042 기준)
	// 60초 간격 · 24시간에 5번(가입 + 재설정 합쳐서)은 SES 프로덕션 신청 때 한 약속 — 남의 주소로 메일 폭탄을 못 만들게
	private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
	private static final Duration DAILY_WINDOW = Duration.ofDays(1);
	private static final int DAILY_LIMIT = 5;
	private static final Duration CODE_VALID = Duration.ofMinutes(10);
	private static final int MAX_FAIL_COUNT = 5;

	private final SecureRandom secureRandom = new SecureRandom();

	private final AuthVerificationMapper authVerificationMapper;
	private final EmailCodeAttemptService emailCodeAttemptService;
	private final UserMapper userMapper;
	private final EmailSender emailSender;
	private final DemoAccount demoAccount;
	private final Clock clock;

	// 코드 받기 — 거절(가입된 주소 · 60초 · 24시간 5번) → 줄 남기기(해시만) → 메일. 한 트랜잭션
	// 메일이 실패하면(EMAIL_SEND_FAILED) 줄도 롤백된다 — 못 받은 메일을 횟수로 세지 않는다
	// 감수 — 같은 주소로 동시에 두 요청이 오면 둘 다 60초 검사를 지날 수 있다(잠글 줄이 아직 없다). 한 사람이 버튼을 연타해도 버튼 잠금이 먼저 막고, 넘어와도 몇 통이다
	@Transactional
	public void sendCode(EmailCodeRequestDto requestDto) {
		String email = requestDto.getEmail();
		String purpose = requestDto.getPurpose();

		// 데모 계정은 받는 사람이 없는 주소 — 재설정 코드를 보내지 않는다(없는 주소와 같이 성공처럼 204)
		boolean registered = userMapper.findByEmail(email) != null && !demoAccount.isDemo(email);
		if (AuthVerification.PURPOSE_SIGNUP.equals(purpose) && registered) {
			// 가입 409 는 원래 있던 것과 같다 — 코드를 받기 전에 알려 메일을 헛되이 보내지 않는다
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
		if (AuthVerification.PURPOSE_PASSWORD_RESET.equals(purpose) && !registered) {
			// 없는 주소의 재설정 — 보내지 않고 성공처럼 204. 화면 문구가 가입된 주소와 같아 "가입했나"를 여기서 더 알려 주지 않는다
			// (60초 · 5번 거절은 줄이 있는 주소에만 나와 아주 티가 안 나지는 않는다 — 가입 409 로 이미 알 수 있는 정보라 감수)
			return;
		}

		LocalDateTime now = LocalDateTime.now(clock);

		LocalDateTime lastSentAt = authVerificationMapper.findLastSentAt(email);
		if (lastSentAt != null && lastSentAt.plus(RESEND_INTERVAL).isAfter(now)) {
			long waitMillis = Duration.between(now, lastSentAt.plus(RESEND_INTERVAL)).toMillis();
			long waitSeconds = (waitMillis + 999) / 1000;
			throw new BusinessException(ErrorCode.EMAIL_CODE_TOO_SOON, waitSeconds + "초 뒤에 다시 받을 수 있습니다.");
		}
		if (authVerificationMapper.countSentSince(email, now.minus(DAILY_WINDOW)) >= DAILY_LIMIT) {
			throw new BusinessException(ErrorCode.EMAIL_CODE_DAILY_LIMIT);
		}

		String code = newCode();

		AuthVerification verification = new AuthVerification();
		verification.setEmail(email);
		verification.setPurpose(purpose);
		verification.setCodeHash(hash(code));
		verification.setCreatedAt(now);
		authVerificationMapper.insertVerification(verification);

		emailSender.send(email, "[도토리] 인증 코드 " + code, mailBody(purpose, code));
	}

	// 코드 확인 + 사용 처리 — 가입 · 재설정이 자기 트랜잭션 안에서 부른다(합류). 통과하면 아무것도 돌려주지 않고, 거절은 예외(400)
	// void + 예외 — boolean 이면 부르는 쪽마다 if 가 필요하고, 하나라도 빠뜨리면 틀린 코드가 조용히 통과한다 (checkPassword 와 같은 이유)
	// 순서: 마지막 코드 찾기 → 쓸 수 있나(안 씀 · 10분 · 5번) → 맞나(틀리면 +1 을 따로 커밋) → 사용 처리
	@Transactional
	public void verifyAndUse(String email, String purpose, String code) {
		LocalDateTime now = LocalDateTime.now(clock);

		// 새로 받으면 옛 코드는 여기 안 잡힌다 — 마지막 한 통만 유효
		AuthVerification verification = authVerificationMapper.findLatest(email, purpose);

		if (verification == null
				|| verification.getUsedAt() != null
				|| !verification.getCreatedAt().plus(CODE_VALID).isAfter(now)
				|| verification.getFailCount() >= MAX_FAIL_COUNT) {
			throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
		}

		// 해시끼리 비교 — 원문은 저장하지 않았다. isEqual 은 글자가 어디서 달라지든 같은 시간이 걸린다
		byte[] expected = verification.getCodeHash().getBytes(StandardCharsets.UTF_8);
		byte[] actual = hash(code).getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expected, actual)) {
			emailCodeAttemptService.recordFailure(verification.getVerificationNum());
			int remaining = MAX_FAIL_COUNT - (verification.getFailCount() + 1);
			if (remaining <= 0) {
				throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED,
						MAX_FAIL_COUNT + "번 틀려 이 코드는 더 쓸 수 없습니다. 새 코드를 받아 주세요.");
			}
			throw new BusinessException(ErrorCode.EMAIL_CODE_MISMATCH, "인증 코드가 맞지 않습니다. (남은 기회 " + remaining + "번)");
		}

		// 0행 = 같은 코드로 동시에 온 다른 요청이 먼저 썼다 — 한 코드로 계정이 둘 생기거나 비번이 두 번 바뀌지 않게
		if (authVerificationMapper.markUsed(verification.getVerificationNum(), now) == 0) {
			throw new BusinessException(ErrorCode.EMAIL_CODE_EXPIRED);
		}
	}

	// 정리 — 하루 지난 줄. 24시간 5번을 세는 데 하루치만 있으면 된다 (AuthCleanupScheduler)
	@Transactional
	public int deleteOldCodes() {
		return authVerificationMapper.deleteSentBefore(LocalDateTime.now(clock).minus(DAILY_WINDOW));
	}

	// 000000 ~ 999999 — 앞자리 0 도 6자리로
	private String newCode() {
		return String.format("%06d", secureRandom.nextInt(1_000_000));
	}

	private static String mailBody(String purpose, String code) {
		String what = AuthVerification.PURPOSE_SIGNUP.equals(purpose) ? "회원가입" : "비밀번호 재설정";
		return "도토리 " + what + " 인증 코드입니다.\n\n"
				+ "    " + code + "\n\n"
				+ "10분 안에 입력해 주세요.\n"
				+ "직접 요청하지 않았다면 이 메일은 무시하셔도 됩니다.\n\n"
				+ "https://dotoree.app\n";
	}

	// SHA-256 hex 64글자 (refresh_tokens.token_hash 와 같은 방식 — 두 번째 복제. 세 번째가 생기면 한 곳으로, ADR-041)
	private static String hash(String code) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(code.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);
		}
	}
}
