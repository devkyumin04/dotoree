package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 읽기 전용 데모 계정 — 데모로 둘러보기(비밀번호 없이 로그인) · 읽기는 되고 쓰기는 403 · 재설정 메일은 안 간다 (통합 테스트)
// 데모 주소는 application.yml 의 dotoree.demo.email. 테스트 안에서 그 주소로 가입해 데모 계정을 흉내 낸다(@Transactional 이라 되돌려진다)
class DemoAccountIntegrationTest extends IntegrationTestSupport {

	private static final String DEMO_EMAIL = "demo@dotoree.app";

	@Test
	void 데모_계정이_없으면_둘러보기는_404() throws Exception {
		mockMvc.perform(post("/api/auth/demo-login"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 데모로_둘러보기는_비밀번호_없이_로그인된다() throws Exception {
		signup(DEMO_EMAIL);

		mockMvc.perform(post("/api/auth/demo-login"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(DEMO_EMAIL))
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	void 데모_계정은_읽기는_되고_쓰기는_403이다() throws Exception {
		signup(DEMO_EMAIL);
		String token = read(mockMvc.perform(post("/api/auth/demo-login")).andExpect(status().isOk()), "$.accessToken");
		Integer uncategorized = uncategorized(token, "E");

		request(HttpMethod.GET, "/api/transactions" + TRANS_MONTH_QUERY, token, null)
				.andExpect(status().isOk());
		request(HttpMethod.POST, "/api/transactions", token, transactionBody(uncategorized, 1000, "데모쓰기"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("데모 계정은 보기만 할 수 있어요. 직접 써 보려면 회원가입해 주세요."));
		request(HttpMethod.POST, "/api/categories", token, categoryBody("데모카테고리", "E", null))
				.andExpect(status().isForbidden());
		assertThat(monthTransactions(token)).isEmpty();
	}

	@Test
	void 데모_계정도_로그아웃은_된다() throws Exception {
		signup(DEMO_EMAIL);
		String token = read(mockMvc.perform(post("/api/auth/demo-login")).andExpect(status().isOk()), "$.accessToken");

		request(HttpMethod.POST, "/api/auth/logout", token, null)
				.andExpect(status().isNoContent());
	}

	@Test
	void 데모가_아닌_계정은_쓰기가_그대로_된다() throws Exception {
		String token = signupAndLogin("it-notdemo@test.com");
		Integer uncategorized = uncategorized(token, "E");

		createTransaction(token, uncategorized, 1000, "일반쓰기");

		assertThat(monthTransactions(token)).hasSize(1);
	}

	@Test
	void 데모_주소로는_재설정_메일을_보내지_않는다() throws Exception {
		signup(DEMO_EMAIL);
		mailbox.clear(DEMO_EMAIL);

		mockMvc.perform(post("/api/auth/email-codes").contentType(MediaType.APPLICATION_JSON)
				.content(emailCodeBody(DEMO_EMAIL, "P")))
				.andExpect(status().isNoContent());

		assertThat(mailbox.hasMailFor(DEMO_EMAIL)).isFalse();
	}
}
