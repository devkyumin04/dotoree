package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kyumin.dotoree.scheduler.AccountPurgeScheduler;
import com.kyumin.dotoree.support.IntegrationTestSupport;

// 회원 탈퇴 — 30일 유예 → 파기 (ADR-052). qa/test-withdraw.sh 1~4 번역 (통합 테스트)
// 파기는 스케줄러를 기다리지 않고 직접 부른다 — 셸 QA 에선 로컬 SKIP 이던 묶음이 여기선 어디서나 돈다
// 셸 3-a(유예 중 틀린 비번 로그인 → 401 · 'W' 그대로)는 로그인 잠금 묶음으로 옮겼다 — 틀린 로그인의 실패 기록은 새 트랜잭션이라 이 클래스(@Transactional)와 락을 기다린다
class WithdrawIntegrationTest extends IntegrationTestSupport {

	private static final String WITHDRAW = "/api/users/withdraw";
	private static final String EMAIL = "it-withdraw@test.com";
	private static final String OTHER_EMAIL = "it-withdraw-other@test.com";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AccountPurgeScheduler accountPurgeScheduler;

	private Integer userNum;
	private String token;

	@BeforeEach
	void setUp() throws Exception {
		userNum = signup(EMAIL);
		token = login(EMAIL);

		// 파기 확인용 가계부 — 대분류 + 소분류 + 거래 (지우는 순서가 FK 와 다르면 파기가 실패한다)
		Integer parent = createCategory(token, "QA탈퇴대분류", "E", null);
		Integer child = createCategory(token, "QA탈퇴소분류", "E", parent);
		createTransaction(token, child, 1000, "탈퇴거래");
	}

	// ── 1. 입구 ──────────────────────────────

	@Test
	void 토큰_없이_탈퇴하면_401() throws Exception {
		request(HttpMethod.POST, WITHDRAW, null, passwordBody(PASSWORD))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 비번_칸이_없으면_400() throws Exception {
		request(HttpMethod.POST, WITHDRAW, token, "{}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void 틀린_비번은_401이_아니라_400이고_계정은_그대로() throws Exception {
		// 401 이면 화면(apiClient)이 만료로 보고 로그아웃시킨다
		request(HttpMethod.POST, WITHDRAW, token, passwordBody("wrong-password-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("비밀번호가 일치하지 않습니다")));

		assertThat(userStatus(userNum)).isEqualTo("A");
	}

	// ── 2. 탈퇴 ──────────────────────────────

	@Test
	void 탈퇴하면_204_W와_유예_시작_시각이_남는다() throws Exception {
		request(HttpMethod.POST, WITHDRAW, token, passwordBody(PASSWORD))
				.andExpect(status().isNoContent());

		Map<String, Object> user = userRow(userNum);
		assertThat(user.get("user_status")).isEqualTo("W");
		assertThat(user.get("user_withdrawn_at")).isNotNull();
	}

	@Test
	void 남은_토큰으로_또_탈퇴하면_401() throws Exception {
		request(HttpMethod.POST, WITHDRAW, token, passwordBody(PASSWORD))
				.andExpect(status().isNoContent());

		// 이미 'W' 라 0행 → 없는 사용자
		request(HttpMethod.POST, WITHDRAW, token, passwordBody(PASSWORD))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 유예_중엔_같은_이메일로_재가입_409() throws Exception {
		request(HttpMethod.POST, WITHDRAW, token, passwordBody(PASSWORD))
				.andExpect(status().isNoContent());

		request(HttpMethod.POST, "/api/users/signup", null, signupBody(EMAIL))
				.andExpect(status().isConflict());
	}

	// ── 3. 유예 중 로그인 = 탈퇴 취소 ──────────────────

	@Test
	void 유예_중_맞는_비번으로_로그인하면_탈퇴_취소_가계부도_그대로() throws Exception {
		request(HttpMethod.POST, WITHDRAW, token, passwordBody(PASSWORD))
				.andExpect(status().isNoContent());

		String restoredToken = login(EMAIL);

		Map<String, Object> user = userRow(userNum);
		assertThat(user.get("user_status")).isEqualTo("A");
		assertThat(user.get("user_withdrawn_at")).isNull();
		assertThat(monthTransactions(restoredToken))
				.anyMatch(row -> "QA탈퇴소분류".equals(row.get("categoryName")));
	}

	// ── 4. 파기 ──────────────────────────────

	@Test
	void 유예_30일이_지나면_가계부까지_파기되고_이메일이_풀린다_유예_중인_다른_계정은_남는다() throws Exception {
		Integer otherUserNum = signup(OTHER_EMAIL);
		String otherToken = login(OTHER_EMAIL);
		request(HttpMethod.POST, WITHDRAW, token, passwordBody(PASSWORD)).andExpect(status().isNoContent());
		request(HttpMethod.POST, WITHDRAW, otherToken, passwordBody(PASSWORD)).andExpect(status().isNoContent());

		// 이 계정만 유예 시작을 31일 전으로 — 다른 계정은 방금 탈퇴한 그대로(대조군)
		jdbcTemplate.update("UPDATE users SET user_withdrawn_at = ? WHERE user_num = ?", LocalDateTime.now().minusDays(31), userNum);

		accountPurgeScheduler.purgeWithdrawnUsers();

		assertThat(count("SELECT COUNT(*) FROM users WHERE user_num = ?")).isZero();
		assertThat(count("SELECT COUNT(*) FROM personal_categories WHERE user_num = ?")).isZero();
		assertThat(count("SELECT COUNT(*) FROM personal_transactions WHERE user_num = ?")).isZero();
		assertThat(count("SELECT COUNT(*) FROM refresh_tokens WHERE user_num = ?")).isZero();
		assertThat(count("SELECT COUNT(*) FROM accounts WHERE user_num = ?")).isZero();
		assertThat(userStatus(otherUserNum)).isEqualTo("W");

		request(HttpMethod.POST, "/api/users/login", null, loginBody(EMAIL))
				.andExpect(status().isUnauthorized());
		request(HttpMethod.POST, "/api/users/signup", null, signupBody(EMAIL))
				.andExpect(status().isCreated());
	}

	// ── 부품 ─────────────────────────────────

	private Integer signup(String email) throws Exception {
		return read(request(HttpMethod.POST, "/api/users/signup", null, signupBody(email))
				.andExpect(status().isCreated()), "$.userNum");
	}

	private String login(String email) throws Exception {
		return read(request(HttpMethod.POST, "/api/users/login", null, loginBody(email))
				.andExpect(status().isOk()), "$.accessToken");
	}

	private static String signupBody(String email) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"dotoree\"}";
	}

	private static String loginBody(String email) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
	}

	private static String passwordBody(String password) {
		return "{\"password\":\"" + password + "\"}";
	}

	private Map<String, Object> userRow(Integer targetUserNum) {
		return jdbcTemplate.queryForMap("SELECT user_status, user_withdrawn_at FROM users WHERE user_num = ?", targetUserNum);
	}

	private String userStatus(Integer targetUserNum) {
		return (String) userRow(targetUserNum).get("user_status");
	}

	private int count(String sql) {
		return jdbcTemplate.queryForObject(sql, Integer.class, userNum);
	}
}
