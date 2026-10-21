package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 반복 예정 — 규칙 · 남은 예정 · 확인 · 건너뛰기 · 예상 잔액 · 계좌 닫기 · 카테고리 지우기 (ADR-067, 통합 테스트)
// 시작 날짜는 API 로는 오늘 이후만 된다 → 지난 예정이 필요한 장면은 만든 뒤 JdbcTemplate 으로 시작 날짜를 옮긴다(날짜가 고정돼 언제 돌려도 같다)
class RecurringIntegrationTest extends IntegrationTestSupport {

	private static final String RULES = "/api/recurring";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String token;
	private Integer rent;
	private Integer salary;
	private Integer bank;

	@BeforeEach
	void setUp() throws Exception {
		token = signupAndLogin("it-recurring@test.com");
		rent = createCategory(token, "QA월세", "E", null);
		salary = createCategory(token, "QA월급", "I", null);
		bank = createAccount(token, "국민통장", "B", 1_000_000);
	}

	// ── 남은 예정 ───────────────────────────────

	@Test
	void 매달_31일_규칙은_없는_달엔_말일로_예정이_뜬다() throws Exception {
		Integer rule = createRule(monthlyBody(rent, bank, 500_000, 31));
		moveStart(rule, "2026-01-01");

		List<String> dates = read(pending("2026-04-30"), "$[*].scheduledDate");
		assertThat(dates).containsExactly("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30");
	}

	@Test
	void 매년_규칙은_그_달에만() throws Exception {
		Integer rule = createRule("""
				{"categoryNum":%d,"accountNum":%d,"ruleAmount":600000,"repeatType":"Y","repeatMonth":3,"repeatDay":15}
				""".formatted(rent, bank));
		moveStart(rule, "2026-01-01");

		List<String> dates = read(pending("2027-12-31"), "$[*].scheduledDate");
		assertThat(dates).containsExactly("2026-03-15", "2027-03-15");
	}

	@Test
	void 끝_날짜_뒤엔_예정이_없다() throws Exception {
		Integer rule = createRule(monthlyBody(rent, bank, 50_000, 10));
		jdbcTemplate.update("UPDATE recurring_rules SET start_date = '2026-01-01', end_date = '2026-03-31' WHERE rule_num = ?", rule);

		List<String> dates = read(pending("2026-06-30"), "$[*].scheduledDate");
		assertThat(dates).containsExactly("2026-01-10", "2026-02-10", "2026-03-10");
	}

	// ── 확인 · 건너뛰기 ──────────────────────────

	@Test
	void 확인하면_고친_금액으로_거래가_생기고_예정에서_빠진다() throws Exception {
		Integer rule = createRule(monthlyBody(rent, bank, 500_000, 25));
		moveStart(rule, "2026-09-01");

		request(HttpMethod.POST, RULES + "/" + rule + "/confirm", token, "{\"scheduledDate\":\"2026-09-25\",\"amount\":520000}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("C"))
				.andExpect(jsonPath("$.transNum").isNumber());

		assertThat(balanceOf(token, bank)).isEqualTo(480_000);
		request(HttpMethod.GET, "/api/statistics?year=2026&month=9", token, null)
				.andExpect(jsonPath("$.totalExpense").value(520_000));       // 확인한 거래는 손으로 적은 거래와 같다
		List<String> dates = read(pending("2026-10-31"), "$[*].scheduledDate");
		assertThat(dates).containsExactly("2026-10-25");
	}

	@Test
	void 같은_예정을_두_번_확인하면_409_거래는_한_줄() throws Exception {
		Integer rule = createRule(monthlyBody(rent, bank, 500_000, 25));
		moveStart(rule, "2026-09-01");
		String body = "{\"scheduledDate\":\"2026-09-25\"}";

		request(HttpMethod.POST, RULES + "/" + rule + "/confirm", token, body).andExpect(status().isCreated());
		request(HttpMethod.POST, RULES + "/" + rule + "/confirm", token, body).andExpect(status().isConflict());

		assertThat(balanceOf(token, bank)).isEqualTo(500_000);
	}

	@Test
	void 건너뛰면_예정에서만_빠지고_거래는_안_생긴다() throws Exception {
		Integer rule = createRule(monthlyBody(rent, bank, 500_000, 25));
		moveStart(rule, "2026-09-01");

		request(HttpMethod.POST, RULES + "/" + rule + "/skip", token, "{\"scheduledDate\":\"2026-09-25\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("S"));

		assertThat(balanceOf(token, bank)).isEqualTo(1_000_000);
		List<String> dates = read(pending("2026-10-31"), "$[*].scheduledDate");
		assertThat(dates).containsExactly("2026-10-25");
	}

	@Test
	void 예정일이_아닌_날은_400() throws Exception {
		Integer rule = createRule(monthlyBody(rent, bank, 500_000, 25));
		moveStart(rule, "2026-09-01");

		request(HttpMethod.POST, RULES + "/" + rule + "/confirm", token, "{\"scheduledDate\":\"2026-09-24\"}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void 이체_규칙을_확인하면_이체가_생긴다() throws Exception {
		Integer card = createAccount(token, "신한카드", "K", -450_000);
		Integer rule = createRule("""
				{"fromAccountNum":%d,"toAccountNum":%d,"ruleAmount":450000,"ruleMemo":"카드값","repeatType":"M","repeatDay":15}
				""".formatted(bank, card));
		moveStart(rule, "2026-09-01");

		request(HttpMethod.POST, RULES + "/" + rule + "/confirm", token, "{\"scheduledDate\":\"2026-09-15\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.transferNum").isNumber());

		assertThat(balanceOf(token, bank)).isEqualTo(550_000);
		assertThat(balanceOf(token, card)).isZero();
	}

	// ── 예상 잔액 ───────────────────────────────

	@Test
	void 예상_잔액은_합계와_계좌별_모자란_날까지() throws Exception {
		// 국민통장 300,000 · 현금 200,000 → 11/1 국민통장에서 월세 500,000
		Integer small = createAccount(token, "작은통장", "B", 300_000);
		request(HttpMethod.POST, "/api/transactions", token, transactionBody(salary, 200_000, "용돈"))
				.andExpect(status().isCreated());                       // 기본 '현금'
		Integer rule = createRule(monthlyBody(rent, small, 500_000, 1));
		moveStart(rule, "2026-11-01");

		var forecast = request(HttpMethod.GET, RULES + "/forecast?until=2026-11-30", token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.untilDate").value("2026-11-30"))
				.andExpect(jsonPath("$.currentTotal").value(1_500_000))
				.andExpect(jsonPath("$.forecastTotal").value(1_000_000));
		String path = "$.accounts[?(@.accountNum == " + small + ")]";
		List<Number> balance = read(forecast, path + ".forecastBalance");
		List<String> lowestDate = read(forecast, path + ".lowestDate");
		List<Boolean> shortage = read(forecast, path + ".shortage");
		assertThat(balance.get(0).longValue()).isEqualTo(-200_000);
		assertThat(lowestDate).containsExactly("2026-11-01");
		assertThat(shortage).containsExactly(true);
		List<Boolean> bankShortage = read(forecast, "$.accounts[?(@.accountNum == " + bank + ")].shortage");
		assertThat(bankShortage).containsExactly(false);
	}

	// ── 계좌 · 카테고리와 엮인 것 ──────────────────

	@Test
	void 반복이_쓰는_계좌는_그냥은_못_닫고_같이_끝내면_닫힌다() throws Exception {
		Integer empty = createAccount(token, "빈통장", "B", 0);
		createRule(monthlyBody(rent, empty, 10_000, 5));

		request(HttpMethod.DELETE, "/api/accounts/" + empty, token, null)
				.andExpect(status().isBadRequest());
		request(HttpMethod.DELETE, "/api/accounts/" + empty + "?endRecurring=true", token, null)
				.andExpect(status().isNoContent());

		List<Object> rules = read(request(HttpMethod.GET, RULES, token, null).andExpect(status().isOk()), "$[*]");
		assertThat(rules).isEmpty();
	}

	@Test
	void 카테고리를_지우면_반복은_미분류로() throws Exception {
		Integer gym = createCategory(token, "QA헬스", "E", null);
		createRule(monthlyBody(gym, bank, 50_000, 5));

		request(HttpMethod.DELETE, "/api/categories/" + gym, token, null).andExpect(status().isNoContent());

		List<String> names = read(request(HttpMethod.GET, RULES, token, null), "$[*].categoryName");
		assertThat(names).containsExactly("미분류");
	}

	// ── 거절 ─────────────────────────────────

	@Test
	void 거래와_이체를_같이_주거나_아무것도_안_주면_400() throws Exception {
		request(HttpMethod.POST, RULES, token, """
				{"categoryNum":%d,"fromAccountNum":%d,"toAccountNum":%d,"ruleAmount":1000,"repeatType":"M","repeatDay":1}
				""".formatted(rent, bank, defaultAccount(token)))
				.andExpect(status().isBadRequest());
		request(HttpMethod.POST, RULES, token, "{\"ruleAmount\":1000,\"repeatType\":\"M\",\"repeatDay\":1}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void 매년인데_달이_없으면_400() throws Exception {
		request(HttpMethod.POST, RULES, token, """
				{"categoryNum":%d,"ruleAmount":1000,"repeatType":"Y","repeatDay":1}
				""".formatted(rent))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 시작이_어제면_400() throws Exception {
		request(HttpMethod.POST, RULES, token, """
				{"categoryNum":%d,"ruleAmount":1000,"repeatType":"M","repeatDay":1,"startDate":"%s"}
				""".formatted(rent, LocalDate.now().minusDays(1)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 남의_규칙은_403() throws Exception {
		Integer rule = createRule(monthlyBody(rent, bank, 500_000, 25));
		String other = signupAndLogin("it-recurring-other@test.com");

		request(HttpMethod.DELETE, RULES + "/" + rule, other, null).andExpect(status().isForbidden());
		request(HttpMethod.POST, RULES + "/" + rule + "/skip", other, "{\"scheduledDate\":\"2026-12-25\"}")
				.andExpect(status().isForbidden());
	}

	// ── 부품 ─────────────────────────────────

	private Integer createRule(String body) throws Exception {
		return read(request(HttpMethod.POST, RULES, token, body).andExpect(status().isCreated()), "$.ruleNum");
	}

	private static String monthlyBody(Integer categoryNum, Integer accountNum, long amount, int day) {
		return """
				{"categoryNum":%d,"accountNum":%d,"ruleAmount":%d,"repeatType":"M","repeatDay":%d}
				""".formatted(categoryNum, accountNum, amount, day);
	}

	private void moveStart(Integer ruleNum, String startDate) {
		jdbcTemplate.update("UPDATE recurring_rules SET start_date = ? WHERE rule_num = ?", startDate, ruleNum);
	}

	private org.springframework.test.web.servlet.ResultActions pending(String until) throws Exception {
		return request(HttpMethod.GET, RULES + "/pending?until=" + until, token, null).andExpect(status().isOk());
	}
}
