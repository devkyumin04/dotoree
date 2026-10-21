package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 계좌 API — 등록 · 목록(잔액) · 수정 · 닫기 + 거래에 계좌 고르기 (ADR-065, 통합 테스트)
class AccountIntegrationTest extends IntegrationTestSupport {

	private static final String ACCOUNTS = "/api/accounts";

	private String token;
	private Integer expense;
	private Integer income;

	@BeforeEach
	void setUp() throws Exception {
		token = signupAndLogin("it-acc@test.com");
		expense = createCategory(token, "QA계좌지출", "E", null);
		income = createCategory(token, "QA계좌수입", "I", null);
	}

	// ── 등록 · 종류별 규칙 ──────────────────────────

	@Test
	void 통장을_등록하면_201이고_잔액은_시작_잔액() throws Exception {
		request(HttpMethod.POST, ACCOUNTS, token, accountBody("국민통장", "B", 1_000_000, null))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accountType").value("B"))
				.andExpect(jsonPath("$.isDefault").value(false))
				.andExpect(jsonPath("$.balance").value(1_000_000));
	}

	@Test
	void 현금_통장의_시작_잔액은_0_이상() throws Exception {
		request(HttpMethod.POST, ACCOUNTS, token, accountBody("지갑", "C", -5000, null))
				.andExpect(status().isBadRequest());
		request(HttpMethod.POST, ACCOUNTS, token, accountBody("국민통장", "B", -1, null))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 한도는_마이너스통장_카드만() throws Exception {
		request(HttpMethod.POST, ACCOUNTS, token, accountBody("국민통장", "B", 0, 1_000_000L))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 마이너스통장은_마이너스_한도까지_넘으면_400() throws Exception {
		request(HttpMethod.POST, ACCOUNTS, token, accountBody("마통", "M", -300_000, 5_000_000L))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.balance").value(-300_000));
		request(HttpMethod.POST, ACCOUNTS, token, accountBody("마통2", "M", -6_000_000, 5_000_000L))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 카드는_한도를_안_적으면_마이너스_제한이_없다() throws Exception {
		request(HttpMethod.POST, ACCOUNTS, token, accountBody("신한카드", "K", -200_000, null))
				.andExpect(status().isCreated());
	}

	@Test
	void 잘못된_종류나_빈_이름은_400() throws Exception {
		request(HttpMethod.POST, ACCOUNTS, token, accountBody("이상한", "X", 0, null))
				.andExpect(status().isBadRequest());
		request(HttpMethod.POST, ACCOUNTS, token, accountBody("   ", "B", 0, null))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 열린_계좌끼리_같은_이름은_409() throws Exception {
		createAccount("국민통장", "B", 0);

		request(HttpMethod.POST, ACCOUNTS, token, accountBody(" 국민통장 ", "B", 0, null))
				.andExpect(status().isConflict());
	}

	// ── 목록 · 잔액 ───────────────────────────────

	@Test
	void 목록은_기본_현금이_맨_앞이고_잔액은_시작_잔액_더하기_수입_빼기_지출() throws Exception {
		Integer bank = createAccount("국민통장", "B", 1_000_000);
		createTransaction(income, 3_000_000, bank);
		createTransaction(expense, 12_000, bank);
		createTransaction(expense, 5_000, null);    // 기본 현금 — 0 − 5,000

		List<Map<String, Object>> accounts = read(request(HttpMethod.GET, ACCOUNTS, token, null)
				.andExpect(status().isOk()), "$");

		assertThat(accounts).hasSize(2);
		assertThat(accounts.get(0).get("accountName")).isEqualTo("현금");
		assertThat(accounts.get(0).get("isDefault")).isEqualTo(true);
		assertThat(((Number) accounts.get(0).get("balance")).longValue()).isEqualTo(-5_000);
		assertThat(((Number) accounts.get(1).get("balance")).longValue()).isEqualTo(3_988_000);
	}

	@Test
	void 삭제한_거래는_잔액에서_빠진다() throws Exception {
		Integer bank = createAccount("국민통장", "B", 10_000);
		Integer trans = createTransaction(expense, 3_000, bank);
		request(HttpMethod.DELETE, "/api/transactions/" + trans + "?version=0", token, null)
				.andExpect(status().isNoContent());

		assertThat(balanceOf(bank)).isEqualTo(10_000);
	}

	// ── 수정 ────────────────────────────────────

	@Test
	void 수정하면_200이고_잔액은_거래를_반영한_값() throws Exception {
		Integer bank = createAccount("국민통장", "B", 0);
		createTransaction(income, 50_000, bank);

		request(HttpMethod.PUT, ACCOUNTS + "/" + bank, token, accountBody("월급통장", "B", 10_000, null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountName").value("월급통장"))
				.andExpect(jsonPath("$.balance").value(60_000));
	}

	@Test
	void 남의_계좌는_403_없는_계좌는_404() throws Exception {
		String otherToken = signupAndLogin("it-acc-other@test.com");
		Integer others = read(request(HttpMethod.POST, ACCOUNTS, otherToken, accountBody("남의통장", "B", 0, null))
				.andExpect(status().isCreated()), "$.accountNum");

		request(HttpMethod.PUT, ACCOUNTS + "/" + others, token, accountBody("뺏기", "B", 0, null))
				.andExpect(status().isForbidden());
		request(HttpMethod.DELETE, ACCOUNTS + "/" + others, token, null)
				.andExpect(status().isForbidden());
		request(HttpMethod.PUT, ACCOUNTS + "/999999", token, accountBody("없음", "B", 0, null))
				.andExpect(status().isNotFound());
	}

	// ── 닫기 ────────────────────────────────────

	@Test
	void 잔액_0이면_닫히고_목록에서_빠지지만_지난_거래는_그_계좌로_남는다() throws Exception {
		Integer bank = createAccount("카카오통장", "B", 0);
		Integer in = createTransaction(income, 5_000, bank);
		createTransaction(expense, 5_000, bank);

		request(HttpMethod.DELETE, ACCOUNTS + "/" + bank, token, null)
				.andExpect(status().isNoContent());

		List<Map<String, Object>> accounts = read(request(HttpMethod.GET, ACCOUNTS, token, null), "$");
		assertThat(accounts).noneMatch(row -> bank.equals(row.get("accountNum")));
		Map<String, Object> past = findTransaction(token, in);
		assertThat(past.get("accountNum")).isEqualTo(bank);
		assertThat(past.get("accountName")).isEqualTo("카카오통장");
	}

	@Test
	void 잔액이_0이_아니면_닫기는_400() throws Exception {
		Integer bank = createAccount("카카오통장", "B", 50_000);

		request(HttpMethod.DELETE, ACCOUNTS + "/" + bank, token, null)
				.andExpect(status().isBadRequest());
	}

	@Test
	void 기본_계좌는_닫을_수_없다_403() throws Exception {
		request(HttpMethod.DELETE, ACCOUNTS + "/" + defaultAccount(), token, null)
				.andExpect(status().isForbidden());
	}

	@Test
	void 닫은_계좌_이름은_다시_쓸_수_있다() throws Exception {
		Integer bank = createAccount("카카오통장", "B", 0);
		request(HttpMethod.DELETE, ACCOUNTS + "/" + bank, token, null).andExpect(status().isNoContent());

		request(HttpMethod.POST, ACCOUNTS, token, accountBody("카카오통장", "B", 0, null))
				.andExpect(status().isCreated());
	}

	// ── 거래에 계좌 고르기 ───────────────────────────

	@Test
	void 거래에_계좌를_고르면_그_계좌로_비우면_기본_계좌로() throws Exception {
		Integer bank = createAccount("국민통장", "B", 0);

		request(HttpMethod.POST, "/api/transactions", token, transactionBodyWithAccount(expense, 1_000, bank))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accountNum").value(bank))
				.andExpect(jsonPath("$.accountName").value("국민통장"));
		request(HttpMethod.POST, "/api/transactions", token, transactionBodyWithAccount(expense, 1_000, null))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accountName").value("현금"));
	}

	@Test
	void 닫힌_계좌로는_거래를_못_넣는다_400_남의_계좌는_403_없는_계좌는_404() throws Exception {
		Integer bank = createAccount("카카오통장", "B", 0);
		request(HttpMethod.DELETE, ACCOUNTS + "/" + bank, token, null).andExpect(status().isNoContent());
		String otherToken = signupAndLogin("it-acc-other@test.com");
		Integer others = read(request(HttpMethod.POST, ACCOUNTS, otherToken, accountBody("남의통장", "B", 0, null))
				.andExpect(status().isCreated()), "$.accountNum");

		request(HttpMethod.POST, "/api/transactions", token, transactionBodyWithAccount(expense, 1_000, bank))
				.andExpect(status().isBadRequest());
		request(HttpMethod.POST, "/api/transactions", token, transactionBodyWithAccount(expense, 1_000, others))
				.andExpect(status().isForbidden());
		request(HttpMethod.POST, "/api/transactions", token, transactionBodyWithAccount(expense, 1_000, 999999))
				.andExpect(status().isNotFound());
	}

	@Test
	void 거래_수정으로_계좌를_옮기면_두_계좌_잔액이_같이_바뀐다() throws Exception {
		Integer bank = createAccount("국민통장", "B", 0);
		Integer trans = createTransaction(expense, 7_000, null);

		request(HttpMethod.PUT, "/api/transactions/" + trans + "?version=0", token, transactionBodyWithAccount(expense, 7_000, bank))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountNum").value(bank));

		assertThat(balanceOf(defaultAccount())).isZero();
		assertThat(balanceOf(bank)).isEqualTo(-7_000);
	}

	@Test
	void 토큰_없으면_401() throws Exception {
		request(HttpMethod.GET, ACCOUNTS, null, null)
				.andExpect(status().isUnauthorized());
	}

	// ── 부품 ─────────────────────────────────

	private Integer createAccount(String name, String type, long openingBalance) throws Exception {
		return read(request(HttpMethod.POST, ACCOUNTS, token, accountBody(name, type, openingBalance, null))
				.andExpect(status().isCreated()), "$.accountNum");
	}

	private static String transactionBodyWithAccount(Integer categoryNum, long amount, Integer accountNum) {
		String account = accountNum == null ? "" : ",\"accountNum\":" + accountNum;
		return "{\"categoryNum\":" + categoryNum + ",\"transAmount\":" + amount + ",\"transDate\":\"" + TRANS_DATE + "\"" + account + "}";
	}

	private Integer createTransaction(Integer categoryNum, long amount, Integer accountNum) throws Exception {
		return read(request(HttpMethod.POST, "/api/transactions", token, transactionBodyWithAccount(categoryNum, amount, accountNum))
				.andExpect(status().isCreated()), "$.transNum");
	}

	private Integer defaultAccount() throws Exception {
		List<Integer> found = read(request(HttpMethod.GET, ACCOUNTS, token, null), "$[?(@.isDefault == true)].accountNum");
		return found.get(0);
	}

	private long balanceOf(Integer accountNum) throws Exception {
		List<Number> found = read(request(HttpMethod.GET, ACCOUNTS, token, null), "$[?(@.accountNum == " + accountNum + ")].balance");
		return found.get(0).longValue();
	}
}
