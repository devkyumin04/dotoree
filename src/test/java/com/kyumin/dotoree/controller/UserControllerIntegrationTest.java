package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 가입 · 인증 입구 — 요청이 보안 필터 → 컨트롤러 → 서비스 → MySQL 까지 끝까지 가는지 (통합 테스트)
// 진짜 DB 를 쓴다(CI build 잡의 MySQL, 로컬은 맥 MySQL). @Transactional 이라 테스트마다 끝나면 되돌린다
// 가입은 메일 코드를 받아서 한다(ADR-068) — 코드 규칙(60초 · 5번 틀림 · 10분 · 재설정)은 EmailVerificationIntegrationTest
class UserControllerIntegrationTest extends IntegrationTestSupport {

	private static final String SIGNUP = "/api/users/signup";

	@Test
	void 가입하면_201과_사용자_정보_Access_Refresh_쿠키를_준다_바로_로그인() throws Exception {
		MvcResult result = signupRequest("it-signup@test.com")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("it-signup@test.com"))
				.andExpect(jsonPath("$.userNum").isNumber())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn();

		assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
				.anyMatch(header -> header.startsWith("refresh_token=") && header.contains("Path=/api/auth"));
	}

	@Test
	void 가입_응답의_Access_로_바로_내_정보를_본다() throws Exception {
		String token = signupAndLogin("it-signup-me@test.com");

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("it-signup-me@test.com"));
	}

	@Test
	void 같은_이메일로_다시_가입하면_409_ProblemDetail() throws Exception {
		// given
		String code = emailCode("it-dup@test.com", "S");
		mockMvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(signupBody("it-dup@test.com", code)))
				.andExpect(status().isCreated());

		// when · then — 코드를 다시 받을 필요도 없이 가입된 주소라 409 (거절이 코드 확인보다 먼저)
		mockMvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(signupBody("it-dup@test.com", "000000")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.instance").value(SIGNUP));
	}

	@Test
	void 형식이_틀린_가입은_400() throws Exception {
		String code = emailCode("it-bad@test.com", "S");
		String shortPassword = """
				{"email":"it-bad@test.com","code":"%s","password":"short","nickname":"dotoree"}
				""".formatted(code);

		mockMvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON).content(shortPassword))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 코드_없이_가입하면_400() throws Exception {
		mockMvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"it-nocode@test.com\",\"password\":\"" + PASSWORD + "\",\"nickname\":\"dotoree\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 토큰_없이_내_정보를_보면_401() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized());
	}
}
