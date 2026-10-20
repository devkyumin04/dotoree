package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;
import com.kyumin.dotoree.support.IntegrationTestSupport;

import jakarta.servlet.http.Cookie;

// Refresh 토큰 — 발급 · 재발급(로테이션) · 유예 · 재사용 탐지 · 로그아웃 · 전 세션 폐기 (ADR-055). qa/test-refresh.sh 1~6 번역 (통합 테스트)
// 기기 = Device 하나. 셸의 쿠키 파일(curl -b/-c)처럼 응답의 Set-Cookie 를 받아 두었다가 다음 요청에 싣는다
// (쿠키 Path 에 따라 실을지 고르는 건 브라우저 몫이라 여기선 /api/auth 요청에만 직접 싣는다 — Path 값 자체는 Set-Cookie 로 검사)
class RefreshIntegrationTest extends IntegrationTestSupport {

	private static final String EMAIL = "it-refresh@test.com";
	private static final String REFRESH = "/api/auth/refresh";
	private static final String LOGOUT = "/api/auth/logout";
	private static final String NEW_PASSWORD = "Qa-rt-pass-1!";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Integer userNum;

	@BeforeEach
	void setUp() throws Exception {
		userNum = read(request(HttpMethod.POST, "/api/users/signup", null, """
				{"email":"%s","password":"%s","nickname":"dotoree"}
				""".formatted(EMAIL, PASSWORD))
				.andExpect(status().isCreated()), "$.userNum");
	}

	// ── 1. 발급 · 재발급(로테이션) ─────────────────────

	@Test
	void 로그인하면_200이고_Refresh_쿠키_속성이_다_붙는다() throws Exception {
		Device device = new Device();

		MvcResult login = device.login(PASSWORD).andExpect(status().isOk()).andReturn();

		assertThat(refreshSetCookie(login))
				.contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=1209600");
		assertThat(device.refreshToken).isNotBlank();
		assertThat(device.accessToken).isNotBlank();
	}

	@Test
	void 재발급하면_200_새_Refresh와_새_Access를_준다() throws Exception {
		Device device = new Device();
		device.login(PASSWORD).andExpect(status().isOk());
		String oldRefresh = device.refreshToken;

		MvcResult refresh = device.post(REFRESH).andExpect(status().isOk()).andReturn();

		assertThat(refreshSetCookie(refresh))
				.contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth", "Max-Age=1209600");
		assertThat(device.refreshToken).isNotBlank().isNotEqualTo(oldRefresh);
		assertThat((String) JsonPath.read(body(refresh), "$.accessToken")).isNotBlank();
	}

	@Test
	void 쿠키_없이_재발급하면_401() throws Exception {
		new Device().post(REFRESH).andExpect(status().isUnauthorized());
	}

	// ── 2·3. 유예 · 재사용 탐지 ──────────────────────

	@Test
	void 교체된_옛_카드가_30초_안에_다시_오면_유예로_200_살아_있는_카드는_하나() throws Exception {
		Device device = new Device();
		device.login(PASSWORD).andExpect(status().isOk());
		Device retry = device.copy();                       // 응답을 못 받은 기기 — 교체 전 카드를 그대로 들고 있다
		device.post(REFRESH).andExpect(status().isOk());

		retry.post(REFRESH).andExpect(status().isOk());

		assertThat(aliveCount()).isEqualTo(1);
	}

	@Test
	void 유예_30초가_지난_옛_카드가_오면_401이고_유예로_받은_최신_카드까지_일행_전체가_끝난다() throws Exception {
		Device device = new Device();
		device.login(PASSWORD).andExpect(status().isOk());
		Device old = device.copy();
		device.post(REFRESH).andExpect(status().isOk());
		Device retry = old.copy();
		retry.post(REFRESH).andExpect(status().isOk());      // 유예로 받은 최신 카드

		// 30초를 기다리지 않고 폐기 시각을 31초 당긴다 (셸 QA 와 같은 방식 — 상대값이라 시계 차이와 무관)
		jdbcTemplate.update("UPDATE refresh_tokens SET revoked_at = revoked_at - INTERVAL 31 SECOND WHERE user_num = ? AND revoked_yn = 'Y'", userNum);

		old.post(REFRESH).andExpect(status().isUnauthorized());     // 탐지 → 일행 회수
		retry.post(REFRESH).andExpect(status().isUnauthorized());   // 최신 카드도 끝 — 200 이면 회수가 사라진 것
		assertThat(aliveCount()).isZero();
	}

	// ── 4. 로그아웃 — 도둑 장면 ──────────────────────

	@Test
	void 도둑이_먼저_재발급해도_주인이_로그아웃하면_도둑_카드까지_끝난다() throws Exception {
		Device owner = new Device();
		owner.login(PASSWORD).andExpect(status().isOk());
		Device thief = owner.copy();                          // 카드를 복사해 간다
		thief.post(REFRESH).andExpect(status().isOk());       // 도둑이 먼저 로테이션 — 주인 카드는 이미 폐기

		MvcResult logout = owner.post(LOGOUT).andExpect(status().isNoContent()).andReturn();

		assertThat(refreshSetCookie(logout)).contains("Max-Age=0", "Path=/api/auth");   // 이름·경로가 같아야 덮어써서 지워진다
		assertThat(owner.refreshToken).isNull();
		thief.post(REFRESH).andExpect(status().isUnauthorized());
	}

	@Test
	void 쿠키_없이_로그아웃해도_204() throws Exception {
		new Device().post(LOGOUT).andExpect(status().isNoContent());
	}

	// ── 5·6. 전 세션 폐기 ───────────────────────────

	@Test
	void 비밀번호를_바꾸면_바꾼_기기까지_모든_기기의_재발급이_401() throws Exception {
		Device first = new Device();
		first.login(PASSWORD).andExpect(status().isOk());
		Device second = new Device();
		second.login(PASSWORD).andExpect(status().isOk());

		request(HttpMethod.PUT, "/api/users/me/password", first.accessToken, """
				{"currentPassword":"%s","newPassword":"%s"}
				""".formatted(PASSWORD, NEW_PASSWORD))
				.andExpect(status().isNoContent());

		first.post(REFRESH).andExpect(status().isUnauthorized());
		second.post(REFRESH).andExpect(status().isUnauthorized());
		first.login(NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void 탈퇴하면_모든_기기의_재발급이_401이고_다시_로그인하면_탈퇴_취소로_재발급된다() throws Exception {
		Device first = new Device();
		first.login(PASSWORD).andExpect(status().isOk());
		Device second = new Device();
		second.login(PASSWORD).andExpect(status().isOk());

		request(HttpMethod.POST, "/api/users/withdraw", first.accessToken, "{\"password\":\"" + PASSWORD + "\"}")
				.andExpect(status().isNoContent());

		first.post(REFRESH).andExpect(status().isUnauthorized());   // 유예 중('W') 계정이 옛 카드로 재발급받던 구멍
		second.post(REFRESH).andExpect(status().isUnauthorized());
		first.login(PASSWORD).andExpect(status().isOk());           // 로그인 = 탈퇴 취소 (ADR-052)
		first.post(REFRESH).andExpect(status().isOk());
	}

	// ── 부품 ─────────────────────────────────

	private int aliveCount() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM refresh_tokens WHERE user_num = ? AND revoked_yn = 'N'", Integer.class, userNum);
	}

	private static String body(MvcResult result) throws Exception {
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	// 응답의 refresh_token Set-Cookie 줄 하나 (없으면 빈 문자열)
	private static String refreshSetCookie(MvcResult result) {
		return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
				.filter(line -> line.startsWith("refresh_token="))
				.findFirst()
				.orElse("");
	}

	// 기기 하나 — 쿠키 파일 대신 Refresh 원문과 마지막 Access 를 들고 있다
	private final class Device {

		private String refreshToken;
		private String accessToken;

		ResultActions login(String password) throws Exception {
			return send(MockMvcRequestBuilders.post("/api/users/login").contentType(MediaType.APPLICATION_JSON)
					.content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"));
		}

		ResultActions post(String path) throws Exception {
			return send(MockMvcRequestBuilders.post(path));
		}

		Device copy() {
			Device copied = new Device();
			copied.refreshToken = refreshToken;
			copied.accessToken = accessToken;
			return copied;
		}

		// 실을 카드가 있으면 쿠키로 → 응답의 Set-Cookie · Access 를 받아 둔다(브라우저와 같은 동작)
		private ResultActions send(MockHttpServletRequestBuilder builder) throws Exception {
			if (refreshToken != null) {
				builder.cookie(new Cookie("refresh_token", refreshToken));
			}
			ResultActions result = mockMvc.perform(builder);
			MvcResult mvcResult = result.andReturn();

			String setCookie = refreshSetCookie(mvcResult);
			if (!setCookie.isEmpty()) {
				String value = setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
				refreshToken = setCookie.contains("Max-Age=0") ? null : value;
			}
			String content = body(mvcResult);
			if (mvcResult.getResponse().getStatus() == 200 && content.contains("\"accessToken\"")) {
				accessToken = JsonPath.read(content, "$.accessToken");
			}
			return result;
		}
	}
}
