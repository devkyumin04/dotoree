package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 개인 통계 GET /api/statistics?year=&month= — qa/test-statistics.sh 1~8 번역 (통합 테스트)
// 요약 4개(수입 · 지출 · 이번 달 증감 · 저축률) · 봉투(대분류 + 소분류) · 최근 6개월 추이가 한 응답에서 서로 맞는지
//
// 장면마다 같은 데이터를 다시 넣는다 (테스트끼리 데이터를 나누지 않는다)
//   2020-06-30 지출 7,000                       → 7월 기준 추이에서 빠지는 시작 경계
//   2020-07    수입 3,000,000 / 지출 12,000     → 저축률 99.6
//   2020-08    지출 5,000 만                     → 수입 0 → 저축률 null, incomeList []
//   2020-10    수입 100,000 만 (+ 남의 지출 9,999) → 지출 0 → 저축률 100.0, expenseList []
//   2020-11    지출 50,000 → 삭제               → 전부 0
//   2020-12    수입 500 / 지출 1,000            → 저축률 -100.0, 이번 달 증감 -500
//   2021-01-01 지출 2,000                       → 12월 조회엔 빠지고 3월 조회 추이엔 들어감
//   2021-03    수입 3,000 / 지출 1,500(대분류 직접) + 500(소분류) → 저축률 33.3, 봉투 2,000 = 75.0 + 25.0
class StatisticsIntegrationTest extends IntegrationTestSupport {

	private static final String STATISTICS = "/api/statistics";

	private String token;
	private Integer expense;
	private Integer expenseSub;
	private Integer income;

	@BeforeEach
	void setUp() throws Exception {
		token = signupAndLogin("it-stat@test.com");
		expense = createCategory(token, "QA통계지출", "E", null);
		expenseSub = createCategory(token, "QA통계소분류", "E", expense);
		income = createCategory(token, "QA통계수입", "I", null);

		add(token, expense, 7000, "2020-06-30");
		add(token, income, 3_000_000, "2020-07-01");
		add(token, expense, 12000, "2020-07-15");
		add(token, expense, 5000, "2020-08-10");
		add(token, income, 100_000, "2020-10-05");
		Integer deleted = add(token, expense, 50_000, "2020-11-20");
		request(HttpMethod.DELETE, "/api/transactions/" + deleted + "?version=0", token, null)
				.andExpect(status().isNoContent());
		add(token, expense, 1000, "2020-12-31");
		add(token, income, 500, "2020-12-31");
		add(token, expense, 2000, "2021-01-01");
		add(token, income, 3000, "2021-03-10");
		add(token, expense, 1500, "2021-03-11");
		add(token, expenseSub, 500, "2021-03-12");

		String otherToken = signupAndLogin("it-stat-other@test.com");
		Integer othersCategory = createCategory(otherToken, "QA통계타인", "E", null);
		add(otherToken, othersCategory, 9999, "2020-10-05");
	}

	// ── 정상 ─────────────────────────────────

	@Test
	void 요약_봉투_추이가_한_응답에서_서로_맞는다_2020년_12월() throws Exception {
		ResultActions result = statistics(2020, 12);

		summary(result, 500, 1000, -500)
				.andExpect(jsonPath("$.savingsRate").value(-100.0))
				.andExpect(jsonPath("$.expenseList[0].categoryNum").value(expense))
				.andExpect(jsonPath("$.expenseList[0].amount").value(1000))
				.andExpect(jsonPath("$.expenseList[0].ratio").value(100.0))
				.andExpect(jsonPath("$.incomeList[0].categoryNum").value(income))
				.andExpect(jsonPath("$.incomeList[0].amount").value(500))
				.andExpect(jsonPath("$.incomeList[0].ratio").value(100.0));

		assertThat(months(result)).containsExactly("2020-07", "2020-08", "2020-09", "2020-10", "2020-11", "2020-12");
		assertMonth(result, "2020-12", 500, 1000, -500);      // 추이 마지막 = 요약과 같은 값
		assertMonth(result, "2020-11", 0, 0, 0);              // 삭제한 거래는 빠진다
		assertMonth(result, "2020-07", 3_000_000, 12000, 2_988_000); // 7/1 은 들어가고 6/30 은 빠진다
		assertMonth(result, "2020-09", 0, 0, 0);              // 거래 없는 달도 0 으로 채운다
	}

	// ── 경계 ─────────────────────────────────

	@Test
	void 수입이_0이면_저축률은_null이고_수입_목록은_빈_배열() throws Exception {
		summary(statistics(2020, 8), 0, 5000, -5000)
				.andExpect(jsonPath("$.savingsRate").value(nullValue()))
				.andExpect(jsonPath("$.incomeList").isEmpty());
	}

	@Test
	void 지출이_0이면_저축률_100이고_지출_목록은_빈_배열_남의_거래는_안_섞인다() throws Exception {
		summary(statistics(2020, 10), 100_000, 0, 100_000)
				.andExpect(jsonPath("$.savingsRate").value(100.0))
				.andExpect(jsonPath("$.expenseList").isEmpty());
	}

	@Test
	void 거래가_전혀_없는_달은_0_null_빈_배열() throws Exception {
		ResultActions result = statistics(1999, 6);

		summary(result, 0, 0, 0)
				.andExpect(jsonPath("$.savingsRate").value(nullValue()))
				.andExpect(jsonPath("$.expenseList").isEmpty())
				.andExpect(jsonPath("$.incomeList").isEmpty());
		assertThat(months(result)).containsExactly("1999-01", "1999-02", "1999-03", "1999-04", "1999-05", "1999-06");
	}

	// ── 소수점 · 봉투 ──────────────────────────────

	@Test
	void 저축률은_수입_빼기_지출을_수입으로_나눈_값_2020년_7월은_99점6() throws Exception {
		summary(statistics(2020, 7), 3_000_000, 12000, 2_988_000)
				.andExpect(jsonPath("$.savingsRate").value(99.6));
	}

	@Test
	void 봉투는_대분류_직접_거래와_소분류를_묶는다_2021년_3월() throws Exception {
		ResultActions result = statistics(2021, 3);

		summary(result, 3000, 2000, 1000)
				.andExpect(jsonPath("$.savingsRate").value(33.3))
				.andExpect(jsonPath("$.expenseList[0].categoryNum").value(expense))
				.andExpect(jsonPath("$.expenseList[0].amount").value(2000))
				.andExpect(jsonPath("$.expenseList[0].ratio").value(100.0));

		// 봉투 안 줄 — 비율은 봉투(부모) 합계 대비
		Map<String, Object> direct = child(result, expense);
		assertThat(direct.get("amount")).isEqualTo(1500);
		assertThat(direct.get("ratio")).isEqualTo(75.0);
		Map<String, Object> sub = child(result, expenseSub);
		assertThat(sub.get("amount")).isEqualTo(500);
		assertThat(sub.get("ratio")).isEqualTo(25.0);

		assertThat(months(result)).containsExactly("2020-10", "2020-11", "2020-12", "2021-01", "2021-02", "2021-03");
		assertMonth(result, "2021-01", 0, 2000, -2000);       // 12월 조회 땐 빠졌던 1/1 이 여기선 들어간다
		assertMonth(result, "2021-02", 0, 0, 0);
	}

	@Test
	void 저축률은_소수_둘째_자리에서_HALF_UP() throws Exception {
		// 수입 2,000 · 지출 1,999 → 1 ÷ 2,000 × 100 = 0.05 → HALF_UP 0.1 (HALF_EVEN · 버림이면 0.0)
		add(token, income, 2000, "2019-01-10");
		add(token, expense, 1999, "2019-01-11");

		summary(statistics(2019, 1), 2000, 1999, 1)
				.andExpect(jsonPath("$.savingsRate").value(0.1));
	}

	// ── 실수 · 악의적 ─────────────────────────────

	@Test
	void month는_1부터_12까지만() throws Exception {
		statistics(2020, 13)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("허용 범위")));
		statistics(2020, 0).andExpect(status().isBadRequest());
		statistics(2020, -1).andExpect(status().isBadRequest());
		statistics(2020, 1).andExpect(status().isOk());
	}

	@Test
	void 연도_월이_빠지거나_숫자가_아니면_400() throws Exception {
		request(HttpMethod.GET, STATISTICS + "?year=2020", token, null).andExpect(status().isBadRequest());
		request(HttpMethod.GET, STATISTICS + "?month=12", token, null).andExpect(status().isBadRequest());
		request(HttpMethod.GET, STATISTICS + "?year=2020&month=abc", token, null).andExpect(status().isBadRequest());
	}

	@Test
	void 토큰_없으면_401() throws Exception {
		request(HttpMethod.GET, STATISTICS + "?year=2020&month=12", null, null)
				.andExpect(status().isUnauthorized());
	}

	// ── 부품 ─────────────────────────────────

	private Integer add(String userToken, Integer categoryNum, long amount, String date) throws Exception {
		return read(request(HttpMethod.POST, "/api/transactions", userToken, """
				{"categoryNum":%d,"transAmount":%d,"transDate":"%s"}
				""".formatted(categoryNum, amount, date))
				.andExpect(status().isCreated()), "$.transNum");
	}

	private ResultActions statistics(int year, int month) throws Exception {
		return request(HttpMethod.GET, STATISTICS + "?year=" + year + "&month=" + month, token, null);
	}

	// 요약 셋(수입 · 지출 · 잔액) — 수입 대비 지출은 장면마다 따로
	private static ResultActions summary(ResultActions result, long totalIncome, long totalExpense, long balance) throws Exception {
		return result.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalIncome").value(totalIncome))
				.andExpect(jsonPath("$.totalExpense").value(totalExpense))
				.andExpect(jsonPath("$.balance").value(balance));
	}

	private static List<String> months(ResultActions result) throws Exception {
		return read(result, "$.monthlyList[*].yearMonth");
	}

	private static void assertMonth(ResultActions result, String yearMonth, long totalIncome, long totalExpense, long balance) throws Exception {
		List<Map<String, Object>> found = read(result, "$.monthlyList[?(@.yearMonth == '" + yearMonth + "')]");
		assertThat(found).hasSize(1);
		Map<String, Object> month = found.get(0);
		assertThat(((Number) month.get("totalIncome")).longValue()).as(yearMonth + " 수입").isEqualTo(totalIncome);
		assertThat(((Number) month.get("totalExpense")).longValue()).as(yearMonth + " 지출").isEqualTo(totalExpense);
		assertThat(((Number) month.get("balance")).longValue()).as(yearMonth + " 잔액").isEqualTo(balance);
	}

	// 첫 지출 봉투 안에서 categoryNum 줄 하나
	private static Map<String, Object> child(ResultActions result, Integer categoryNum) throws Exception {
		List<Map<String, Object>> found = read(result, "$.expenseList[0].children[?(@.categoryNum == " + categoryNum + ")]");
		assertThat(found).hasSize(1);
		return found.get(0);
	}
}
