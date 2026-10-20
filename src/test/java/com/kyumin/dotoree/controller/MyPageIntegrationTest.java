package com.kyumin.dotoree.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 마이페이지 — 관문(비번 확인) · 닉네임 · 비밀번호 변경. qa/test-mypage.sh 0~2 번역 (통합 테스트)
class MyPageIntegrationTest extends IntegrationTestSupport {

	private static final String EMAIL = "it-mypage@test.com";
	private static final String VERIFY = "/api/users/me/verify-password";
	private static final String NICKNAME = "/api/users/me/nickname";
	private static final String PASSWORD_CHANGE = "/api/users/me/password";
	private static final String NEW_PASSWORD = "Qa-new-pass-1!";

	private String token;

	@BeforeEach
	void setUp() throws Exception {
		token = signupAndLogin(EMAIL);
	}

	// ── 0. 관문 — 마이페이지 들어가기 전 비밀번호 확인 ─────────

	@Test
	void 토큰_없이_비번_확인은_401() throws Exception {
		request(HttpMethod.POST, VERIFY, null, "{\"password\":\"x\"}")
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 비번_칸이_없으면_400() throws Exception {
		request(HttpMethod.POST, VERIFY, token, "{}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void 틀린_비번은_401이_아니라_400() throws Exception {
		// 401 이면 화면(apiClient)이 재발급 → 로그아웃으로 간다
		request(HttpMethod.POST, VERIFY, token, "{\"password\":\"wrong-password-1\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("비밀번호가 일치하지 않습니다")));
	}

	@Test
	void 맞는_비번은_204() throws Exception {
		request(HttpMethod.POST, VERIFY, token, passwordBody(PASSWORD))
				.andExpect(status().isNoContent());
	}

	// ── 1. 닉네임 ─────────────────────────────

	@Test
	void 토큰_없이_닉네임_변경은_401() throws Exception {
		request(HttpMethod.PUT, NICKNAME, null, "{\"nickname\":\"새이름\"}")
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 공백뿐이거나_21자인_닉네임은_400() throws Exception {
		request(HttpMethod.PUT, NICKNAME, token, "{\"nickname\":\"   \"}")
				.andExpect(status().isBadRequest());
		request(HttpMethod.PUT, NICKNAME, token, "{\"nickname\":\"123456789012345678901\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void 닉네임_20자는_200이고_내_정보에도_반영된다() throws Exception {
		request(HttpMethod.PUT, NICKNAME, token, "{\"nickname\":\"12345678901234567890\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").value("12345678901234567890"));
		request(HttpMethod.GET, "/api/users/me", token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nickname").value("12345678901234567890"));
	}

	@Test
	void 닉네임_본문에_email_userNum을_끼워_넣어도_무시한다() throws Exception {
		request(HttpMethod.PUT, NICKNAME, token, "{\"nickname\":\"새이름\",\"email\":\"hijack@test.com\",\"userNum\":1}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(EMAIL));
	}

	// ── 2. 비밀번호 ────────────────────────────

	@Test
	void 현재_비번이_틀리면_400() throws Exception {
		request(HttpMethod.PUT, PASSWORD_CHANGE, token, changeBody("wrong-password-1", NEW_PASSWORD))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("현재 비밀번호가 일치하지 않습니다")));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "short1!", "nospecial123" })
	void 새_비번이_규칙에_안_맞으면_400(String newPassword) throws Exception {
		request(HttpMethod.PUT, PASSWORD_CHANGE, token, changeBody(PASSWORD, newPassword))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 새_비번이_빠지면_400() throws Exception {
		request(HttpMethod.PUT, PASSWORD_CHANGE, token, "{\"currentPassword\":\"" + PASSWORD + "\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void 지금과_같은_비번으로는_못_바꾼다_400() throws Exception {
		request(HttpMethod.PUT, PASSWORD_CHANGE, token, changeBody(PASSWORD, PASSWORD))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("현재 비밀번호와 다른 비밀번호")));
	}

	@Test
	void 바꾸면_204이고_옛_비번은_끝_새_비번으로_로그인된다() throws Exception {
		request(HttpMethod.PUT, PASSWORD_CHANGE, token, changeBody(PASSWORD, NEW_PASSWORD))
				.andExpect(status().isNoContent());

		// 옛 비번이 끝났는지는 로그인 말고 비번 확인으로 본다 — 틀린 로그인은 실패 기록을 새 트랜잭션(REQUIRES_NEW)으로 쓰는데,
		// 이 테스트의 트랜잭션이 가입한 줄을 커밋하지 않고 잡고 있어 서로 기다린다(락 대기)
		request(HttpMethod.POST, VERIFY, token, passwordBody(PASSWORD))
				.andExpect(status().isBadRequest());
		request(HttpMethod.POST, VERIFY, token, passwordBody(NEW_PASSWORD))
				.andExpect(status().isNoContent());
		request(HttpMethod.POST, "/api/users/login", null, "{\"email\":\"" + EMAIL + "\",\"password\":\"" + NEW_PASSWORD + "\"}")
				.andExpect(status().isOk());
	}

	// ── 부품 ─────────────────────────────────

	private static String passwordBody(String password) {
		return "{\"password\":\"" + password + "\"}";
	}

	private static String changeBody(String currentPassword, String newPassword) {
		return "{\"currentPassword\":\"" + currentPassword + "\",\"newPassword\":\"" + newPassword + "\"}";
	}
}
