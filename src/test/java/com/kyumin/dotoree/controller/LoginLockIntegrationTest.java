package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.kyumin.dotoree.domain.User;
import com.kyumin.dotoree.mapper.UserMapper;
import com.kyumin.dotoree.support.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

// 로그인 잠금 — 계정 기준 5회 실패 → 30분 잠금 · 잠금 중 거절 · 만료 뒤 0 부터 · 성공 시 초기화 (ADR-057). qa/test-login-lock.sh 1~6 번역 (통합 테스트)
//
// 이 클래스만 테스트 트랜잭션을 끈다(NOT_SUPPORTED) — 틀린 로그인은 실패 기록을 새 트랜잭션(REQUIRES_NEW, ADR-062)으로 바로 커밋하는데,
// 테스트가 트랜잭션으로 가입한 줄을 커밋하지 않고 잡고 있으면 그 새 트랜잭션이 같은 줄을 기다리다 멈춘다(락 대기).
// 대신 데이터가 진짜로 커밋되니 테스트 앞뒤로 이 이메일 계정을 직접 지운다(파기와 같은 FK 순서)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LoginLockIntegrationTest extends IntegrationTestSupport {

	private static final String EMAIL = "it-lock@test.com";
	private static final String LOGIN = "/api/users/login";
	private static final String WRONG_PASSWORD = "wrong-" + PASSWORD;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private UserMapper userMapper;

	private Integer userNum;

	@BeforeEach
	void setUp() throws Exception {
		deleteAccount(EMAIL);    // 지난 실행이 중간에 죽어 남은 계정이 있으면
		userNum = read(request(HttpMethod.POST, "/api/users/signup", null, """
				{"email":"%s","password":"%s","nickname":"dotoree"}
				""".formatted(EMAIL, PASSWORD))
				.andExpect(status().isCreated()), "$.userNum");
	}

	@AfterEach
	void tearDown() {
		deleteAccount(EMAIL);
	}

	// ── 1·2. 잠기기 전 · 5회째 ──────────────────────

	@Test
	void 틀린_비번_4회는_401이고_아직_잠기지_않는다() throws Exception {
		failTimes(4);

		assertState(4, false);
	}

	@Test
	void 다섯_번째_실패에서_423으로_잠긴다() throws Exception {
		failTimes(4);

		login(WRONG_PASSWORD)
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.detail").value(containsString("잠겼습니다")))
				.andExpect(jsonPath("$.detail").value(containsString("30분")));
		assertState(5, true);
	}

	// ── 3. 잠금 중 ─────────────────────────────

	@Test
	void 잠금_중엔_맞는_비번도_423_틀린_비번은_세지도_연장하지도_않는다() throws Exception {
		failTimes(4);
		login(WRONG_PASSWORD).andExpect(status().isLocked());
		Object lockedUntil = userRow().get("locked_until");

		login(PASSWORD).andExpect(status().isLocked());          // 시도 자체를 막는다
		login(WRONG_PASSWORD).andExpect(status().isLocked());

		assertState(5, true);
		assertThat(userRow().get("locked_until")).isEqualTo(lockedUntil);
	}

	@Test
	void 잠겨도_이미_로그인된_기기의_재발급은_200() throws Exception {
		String refreshToken = refreshCookie(login(PASSWORD).andExpect(status().isOk()).andReturn());
		failTimes(4);
		login(WRONG_PASSWORD).andExpect(status().isLocked());

		// 잠금은 비번 로그인만 막는다
		mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)))
				.andExpect(status().isOk());
	}

	// ── 4. 30분 뒤 ─────────────────────────────

	@Test
	void 잠금이_풀린_뒤엔_0부터_다시_세고_성공하면_초기화() throws Exception {
		failTimes(4);
		login(WRONG_PASSWORD).andExpect(status().isLocked());
		// 30분을 기다리지 않고 저장된 잠금 시각을 31분 당긴다 (상대값이라 시계 차이와 무관)
		jdbcTemplate.update("UPDATE users SET locked_until = locked_until - INTERVAL 31 MINUTE WHERE user_num = ?", userNum);

		login(WRONG_PASSWORD).andExpect(status().isUnauthorized());   // 6회째가 아니라 1회째 — 이어 세면 바로 다시 잠긴다
		assertState(1, false);
		login(PASSWORD).andExpect(status().isOk());
		assertState(0, false);
	}

	// ── 5·6. 없는 이메일 · 성공 시 초기화 ─────────────────

	@Test
	void 없는_이메일은_다섯_번_틀려도_401이고_문구도_같다() throws Exception {
		for (int i = 0; i < 5; i++) {
			request(HttpMethod.POST, LOGIN, null, loginBody("nobody-lock@test.com", PASSWORD))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.detail").value(containsString("일치하지 않습니다")));
		}
	}

	@Test
	void 네_번_틀리고_성공하면_횟수가_0으로_돌아간다() throws Exception {
		failTimes(4);

		login(PASSWORD).andExpect(status().isOk());
		assertState(0, false);
		login(WRONG_PASSWORD).andExpect(status().isUnauthorized());   // 423 이면 초기화가 안 된 것
		assertState(1, false);
	}

	// ── 탈퇴 묶음에서 옮겨 온 것 (test-withdraw 3-a) ─────────────

	@Test
	void 유예_중_틀린_비번_로그인은_401이고_탈퇴_상태_그대로() throws Exception {
		String token = read(login(PASSWORD).andExpect(status().isOk()), "$.accessToken");
		request(HttpMethod.POST, "/api/users/withdraw", token, "{\"password\":\"" + PASSWORD + "\"}")
				.andExpect(status().isNoContent());

		login(WRONG_PASSWORD).andExpect(status().isUnauthorized());

		assertThat(userRow().get("user_status")).isEqualTo("W");   // 비번 확인이 복구보다 앞
	}

	// ── 부품 ─────────────────────────────────

	private ResultActions login(String password) throws Exception {
		return request(HttpMethod.POST, LOGIN, null, loginBody(EMAIL, password));
	}

	private void failTimes(int times) throws Exception {
		for (int i = 0; i < times; i++) {
			login(WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}
	}

	private static String loginBody(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	private Map<String, Object> userRow() {
		return jdbcTemplate.queryForMap("SELECT login_fail_count, locked_until, user_status FROM users WHERE user_num = ?", userNum);
	}

	private void assertState(int failCount, boolean locked) {
		Map<String, Object> row = userRow();
		assertThat(((Number) row.get("login_fail_count")).intValue()).as("실패 횟수").isEqualTo(failCount);
		assertThat(row.get("locked_until") != null).as("잠금 시각 있음").isEqualTo(locked);
	}

	private static String refreshCookie(MvcResult result) {
		String line = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
				.filter(header -> header.startsWith("refresh_token="))
				.findFirst()
				.orElseThrow();
		return line.substring("refresh_token=".length(), line.indexOf(';'));
	}

	// 커밋된 계정 지우기 — 파기(UserService.purgeUser)와 같은 FK 순서. 상태('A'·'W')와 상관없이
	private void deleteAccount(String email) {
		User user = userMapper.findByEmail(email);
		if (user == null) {
			return;
		}
		Integer target = user.getUserNum();
		userMapper.deleteTransactionsOf(target);
		userMapper.deleteChildCategoriesOf(target);
		userMapper.deleteCategoriesOf(target);
		userMapper.deleteRefreshTokensOf(target);
		// deleteUser 는 'W'(탈퇴 유예) 계정만 지운다(파기 안전장치) — 활성 계정도 지워야 해서 여기만 직접
		jdbcTemplate.update("DELETE FROM users WHERE user_num = ?", target);
	}
}
