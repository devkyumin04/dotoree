package com.kyumin.dotoree.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 카테고리 부모 검증 · 하위 있으면 삭제 거부 · 이름 정규화 — qa/test-category-hierarchy.sh 의 A~R 번역 (통합 테스트)
// 장면마다 대분류부터 새로 만든다(테스트끼리 데이터를 나누지 않는다)
class CategoryHierarchyIntegrationTest extends IntegrationTestSupport {

	private static final String CATEGORIES = "/api/categories";

	private String token;

	@BeforeEach
	void setUp() throws Exception {
		token = signupAndLogin("it-cat@test.com");
	}

	// ── 4. 부모 검증 ─────────────────────────────

	@Test
	void A_지출_대분류_밑에_지출_소분류를_만들면_201() throws Exception {
		Integer big = createCategory(token, "QA대분류", "E", null);

		request(HttpMethod.POST, CATEGORIES, token, categoryBody("QA카페", "E", big))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.parentCategoryNum").value(big));
	}

	@Test
	void B_지출_대분류_밑에_수입_소분류는_400() throws Exception {
		Integer big = createCategory(token, "QA대분류", "E", null);

		request(HttpMethod.POST, CATEGORIES, token, categoryBody("QA월급", "I", big))
				.andExpect(status().isBadRequest());
	}

	@Test
	void C_소분류_밑에_또_소분류는_400() throws Exception {
		Integer big = createCategory(token, "QA대분류", "E", null);
		Integer cafe = createCategory(token, "QA카페", "E", big);

		request(HttpMethod.POST, CATEGORIES, token, categoryBody("QA스벅", "E", cafe))
				.andExpect(status().isBadRequest());
	}

	@Test
	void D_없는_부모는_404() throws Exception {
		request(HttpMethod.POST, CATEGORIES, token, categoryBody("x", "E", 99999))
				.andExpect(status().isNotFound());
	}

	@Test
	void E_미분류를_부모로_고르면_400() throws Exception {
		Integer defaultExpense = uncategorized(token, "E");

		request(HttpMethod.POST, CATEGORIES, token, categoryBody("x", "E", defaultExpense))
				.andExpect(status().isBadRequest());
	}

	@Test
	void F_남의_카테고리를_부모로_고르면_403() throws Exception {
		String otherToken = signupAndLogin("it-cat-other@test.com");
		Integer others = uncategorized(otherToken, "E");

		request(HttpMethod.POST, CATEGORIES, token, categoryBody("x", "E", others))
				.andExpect(status().isForbidden());
	}

	@Test
	void G_자기_자신을_부모로_바꾸면_400() throws Exception {
		Integer big = createCategory(token, "QA대분류", "E", null);

		request(HttpMethod.PUT, CATEGORIES + "/" + big, token, categoryBody("QA대분류", "E", big))
				.andExpect(status().isBadRequest());
	}

	@Test
	void H_소분류를_가진_대분류를_다른_대분류_밑으로_옮기면_400() throws Exception {
		Integer big = createCategory(token, "QA대분류", "E", null);
		createCategory(token, "QA카페", "E", big);
		Integer trans = createCategory(token, "QA교통", "E", null);

		request(HttpMethod.PUT, CATEGORIES + "/" + big, token, categoryBody("QA대분류", "E", trans))
				.andExpect(status().isBadRequest());
	}

	@Test
	void I_소분류의_부모를_다른_대분류로_바꾸면_200() throws Exception {
		Integer big = createCategory(token, "QA대분류", "E", null);
		Integer cafe = createCategory(token, "QA카페", "E", big);
		Integer trans = createCategory(token, "QA교통", "E", null);

		request(HttpMethod.PUT, CATEGORIES + "/" + cafe, token, categoryBody("QA카페", "E", trans))
				.andExpect(status().isOk());
	}

	// ── 9. 하위 있으면 삭제 거부 ─────────────────────

	@Test
	void J_K_L_소분류가_있으면_대분류_삭제는_400_소분류를_지우면_204() throws Exception {
		Integer trans = createCategory(token, "QA교통", "E", null);
		Integer cafe = createCategory(token, "QA카페", "E", trans);

		request(HttpMethod.DELETE, CATEGORIES + "/" + trans, token, null)
				.andExpect(status().isBadRequest());
		request(HttpMethod.DELETE, CATEGORIES + "/" + cafe, token, null)
				.andExpect(status().isNoContent());
		request(HttpMethod.DELETE, CATEGORIES + "/" + trans, token, null)
				.andExpect(status().isNoContent());
	}

	// ── 11. 이름 정규화 — 앞뒤 공백은 저장 전에 지운다(DB collation 에 맡기지 않는다) ──

	@Test
	void M_N_앞뒤_공백은_지우고_저장한다() throws Exception {
		Integer spaced = read(request(HttpMethod.POST, CATEGORIES, token, categoryBody("  QA공백  ", "E", null))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.categoryName").value("QA공백")), "$.categoryNum");

		request(HttpMethod.PUT, CATEGORIES + "/" + spaced, token, categoryBody("QA공백수정   ", "E", null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categoryName").value("QA공백수정"));
	}

	@Test
	void O_P_공백을_붙인_미분류_이름은_400() throws Exception {
		Integer spaced = createCategory(token, "QA공백", "E", null);

		request(HttpMethod.POST, CATEGORIES, token, categoryBody("미분류 ", "E", null))
				.andExpect(status().isBadRequest());
		request(HttpMethod.PUT, CATEGORIES + "/" + spaced, token, categoryBody(" 미분류", "E", null))
				.andExpect(status().isBadRequest());
	}

	@Test
	void Q_공백뿐인_이름은_400() throws Exception {
		request(HttpMethod.POST, CATEGORIES, token, categoryBody("   ", "E", null))
				.andExpect(status().isBadRequest());
	}

	@Test
	void R_전각_공백도_지운다() throws Exception {
		request(HttpMethod.POST, CATEGORIES, token, categoryBody("\u3000QA전각\u3000", "E", null))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.categoryName").value("QA전각"));
	}
}
