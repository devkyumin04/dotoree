package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 잔액 — 시작 날짜 · 그날 기준 합계 · 전날 대비 · 월말 6개월 (ADR-065 ④⑦, 통합 테스트)
class AccountBalanceIntegrationTest extends IntegrationTestSupport {

	private String token;
	private Integer expense;
	private Integer income;

	@BeforeEach
	void setUp() throws Exception {
		token = signupAndLogin("it-balance@test.com");
		expense = createCategory(token, "QA잔액지출", "E", null);
		income = createCategory(token, "QA잔액수입", "I", null);
	}

	// ── 시작 날짜 ─────────────────────────────

	@Test
	void 시작_날짜_전_거래는_잔액에서_빠지고_통계엔_잡힌다() throws Exception {
		// 10/20 에 통장 앱을 보고 1,000,000 으로 시작 → 다음 날 10/5 월급을 지난 기록으로 적는다
		Integer bank = createAccountOn("국민통장", "B", 1_000_000, "2026-10-20");
		add(income, 3_000_000, "2026-10-05", bank);

		assertThat(balanceOf(token, bank)).isEqualTo(1_000_000);          // 4,000,000 이 아니다 — 월급은 이미 1,000,000 안에 있다
		request(HttpMethod.GET, "/api/statistics?year=2026&month=10", token, null)
				.andExpect(jsonPath("$.totalIncome").value(3_000_000));
	}

	@Test
	void 시작_날짜_뒤_거래는_더한다() throws Exception {
		Integer bank = createAccountOn("국민통장", "B", 1_000_000, "2026-10-01");
		add(expense, 12_000, "2026-10-05", bank);

		assertThat(balanceOf(token, bank)).isEqualTo(988_000);
	}

	@Test
	void 기본_현금_계좌는_시작_날짜가_없어_처음부터_더한다() throws Exception {
		add(expense, 5_000, "2020-01-01", null);

		assertThat(balanceOf(token, defaultAccount(token))).isEqualTo(-5_000);
	}

	@Test
	void 시작_날짜를_미래로_잡으면_400() throws Exception {
		request(HttpMethod.POST, "/api/accounts", token, body("국민통장", "B", 0, LocalDate.now().plusDays(1).toString()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 응답에_시작_날짜가_나온다() throws Exception {
		request(HttpMethod.POST, "/api/accounts", token, body("국민통장", "B", 0, "2026-10-01"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.openingDate").value("2026-10-01"));
	}

	// ── 그날 기준 합계 · 전날 대비 ──────────────────────

	@Test
	void 그날_기준_합계와_전날_대비() throws Exception {
		Integer bank = createAccountOn("국민통장", "B", 100_000, "2026-10-01");
		add(expense, 30_000, "2026-10-05", bank);

		request(HttpMethod.GET, "/api/accounts/summary?date=2026-10-05", token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.date").value("2026-10-05"))
				.andExpect(jsonPath("$.totalBalance").value(70_000))
				.andExpect(jsonPath("$.previousDayTotalBalance").value(100_000))
				.andExpect(jsonPath("$.change").value(-30_000));
	}

	@Test
	void 그날_시작한_계좌의_시작_잔액은_전날_대비에_안_잡히고_그날_거래만_잡힌다() throws Exception {
		Integer bank = createAccountOn("국민통장", "B", 500_000, "2026-10-10");
		add(expense, 20_000, "2026-10-10", bank);

		request(HttpMethod.GET, "/api/accounts/summary?date=2026-10-10", token, null)
				.andExpect(jsonPath("$.totalBalance").value(480_000))
				.andExpect(jsonPath("$.previousDayTotalBalance").value(0))
				.andExpect(jsonPath("$.change").value(-20_000));          // +480,000 이 아니다 — 시작 잔액은 늘어난 돈이 아니다
	}

	@Test
	void 만든_날_0원으로_맞추고_닫으면_전날_대비_0() throws Exception {
		LocalDate today = LocalDate.now();
		Integer bank = createAccountOn("국민통장", "B", 12_340, today.toString());
		request(HttpMethod.POST, "/api/accounts/" + bank + "/adjustments", token, "{\"actualBalance\":0}")
				.andExpect(status().isCreated());
		request(HttpMethod.DELETE, "/api/accounts/" + bank, token, null).andExpect(status().isNoContent());

		request(HttpMethod.GET, "/api/accounts/summary", token, null)
				.andExpect(jsonPath("$.change").value(0));          // −12,340 이 아니다 — 아무것도 안 적었다
	}

	@Test
	void 어제_만든_계좌의_오늘_보정은_전날_대비에_잡힌다() throws Exception {
		LocalDate today = LocalDate.now();
		Integer bank = createAccountOn("국민통장", "B", 1_230_000, today.minusDays(1).toString());
		request(HttpMethod.POST, "/api/accounts/" + bank + "/adjustments", token, "{\"actualBalance\":1234560}")
				.andExpect(status().isCreated());

		request(HttpMethod.GET, "/api/accounts/summary", token, null)
				.andExpect(jsonPath("$.change").value(4_560));     // 실제로 발견한 변화
	}

	@Test
	void 지난_날로_시작한_계좌는_원래대로() throws Exception {
		Integer bank = createAccountOn("국민통장", "B", 500_000, "2026-10-01");
		add(expense, 20_000, "2026-10-10", bank);

		request(HttpMethod.GET, "/api/accounts/summary?date=2026-10-10", token, null)
				.andExpect(jsonPath("$.change").value(-20_000));
	}

	@Test
	void 시작_날짜_전_날짜의_합계엔_그_계좌가_안_들어간다() throws Exception {
		createAccountOn("국민통장", "B", 500_000, "2026-10-10");

		request(HttpMethod.GET, "/api/accounts/summary?date=2026-10-05", token, null)
				.andExpect(jsonPath("$.totalBalance").value(0));
		request(HttpMethod.GET, "/api/accounts/summary?date=2026-10-10", token, null)
				.andExpect(jsonPath("$.totalBalance").value(500_000));
	}

	@Test
	void 닫힌_계좌도_지난_날짜_합계엔_들어간다() throws Exception {
		Integer bank = createAccountOn("국민통장", "B", 50_000, "2026-10-01");
		add(expense, 50_000, "2026-10-10", bank);
		request(HttpMethod.DELETE, "/api/accounts/" + bank, token, null).andExpect(status().isNoContent());

		request(HttpMethod.GET, "/api/accounts/summary?date=2026-10-05", token, null)
				.andExpect(jsonPath("$.totalBalance").value(50_000));
		request(HttpMethod.GET, "/api/accounts/summary?date=2026-10-10", token, null)
				.andExpect(jsonPath("$.totalBalance").value(0));
	}

	@Test
	void 날짜를_비우면_오늘_형식이_틀리면_400() throws Exception {
		request(HttpMethod.GET, "/api/accounts/summary", token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.date").value(LocalDate.now().toString()));
		request(HttpMethod.GET, "/api/accounts/summary?date=abc", token, null)
				.andExpect(status().isBadRequest());
	}

	// ── 월말 6개월 ────────────────────────────────

	@Test
	void 월말_합계_6개월은_오래된_달부터() throws Exception {
		Integer bank = createAccountOn("국민통장", "B", 100_000, "2026-08-01");
		add(income, 50_000, "2026-09-10", bank);
		add(expense, 20_000, "2026-10-05", bank);

		List<String> months = read(request(HttpMethod.GET, "/api/accounts/monthly?year=2026&month=10", token, null)
				.andExpect(status().isOk()), "$[*].yearMonth");
		List<Number> totals = read(request(HttpMethod.GET, "/api/accounts/monthly?year=2026&month=10", token, null), "$[*].totalBalance");

		assertThat(months).containsExactly("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");
		assertThat(totals).extracting(Number::longValue).containsExactly(0L, 0L, 0L, 100_000L, 150_000L, 130_000L);
	}

	@Test
	void 토큰_없으면_401() throws Exception {
		request(HttpMethod.GET, "/api/accounts/summary", null, null)
				.andExpect(status().isUnauthorized());
	}

	// ── 부품 ─────────────────────────────────

	private static String body(String name, String type, long openingBalance, String openingDate) {
		return """
				{"accountName":"%s","accountType":"%s","openingBalance":%d,"openingDate":"%s"}
				""".formatted(name, type, openingBalance, openingDate);
	}

	private Integer createAccountOn(String name, String type, long openingBalance, String openingDate) throws Exception {
		return read(request(HttpMethod.POST, "/api/accounts", token, body(name, type, openingBalance, openingDate))
				.andExpect(status().isCreated()), "$.accountNum");
	}

	private void add(Integer categoryNum, long amount, String date, Integer accountNum) throws Exception {
		String account = accountNum == null ? "" : ",\"accountNum\":" + accountNum;
		request(HttpMethod.POST, "/api/transactions", token,
				"{\"categoryNum\":" + categoryNum + ",\"transAmount\":" + amount + ",\"transDate\":\"" + date + "\"" + account + "}")
				.andExpect(status().isCreated());
	}
}
