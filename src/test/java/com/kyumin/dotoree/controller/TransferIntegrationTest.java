package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 이체 — 등록 · 월 목록 · 수정 · 삭제. 두 계좌 잔액만 옮기고 통계에는 안 잡힌다 (ADR-065, 통합 테스트)
class TransferIntegrationTest extends IntegrationTestSupport {

	private static final String TRANSFERS = "/api/transfers";

	private String token;
	private Integer cash;
	private Integer bank;

	@BeforeEach
	void setUp() throws Exception {
		token = signupAndLogin("it-transfer2@test.com");
		cash = defaultAccount(token);
		bank = createAccount(token, "국민통장", "B", 100_000);
	}

	// ── 정상 ─────────────────────────────────

	@Test
	void 이체하면_보낸_계좌는_줄고_받은_계좌는_늘고_합은_그대로() throws Exception {
		request(HttpMethod.POST, TRANSFERS, token, transferBody(bank, cash, 30_000, TRANS_DATE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.version").value(0))
				.andExpect(jsonPath("$.fromAccountName").value("국민통장"))
				.andExpect(jsonPath("$.toAccountName").value("현금"));

		assertThat(balanceOf(token, bank)).isEqualTo(70_000);
		assertThat(balanceOf(token, cash)).isEqualTo(30_000);
	}

	@Test
	void 카드값_이체는_통계_지출에_안_잡히고_카드_잔액만_0으로() throws Exception {
		Integer card = createAccount(token, "신한카드", "K", 0);
		Integer cafe = createCategory(token, "QA카페", "E", null);
		request(HttpMethod.POST, "/api/transactions", token, """
				{"categoryNum":%d,"transAmount":30000,"transDate":"%s","accountNum":%d}
				""".formatted(cafe, TRANS_DATE, card))
				.andExpect(status().isCreated());     // 쓴 날 지출 — 카드 −30,000

		request(HttpMethod.POST, TRANSFERS, token, transferBody(bank, card, 30_000, TRANS_DATE))
				.andExpect(status().isCreated());     // 결제일 — 통장 → 카드

		assertThat(balanceOf(token, card)).isZero();
		assertThat(balanceOf(token, bank)).isEqualTo(70_000);
		request(HttpMethod.GET, "/api/statistics" + TRANS_MONTH_QUERY, token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalExpense").value(30_000));   // 60,000 이 아니다
	}

	@Test
	void 월_목록에_계좌_이름과_함께_나오고_다른_달은_안_나온다() throws Exception {
		Integer october = createTransfer(bank, cash, 10_000, TRANS_DATE);
		Integer september = createTransfer(bank, cash, 5_000, "2026-09-15");

		request(HttpMethod.GET, TRANSFERS + TRANS_MONTH_QUERY, token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.transferNum == " + october + ")].toAccountName").value("현금"))
				.andExpect(jsonPath("$[?(@.transferNum == " + september + ")]").isEmpty());
	}

	@Test
	void 이체로_잔액을_0으로_맞추면_계좌를_닫을_수_있다() throws Exception {
		createTransfer(bank, cash, 100_000, TRANS_DATE);

		request(HttpMethod.DELETE, "/api/accounts/" + bank, token, null)
				.andExpect(status().isNoContent());
	}

	// ── 수정 · 삭제 ──────────────────────────────

	@Test
	void 수정하면_version이_오르고_잔액도_바뀐다_옛_version은_409() throws Exception {
		Integer transfer = createTransfer(bank, cash, 10_000, TRANS_DATE);

		request(HttpMethod.PUT, TRANSFERS + "/" + transfer + "?version=0", token, transferBody(bank, cash, 25_000, TRANS_DATE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1));
		assertThat(balanceOf(token, bank)).isEqualTo(75_000);
		assertThat(balanceOf(token, cash)).isEqualTo(25_000);

		request(HttpMethod.PUT, TRANSFERS + "/" + transfer + "?version=0", token, transferBody(bank, cash, 1_000, TRANS_DATE))
				.andExpect(status().isConflict());
	}

	@Test
	void 삭제하면_잔액이_돌아오고_목록에서_빠진다() throws Exception {
		Integer transfer = createTransfer(bank, cash, 10_000, TRANS_DATE);

		request(HttpMethod.DELETE, TRANSFERS + "/" + transfer + "?version=0", token, null)
				.andExpect(status().isNoContent());

		assertThat(balanceOf(token, bank)).isEqualTo(100_000);
		assertThat(balanceOf(token, cash)).isZero();
		request(HttpMethod.GET, TRANSFERS + TRANS_MONTH_QUERY, token, null)
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void 남의_이체는_403_없는_이체는_404() throws Exception {
		String otherToken = signupAndLogin("it-transfer2-other@test.com");
		Integer otherCash = defaultAccount(otherToken);
		Integer otherBank = createAccount(otherToken, "남의통장", "B", 10_000);
		Integer others = read(request(HttpMethod.POST, TRANSFERS, otherToken, transferBody(otherBank, otherCash, 1_000, TRANS_DATE))
				.andExpect(status().isCreated()), "$.transferNum");

		request(HttpMethod.DELETE, TRANSFERS + "/" + others + "?version=0", token, null)
				.andExpect(status().isForbidden());
		request(HttpMethod.DELETE, TRANSFERS + "/999999?version=0", token, null)
				.andExpect(status().isNotFound());
	}

	// ── 실수 · 악의적 ─────────────────────────────

	@Test
	void 같은_계좌끼리는_400() throws Exception {
		request(HttpMethod.POST, TRANSFERS, token, transferBody(bank, bank, 1_000, TRANS_DATE))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 금액_0이나_미래_날짜는_400() throws Exception {
		request(HttpMethod.POST, TRANSFERS, token, transferBody(bank, cash, 0, TRANS_DATE))
				.andExpect(status().isBadRequest());
		request(HttpMethod.POST, TRANSFERS, token, transferBody(bank, cash, 1_000, LocalDate.now().plusDays(1).toString()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 남의_계좌는_403_닫힌_계좌는_400_없는_계좌는_404() throws Exception {
		String otherToken = signupAndLogin("it-transfer2-other@test.com");
		Integer others = defaultAccount(otherToken);
		Integer closed = createAccount(token, "닫을통장", "B", 0);
		request(HttpMethod.DELETE, "/api/accounts/" + closed, token, null).andExpect(status().isNoContent());

		request(HttpMethod.POST, TRANSFERS, token, transferBody(bank, others, 1_000, TRANS_DATE))
				.andExpect(status().isForbidden());
		request(HttpMethod.POST, TRANSFERS, token, transferBody(bank, closed, 1_000, TRANS_DATE))
				.andExpect(status().isBadRequest());
		request(HttpMethod.POST, TRANSFERS, token, transferBody(bank, 999999, 1_000, TRANS_DATE))
				.andExpect(status().isNotFound());
	}

	@Test
	void 토큰_없으면_401() throws Exception {
		request(HttpMethod.GET, TRANSFERS + TRANS_MONTH_QUERY, null, null)
				.andExpect(status().isUnauthorized());
	}

	// ── 부품 ─────────────────────────────────

	private static String transferBody(Integer from, Integer to, long amount, String date) {
		return """
				{"fromAccountNum":%d,"toAccountNum":%d,"transferAmount":%d,"transferDate":"%s"}
				""".formatted(from, to, amount, date);
	}

	private Integer createTransfer(Integer from, Integer to, long amount, String date) throws Exception {
		return read(request(HttpMethod.POST, TRANSFERS, token, transferBody(from, to, amount, date))
				.andExpect(status().isCreated()), "$.transferNum");
	}
}
