package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 카테고리를 지우면 그 거래는 같은 종류의 '미분류'로 옮겨진다 (ADR-023) — qa/test-category-transfer.sh 1~12 번역 (통합 테스트)
// 검사 대상은 DELETE 의 상태코드가 아니라 부수효과 — 지운 뒤 거래 목록을 다시 읽어 카테고리·version 을 본다
class CategoryTransferIntegrationTest extends IntegrationTestSupport {

	private static final String CATEGORIES = "/api/categories/";
	private static final String TRANSACTIONS = "/api/transactions/";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String token;
	private Integer defaultExpense;
	private Integer defaultIncome;

	@BeforeEach
	void setUp() throws Exception {
		token = signupAndLogin("it-transfer@test.com");
		defaultExpense = uncategorized(token, "E");
		defaultIncome = uncategorized(token, "I");
	}

	// ── 정상 — 거래 건수 0 · 1 · 3 ─────────────────

	@Test
	void 거래가_없는_카테고리를_지워도_목록_건수는_그대로() throws Exception {
		int before = monthTransactions(token).size();
		Integer empty = createCategory(token, "QA빈", "E", null);

		request(HttpMethod.DELETE, CATEGORIES + empty, token, null)
				.andExpect(status().isNoContent());

		assertThat(monthTransactions(token)).hasSize(before);
	}

	@Test
	void 거래_1건은_지출_미분류로_옮겨지고_version이_오른다() throws Exception {
		Integer category = createCategory(token, "QA1건", "E", null);
		Integer trans = read(request(HttpMethod.POST, "/api/transactions", token, transactionBody(category, 12000, "이관1건"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value(0)), "$.transNum");

		request(HttpMethod.DELETE, CATEGORIES + category, token, null)
				.andExpect(status().isNoContent());

		Map<String, Object> moved = findTransaction(token, trans);
		assertThat(moved.get("categoryNum")).isEqualTo(defaultExpense);
		assertThat(moved.get("categoryName")).isEqualTo("미분류");
		assertThat(moved.get("version")).isEqualTo(1);
		assertThat(moved.get("transType")).isEqualTo("E");
	}

	@Test
	void 거래_3건은_전부_옮겨지고_지운_카테고리에_남은_거래가_없다() throws Exception {
		Integer category = createCategory(token, "QA3건", "E", null);
		Integer first = createTransaction(token, category, 1000, "셋1");
		Integer second = createTransaction(token, category, 2000, "셋2");
		Integer third = createTransaction(token, category, 3000, "셋3");

		request(HttpMethod.DELETE, CATEGORIES + category, token, null)
				.andExpect(status().isNoContent());

		for (Integer trans : List.of(first, second, third)) {
			assertThat(findTransaction(token, trans).get("categoryNum")).isEqualTo(defaultExpense);
		}
		assertThat(monthTransactions(token)).noneMatch(row -> category.equals(row.get("categoryNum")));
	}

	// ── 정상 — 종류 E / I ─────────────────────────

	@Test
	void 수입_카테고리의_거래는_수입_미분류로_옮겨진다() throws Exception {
		Integer income = createCategory(token, "QA수입", "I", null);
		Integer trans = createTransaction(token, income, 3_000_000, "월급");

		request(HttpMethod.DELETE, CATEGORIES + income, token, null)
				.andExpect(status().isNoContent());

		Map<String, Object> moved = findTransaction(token, trans);
		assertThat(moved.get("categoryNum")).isEqualTo(defaultIncome);
		assertThat(moved.get("transType")).isEqualTo("I");
	}

	@Test
	void 지출_카테고리를_지워도_수입_거래는_그대로() throws Exception {
		Integer expense = createCategory(token, "QA격리E", "E", null);
		Integer income = createCategory(token, "QA격리I", "I", null);
		Integer expenseTrans = createTransaction(token, expense, 5000, "격리지출");
		Integer incomeTrans = createTransaction(token, income, 7000, "격리수입");

		request(HttpMethod.DELETE, CATEGORIES + expense, token, null)
				.andExpect(status().isNoContent());

		assertThat(findTransaction(token, expenseTrans).get("categoryNum")).isEqualTo(defaultExpense);
		Map<String, Object> untouched = findTransaction(token, incomeTrans);
		assertThat(untouched.get("categoryNum")).isEqualTo(income);
		assertThat(untouched.get("version")).isEqualTo(0);
	}

	// ── 순서 의존 — 이관이 version 을 올린 뒤 (ADR-023 · ADR-028) ─────

	@Test
	void 이관_뒤엔_옛_version_수정은_409_새_version은_200() throws Exception {
		Integer category = createCategory(token, "QA1건", "E", null);
		Integer trans = createTransaction(token, category, 12000, "이관1건");
		request(HttpMethod.DELETE, CATEGORIES + category, token, null)
				.andExpect(status().isNoContent());

		request(HttpMethod.PUT, TRANSACTIONS + trans + "?version=0", token, transactionBody(defaultExpense, 9999, "옛버전"))
				.andExpect(status().isConflict());
		request(HttpMethod.PUT, TRANSACTIONS + trans + "?version=1", token, transactionBody(defaultExpense, 9999, "새버전"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2));
	}

	@Test
	void 이미_삭제된_거래는_옮기지_않는다() throws Exception {
		Integer category = createCategory(token, "QA삭제", "E", null);
		Integer alive = createTransaction(token, category, 1100, "살아있음");
		Integer deleted = createTransaction(token, category, 2200, "먼저삭제");
		request(HttpMethod.DELETE, TRANSACTIONS + deleted + "?version=0", token, null)
				.andExpect(status().isNoContent());

		request(HttpMethod.DELETE, CATEGORIES + category, token, null)
				.andExpect(status().isNoContent());

		Map<String, Object> moved = findTransaction(token, alive);
		assertThat(moved.get("categoryNum")).isEqualTo(defaultExpense);
		assertThat(moved.get("version")).isEqualTo(1);
		assertThat(findTransaction(token, deleted)).isNull();
		// 셸 QA 에선 손으로 보던 것 — 삭제된 거래는 원래 카테고리 · 삭제 때의 version(1) 그대로 (같은 트랜잭션이라 커밋 전 값이 보인다)
		Map<String, Object> deletedRow = jdbcTemplate.queryForMap(
				"SELECT category_num, version, use_yn FROM personal_transactions WHERE trans_num = ?", deleted);
		assertThat(((Number) deletedRow.get("category_num")).intValue()).isEqualTo(category);
		assertThat(((Number) deletedRow.get("version")).intValue()).isEqualTo(1);
		assertThat(deletedRow.get("use_yn")).isEqualTo("N");
	}

	// ── 거절 — 예외가 이관보다 먼저인가 ───────────────────

	@Test
	void 남의_카테고리를_지우면_403이고_남의_거래는_그대로() throws Exception {
		String otherToken = signupAndLogin("it-transfer-other@test.com");
		Integer others = createCategory(otherToken, "QA타인", "E", null);
		Integer othersTrans = createTransaction(otherToken, others, 4400, "타인거래");

		request(HttpMethod.DELETE, CATEGORIES + others, token, null)
				.andExpect(status().isForbidden());

		Map<String, Object> untouched = findTransaction(otherToken, othersTrans);
		assertThat(untouched.get("categoryNum")).isEqualTo(others);
		assertThat(untouched.get("version")).isEqualTo(0);
	}

	@Test
	void 소분류가_있는_대분류를_지우면_400이고_거래는_그대로() throws Exception {
		Integer parent = createCategory(token, "QA부모", "E", null);
		createCategory(token, "QA자식", "E", parent);
		Integer parentTrans = createTransaction(token, parent, 6600, "부모거래");

		request(HttpMethod.DELETE, CATEGORIES + parent, token, null)
				.andExpect(status().isBadRequest());

		Map<String, Object> untouched = findTransaction(token, parentTrans);
		assertThat(untouched.get("categoryNum")).isEqualTo(parent);
		assertThat(untouched.get("version")).isEqualTo(0);
	}

	@Test
	void 미분류_자체는_지울_수_없다_403() throws Exception {
		request(HttpMethod.DELETE, CATEGORIES + defaultExpense, token, null)
				.andExpect(status().isForbidden());
	}
}
