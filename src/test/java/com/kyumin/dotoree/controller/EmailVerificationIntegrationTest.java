package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kyumin.dotoree.scheduler.AuthCleanupScheduler;
import com.kyumin.dotoree.support.IntegrationTestSupport;

// 이메일 인증 코드 — 받기(60초 · 24시간 5번) · 가입 · 틀림(5번) · 10분 · 비밀번호 재설정 · 정리 (ADR-068) (통합 테스트)
//
// 테스트 트랜잭션을 끈다(NOT_SUPPORTED) — 틀린 코드의 +1 은 새 트랜잭션(REQUIRES_NEW)으로 바로 커밋하는데,
// 테스트가 트랜잭션으로 넣은 코드 줄을 커밋하지 않고 잡고 있으면 그 새 트랜잭션이 같은 줄을 기다리다 멈춘다(LoginLockIntegrationTest 와 같은 이유)
// 대신 데이터가 진짜로 커밋되니 테스트 앞뒤로 이 주소의 계정 · 코드 줄을 지운다
// 시간은 기다리지 않고 created_at 을 당긴다(상대값이라 시계 차이와 무관)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailVerificationIntegrationTest extends IntegrationTestSupport {

	private static final String EMAIL = "it-verify@test.com";
	private static final String OTHER_EMAIL = "it-verify-other@test.com";
	private static final String NEW_PASSWORD = "Qa-reset-pw-1!";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AuthCleanupScheduler authCleanupScheduler;

	@BeforeEach
	void setUp() {
		deleteCommittedAccount(EMAIL);    // 지난 실행이 중간에 죽어 남은 것이 있으면
		deleteCommittedAccount(OTHER_EMAIL);
	}

	@AfterEach
	void tearDown() {
		deleteCommittedAccount(EMAIL);
		deleteCommittedAccount(OTHER_EMAIL);
	}

	// ── 1. 코드 받기 ──────────────────────────────

	@Test
	void 코드를_받으면_204_메일이_가고_DB엔_원문이_아니라_해시만_남는다() throws Exception {
		String code = emailCode(EMAIL, "S");

		assertThat(code).matches("\\d{6}");
		Map<String, Object> row = latestRow(EMAIL);
		assertThat(row.get("purpose")).isEqualTo("S");
		assertThat((String) row.get("code_hash")).hasSize(64).isNotEqualTo(code);
		assertThat(((Number) row.get("fail_count")).intValue()).isZero();
		assertThat(row.get("used_at")).isNull();
	}

	@Test
	void 육십초_안에_다시_받으면_429_남은_초를_알려준다() throws Exception {
		emailCode(EMAIL, "S");

		sendCode(EMAIL, "S")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.detail").value(containsString("초 뒤")));
		assertThat(codeRows(EMAIL)).isEqualTo(1);
	}

	@Test
	void 육십초가_지나면_다시_받고_옛_코드는_무효가_된다() throws Exception {
		String oldCode = emailCode(EMAIL, "S");
		ageCodes(EMAIL, 61);
		String newCode = emailCode(EMAIL, "S");
		assumeFalse(oldCode.equals(newCode));    // 백만분의 일 — 같으면 이 장면을 만들 수 없다

		signupWith(EMAIL, oldCode).andExpect(status().isBadRequest());
		signupWith(EMAIL, newCode).andExpect(status().isCreated());
	}

	@Test
	void 이십사시간에_다섯_번을_넘으면_429_하루가_지나면_다시_받는다() throws Exception {
		for (int i = 0; i < 5; i++) {
			emailCode(EMAIL, "S");
			ageCodes(EMAIL, 61);
		}

		sendCode(EMAIL, "S")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.detail").value(containsString("5번")));

		ageCodes(EMAIL, 24 * 60 * 60);
		sendCode(EMAIL, "S").andExpect(status().isNoContent());
	}

	@Test
	void 가입과_재설정은_횟수를_같이_센다() throws Exception {
		signup(EMAIL);    // 가입 코드 1번
		for (int i = 0; i < 4; i++) {
			ageCodes(EMAIL, 61);
			emailCode(EMAIL, "P");
		}
		ageCodes(EMAIL, 61);

		sendCode(EMAIL, "P").andExpect(status().isTooManyRequests());
	}

	@Test
	void 가입된_주소로_가입_코드를_받으면_409_메일은_안_간다() throws Exception {
		signup(EMAIL);
		ageCodes(EMAIL, 61);
		mailbox.clear(EMAIL);

		sendCode(EMAIL, "S").andExpect(status().isConflict());
		assertThat(mailbox.hasMailFor(EMAIL)).isFalse();
	}

	@Test
	void 없는_주소로_재설정_코드를_받아도_204_메일도_줄도_없다() throws Exception {
		mailbox.clear(EMAIL);

		sendCode(EMAIL, "P").andExpect(status().isNoContent());

		assertThat(mailbox.hasMailFor(EMAIL)).isFalse();
		assertThat(codeRows(EMAIL)).isZero();
	}

	// ── 2. 가입 — 코드 확인 ─────────────────────────

	@Test
	void 틀린_코드는_400_남은_기회를_알려주고_틀린_횟수는_가입이_취소돼도_남는다() throws Exception {
		String code = emailCode(EMAIL, "S");

		signupWith(EMAIL, wrong(code))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("남은 기회 4번")));

		assertThat(((Number) latestRow(EMAIL).get("fail_count")).intValue()).isEqualTo(1);
		assertThat(userRows(EMAIL)).isZero();
		signupWith(EMAIL, code).andExpect(status().isCreated());    // 맞는 코드는 그대로 통과
	}

	@Test
	void 다섯_번_틀리면_맞는_코드도_거절한다() throws Exception {
		String code = emailCode(EMAIL, "S");
		for (int i = 0; i < 4; i++) {
			signupWith(EMAIL, wrong(code)).andExpect(status().isBadRequest());
		}

		signupWith(EMAIL, wrong(code))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("5번 틀려")));
		signupWith(EMAIL, code)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("새 코드")));
		assertThat(((Number) latestRow(EMAIL).get("fail_count")).intValue()).isEqualTo(5);
		assertThat(userRows(EMAIL)).isZero();
	}

	@Test
	void 십분이_지난_코드는_거절한다() throws Exception {
		String code = emailCode(EMAIL, "S");
		ageCodes(EMAIL, 10 * 60 + 1);

		signupWith(EMAIL, code)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("만료")));
	}

	@Test
	void 코드를_받은_적_없는_주소로_가입하면_400() throws Exception {
		signupWith(EMAIL, "123456").andExpect(status().isBadRequest());
	}

	@Test
	void 가입하면_코드는_쓴_것이_된다() throws Exception {
		signup(EMAIL);

		assertThat(latestRow(EMAIL).get("used_at")).isNotNull();
	}

	// ── 3. 비밀번호 재설정 ──────────────────────────

	@Test
	void 재설정하면_새_비번으로_로그인되고_잠금이_풀리고_옛_세션이_끝난다() throws Exception {
		Integer userNum = signup(EMAIL);    // 가입하면 바로 로그인 — 살아 있는 카드 한 장
		jdbcTemplate.update("UPDATE users SET login_fail_count = 5, locked_until = NOW() + INTERVAL 30 MINUTE WHERE user_num = ?", userNum);
		ageCodes(EMAIL, 61);
		String code = emailCode(EMAIL, "P");

		resetWith(EMAIL, code, NEW_PASSWORD).andExpect(status().isNoContent());

		Map<String, Object> user = jdbcTemplate.queryForMap("SELECT login_fail_count, locked_until FROM users WHERE user_num = ?", userNum);
		assertThat(((Number) user.get("login_fail_count")).intValue()).isZero();
		assertThat(user.get("locked_until")).isNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens WHERE user_num = ? AND revoked_yn = 'N'", Integer.class, userNum)).isZero();
		login(EMAIL, PASSWORD).andExpect(status().isUnauthorized());
		login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 재설정_코드는_한_번만_쓴다() throws Exception {
		signup(EMAIL);
		ageCodes(EMAIL, 61);
		String code = emailCode(EMAIL, "P");
		resetWith(EMAIL, code, NEW_PASSWORD).andExpect(status().isNoContent());

		resetWith(EMAIL, code, "Qa-reset-pw-2!").andExpect(status().isBadRequest());
		login(EMAIL, NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 가입_코드로는_비밀번호를_못_바꾼다() throws Exception {
		signup(EMAIL);
		// 이 주소에 아직 안 쓴 가입(S) 코드 123456 이 있다고 치고 — 재설정은 목적(P)이 맞는 코드만 찾는다
		jdbcTemplate.update("INSERT INTO auth_verifications (email, purpose, code_hash, fail_count, created_at) "
				+ "VALUES (?, 'S', SHA2('123456', 256), 0, NOW())", EMAIL);

		resetWith(EMAIL, "123456", NEW_PASSWORD).andExpect(status().isBadRequest());
		login(EMAIL, PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 없는_주소의_재설정은_400() throws Exception {
		resetWith(EMAIL, "123456", NEW_PASSWORD).andExpect(status().isBadRequest());
	}

	// ── 4. 정리 ──────────────────────────────

	@Test
	void 정리하면_하루_지난_코드와_만료된_Refresh_만_지운다() throws Exception {
		Integer userNum = signup(EMAIL);
		ageCodes(EMAIL, 24 * 60 * 60 + 60);
		jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = NOW() - INTERVAL 1 DAY WHERE user_num = ?", userNum);
		emailCode(OTHER_EMAIL, "S");    // 방금 보낸 코드 — 남아야 한다

		authCleanupScheduler.cleanUp();

		assertThat(codeRows(EMAIL)).isZero();
		assertThat(codeRows(OTHER_EMAIL)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_num = ?", Integer.class, userNum)).isZero();
	}

	// ── 부품 ─────────────────────────────────

	private ResultActions sendCode(String email, String purpose) throws Exception {
		return request(HttpMethod.POST, "/api/auth/email-codes", null, emailCodeBody(email, purpose));
	}

	private ResultActions signupWith(String email, String code) throws Exception {
		return request(HttpMethod.POST, "/api/users/signup", null, signupBody(email, code));
	}

	private ResultActions resetWith(String email, String code, String newPassword) throws Exception {
		return request(HttpMethod.POST, "/api/auth/password-reset", null,
				"{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + newPassword + "\"}");
	}

	private ResultActions login(String email, String password) throws Exception {
		return request(HttpMethod.POST, "/api/users/login", null,
				"{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	// 맞는 코드와 반드시 다른 6자리
	private static String wrong(String code) {
		return "000000".equals(code) ? "111111" : "000000";
	}

	// 이 주소의 코드 줄을 전부 seconds 초 전으로 — 60초 · 10분 · 24시간을 기다리지 않는다
	private void ageCodes(String email, int seconds) {
		jdbcTemplate.update("UPDATE auth_verifications SET created_at = created_at - INTERVAL ? SECOND WHERE email = ?", seconds, email);
	}

	private Map<String, Object> latestRow(String email) {
		return jdbcTemplate.queryForMap(
				"SELECT purpose, code_hash, fail_count, used_at FROM auth_verifications WHERE email = ? ORDER BY verification_num DESC LIMIT 1", email);
	}

	private int codeRows(String email) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM auth_verifications WHERE email = ?", Integer.class, email);
	}

	private int userRows(String email) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE user_email = ?", Integer.class, email);
	}
}
