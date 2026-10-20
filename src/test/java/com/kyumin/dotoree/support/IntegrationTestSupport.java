package com.kyumin.dotoree.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

// 셸 QA(qa/test-*.sh) → JUnit 통합 테스트 번역의 공통 부품 — _lib.sh 의 login · req · jnum 자리
// 진짜 DB(CI build 잡의 MySQL, 로컬은 맥 MySQL)를 쓰고 @Transactional 이라 테스트마다 되돌린다 → 계정도 테스트 안에서 가입해 쓴다(QA 의 test@test.com 전제가 없다)
@SpringBootTest
@Transactional
public abstract class IntegrationTestSupport {

	protected static final String PASSWORD = "Qarace12!";

	@Autowired
	private WebApplicationContext context;

	protected MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		// 응답을 UTF-8 로 읽는다 — 기본(ISO-8859-1)이면 한글 이름 비교가 깨진다
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
				.build();
	}

	// 가입 → 로그인 → Access 토큰. 가입하면 '미분류' 지출·수입이 같이 생긴다(UserService)
	protected String signupAndLogin(String email) throws Exception {
		String credentials = """
				{"email":"%s","password":"%s","nickname":"dotoree"}
				""".formatted(email, PASSWORD);
		mockMvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isCreated());
		ResultActions login = mockMvc.perform(post("/api/users/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
				.andExpect(status().isOk());
		return read(login, "$.accessToken");
	}

	// 토큰을 실은 요청 — body 가 null 이면 본문 없이
	protected ResultActions request(HttpMethod method, String path, String token, String body) throws Exception {
		MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, path);
		if (token != null) {
			builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (body != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(builder);
	}

	// 응답 본문에서 값 하나 — jsonPath 예) "$.categoryNum"
	protected static <T> T read(ResultActions result, String jsonPath) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return JsonPath.read(body, jsonPath);
	}
}
