package com.kyumin.dotoree.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// API 문서(Swagger UI) — 로그인 없이 열리고, 자물쇠 · 공통 에러 응답이 규칙대로 붙는다 (ADR-074)
class ApiDocsIntegrationTest extends IntegrationTestSupport {

	@Test
	void 문서는_로그인_없이_열린다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("도토리 API"))
				.andExpect(jsonPath("$.paths['/api/accounts']").exists());
	}

	@Test
	void 문서_화면도_로그인_없이_열리고_스타일을_허용하는_CSP가_붙는다() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Security-Policy", Matchers.containsString("style-src 'self' 'unsafe-inline'")));
	}

	@Test
	void 앱_화면의_CSP는_그대로_엄격하다() throws Exception {
		mockMvc.perform(get("/"))
				.andExpect(header().string("Content-Security-Policy", Matchers.not(Matchers.containsString("unsafe-inline"))));
	}

	@Test
	void 로그인이_필요한_주소에는_자물쇠와_401_400이_붙는다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/accounts'].post.security[0]").exists())
				.andExpect(jsonPath("$.paths['/api/accounts'].post.responses['401']").exists())
				.andExpect(jsonPath("$.paths['/api/accounts'].post.responses['400']").exists());
	}

	@Test
	void 열린_주소에는_자물쇠가_없다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/users/login'].post.security").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/auth/demo-login'].post.security").doesNotExist());
	}

	@Test
	void 번호가_들어가는_주소에는_404가_붙고_에러_모양은_ProblemDetail이다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/accounts/{accountNum}'].delete.responses['404']").exists())
				.andExpect(jsonPath("$.components.schemas.ProblemDetail.properties.detail").exists());
	}

	@Test
	void 화면용_컨트롤러는_문서에_없다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/']").doesNotExist())
				.andExpect(jsonPath("$.paths['/error']").doesNotExist());
	}
}
