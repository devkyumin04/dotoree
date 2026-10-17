package com.kyumin.dotoree.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

// 가입 · 인증 입구 — 요청이 보안 필터 → 컨트롤러 → 서비스 → MySQL 까지 끝까지 가는지 (통합 테스트)
// 진짜 DB 를 쓴다(CI build 잡의 MySQL, 로컬은 맥 MySQL). @Transactional 이라 테스트마다 끝나면 되돌린다
@SpringBootTest
@Transactional
class UserControllerIntegrationTest {

	private static final String SIGNUP = "/api/users/signup";

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.build();
	}

	private static String signupBody(String email, String password) {
		return """
				{"email":"%s","password":"%s","nickname":"dotoree"}
				""".formatted(email, password);
	}

	@Test
	void 가입하면_201과_사용자_정보를_돌려준다() throws Exception {
		mockMvc.perform(post(SIGNUP)
						.contentType(MediaType.APPLICATION_JSON)
						.content(signupBody("it-signup@test.com", "Qarace12!")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("it-signup@test.com"))
				.andExpect(jsonPath("$.userNum").isNumber());
	}

	@Test
	void 같은_이메일로_다시_가입하면_409_ProblemDetail() throws Exception {
		// given
		mockMvc.perform(post(SIGNUP)
						.contentType(MediaType.APPLICATION_JSON)
						.content(signupBody("it-dup@test.com", "Qarace12!")))
				.andExpect(status().isCreated());

		// when · then
		mockMvc.perform(post(SIGNUP)
						.contentType(MediaType.APPLICATION_JSON)
						.content(signupBody("it-dup@test.com", "Qarace12!")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.instance").value(SIGNUP));
	}

	@Test
	void 형식이_틀린_가입은_400() throws Exception {
		mockMvc.perform(post(SIGNUP)
						.contentType(MediaType.APPLICATION_JSON)
						.content(signupBody("it-bad@test.com", "short")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 토큰_없이_내_정보를_보면_401() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized());
	}
}
