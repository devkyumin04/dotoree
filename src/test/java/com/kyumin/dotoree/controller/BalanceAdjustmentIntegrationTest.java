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

// 잔액 맞추기 — 실제 잔액을 넣으면 차이만큼 보정. 잔액엔 들어가고 통계엔 안 들어간다 (ADR-066, 통합 테스트)
class BalanceAdjustmentIntegrationTest extends IntegrationTestSupport {

	private String token;
	private Integer bank;

	@BeforeEach
	void setUp() throws Exception {
		token = signupAndLogin("it-adjust@test.com");
		bank = createAccount(token, "국민통장", "B", 1_230_000);
	}

	// ── 정상 ─────────────────────────────────

	@Test
	void 실제_잔액을_넣으면_차이만큼_보정되고_잔액이_실제와_같아진다() throws Exception {
		// 은행 앱엔 이자 4,560원이 붙어 1,234,560원
		request(HttpMethod.POST, adjustPath(bank), token, actual(1_234_560))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.adjustmentAmount").value(4_560))
				.andExpect(jsonPath("$.accountName").value("국민통장"))
				.andExpect(jsonPath("$.adjustmentDate").value(LocalDate.now().toString()));

		assertThat(balanceOf(token, bank)).isEqualTo(1_234_560);
	}

	@Test
	void 실제가_더_적으면_마이너스로_보정한다() throws Exception {
		request(HttpMethod.POST, adjustPath(bank), token, actual(1_200_000))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.adjustmentAmount").value(-30_000));

		assertThat(balanceOf(token, bank)).isEqualTo(1_200_000);
	}

	@Test
	void 카드는_마이너스_실제_잔액으로_맞춘다() throws Exception {
		Integer card = createAccount(token, "신한카드", "K", 0);

		request(HttpMethod.POST, adjustPath(card), token, actual(-452_000))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.adjustmentAmount").value(-452_000));

		assertThat(balanceOf(token, card)).isEqualTo(-452_000);
	}

	@Test
	void 차이가_없으면_아무것도_안_만들고_204() throws Exception {
		request(HttpMethod.POST, adjustPath(bank), token, actual(1_230_000))
				.andExpect(status().isNoContent());

		assertThat(monthAdjustments()).isEmpty();
	}

	@Test
	void 보정은_통계_수입_지출에_안_잡히고_순자산엔_잡힌다() throws Exception {
		request(HttpMethod.POST, adjustPath(bank), token, actual(1_300_000)).andExpect(status().isCreated());

		LocalDate today = LocalDate.now();
		request(HttpMethod.GET, "/api/statistics?year=" + today.getYear() + "&month=" + today.getMonthValue(), token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalIncome").value(0))
				.andExpect(jsonPath("$.totalExpense").value(0));
		request(HttpMethod.GET, "/api/accounts/summary", token, null)
				.andExpect(jsonPath("$.totalBalance").value(1_300_000));
	}

	@Test
	void 월_목록에_나오고_지우면_잔액이_돌아간다() throws Exception {
		Integer adjustmentNum = read(request(HttpMethod.POST, adjustPath(bank), token, actual(1_234_560))
				.andExpect(status().isCreated()), "$.adjustmentNum");
		assertThat(monthAdjustments()).hasSize(1);

		request(HttpMethod.DELETE, "/api/adjustments/" + adjustmentNum, token, null)
				.andExpect(status().isNoContent());

		assertThat(monthAdjustments()).isEmpty();
		assertThat(balanceOf(token, bank)).isEqualTo(1_230_000);
	}

	@Test
	void 남은_잔액을_0으로_맞추면_닫을_수_있다() throws Exception {
		request(HttpMethod.DELETE, "/api/accounts/" + bank, token, null)
				.andExpect(status().isBadRequest());          // 잔액 1,230,000 — 아직 못 닫는다

		request(HttpMethod.POST, adjustPath(bank), token, actual(0)).andExpect(status().isCreated());
		request(HttpMethod.DELETE, "/api/accounts/" + bank, token, null)
				.andExpect(status().isNoContent());
	}

	// ── 거절 ─────────────────────────────────

	@Test
	void 실제_잔액을_비우면_400() throws Exception {
		request(HttpMethod.POST, adjustPath(bank), token, "{}")
				.andExpect(status().isBadRequest());
	}

	@Test
	void 남의_계좌는_403_남의_보정은_못_지운다() throws Exception {
		Integer adjustmentNum = read(request(HttpMethod.POST, adjustPath(bank), token, actual(1_234_560))
				.andExpect(status().isCreated()), "$.adjustmentNum");
		String other = signupAndLogin("it-adjust-other@test.com");

		request(HttpMethod.POST, adjustPath(bank), other, actual(0))
				.andExpect(status().isForbidden());
		request(HttpMethod.DELETE, "/api/adjustments/" + adjustmentNum, other, null)
				.andExpect(status().isForbidden());
	}

	@Test
	void 닫힌_계좌는_맞출_수_없다() throws Exception {
		Integer empty = createAccount(token, "빈통장", "B", 0);
		request(HttpMethod.DELETE, "/api/accounts/" + empty, token, null).andExpect(status().isNoContent());

		request(HttpMethod.POST, adjustPath(empty), token, actual(1_000))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 없는_보정을_지우면_404() throws Exception {
		request(HttpMethod.DELETE, "/api/adjustments/999999", token, null)
				.andExpect(status().isNotFound());
	}

	// ── 부품 ─────────────────────────────────

	private static String adjustPath(Integer accountNum) {
		return "/api/accounts/" + accountNum + "/adjustments";
	}

	private static String actual(long actualBalance) {
		return "{\"actualBalance\":" + actualBalance + "}";
	}

	private List<Object> monthAdjustments() throws Exception {
		LocalDate today = LocalDate.now();
		return read(request(HttpMethod.GET, "/api/adjustments?year=" + today.getYear() + "&month=" + today.getMonthValue(), token, null)
				.andExpect(status().isOk()), "$[*]");
	}
}
