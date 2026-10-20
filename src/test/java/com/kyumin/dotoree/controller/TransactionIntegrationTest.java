package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 개인 지출 · 수입 CRUD — 정상 / 실수 / 악의적. qa/test-transaction.sh 1~31 번역 (통합 테스트)
class TransactionIntegrationTest extends IntegrationTestSupport {

	private static final String TRANSACTIONS = "/api/transactions";

	private String token;
	private Integer expense;
	private Integer income;

	@BeforeEach
	void setUp() throws Exception {
		token = signupAndLogin("it-trans@test.com");
		expense = createCategory(token, "QA거래지출", "E", null);
		income = createCategory(token, "QA거래수입", "I", null);
	}

	// ── 정상 ─────────────────────────────────

	@Test
	void 지출을_등록하면_201_종류는_카테고리를_따르고_version은_0() throws Exception {
		request(HttpMethod.POST, TRANSACTIONS, token, transactionBody(expense, 12000, "점심"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.transType").value("E"))
				.andExpect(jsonPath("$.version").value(0))
				.andExpect(jsonPath("$.categoryName").value("QA거래지출"));
	}

	@Test
	void 수입_카테고리로_등록하면_종류가_I() throws Exception {
		request(HttpMethod.POST, TRANSACTIONS, token, transactionBody(income, 3_000_000, "월급"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.transType").value("I"));
	}

	@Test
	void 메모는_없어도_된다() throws Exception {
		request(HttpMethod.POST, TRANSACTIONS, token, """
				{"categoryNum":%d,"transAmount":5000,"transDate":"%s"}
				""".formatted(expense, TRANS_DATE))
				.andExpect(status().isCreated());
	}

	@Test
	void 오늘_날짜와_1원은_경계라_통과() throws Exception {
		request(HttpMethod.POST, TRANSACTIONS, token, """
				{"categoryNum":%d,"transAmount":1,"transDate":"%s","transMemo":"오늘경계"}
				""".formatted(expense, LocalDate.now()))
				.andExpect(status().isCreated());
	}

	@Test
	void 월_목록은_200이고_카테고리_번호와_이모지_칸을_담는다() throws Exception {
		createTransaction(token, expense, 12000, "점심");

		request(HttpMethod.GET, TRANSACTIONS + TRANS_MONTH_QUERY, token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].categoryNum").exists())
				.andExpect(jsonPath("$[0]").value(hasKey("categoryEmoji")));
	}

	@Test
	void 거래가_없는_달도_200() throws Exception {
		request(HttpMethod.GET, TRANSACTIONS + "?year=1999&month=1", token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void 수정할_때마다_version이_하나씩_오른다() throws Exception {
		Integer trans = createTransaction(token, expense, 12000, "점심");

		request(HttpMethod.PUT, TRANSACTIONS + "/" + trans + "?version=0", token, transactionBody(expense, 15000, "점심수정"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1));
		request(HttpMethod.PUT, TRANSACTIONS + "/" + trans + "?version=1", token, transactionBody(expense, 16000, "연속수정"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2));
	}

	@Test
	void 수입_카테고리로_바꾸면_종류도_I로_바뀐다() throws Exception {
		Integer trans = createTransaction(token, expense, 16000, "점심");

		request(HttpMethod.PUT, TRANSACTIONS + "/" + trans + "?version=0", token, transactionBody(income, 16000, "타입변경"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.transType").value("I"));
	}

	@Test
	void 삭제하면_204이고_목록에서_빠진다() throws Exception {
		Integer trans = createTransaction(token, income, 3_000_000, "월급");

		request(HttpMethod.DELETE, TRANSACTIONS + "/" + trans + "?version=0", token, null)
				.andExpect(status().isNoContent());

		assertThat(findTransaction(token, trans)).isNull();
	}

	// ── 실수 — 입력값 ───────────────────────────
	// %d 자리에 내 지출 카테고리 번호가 들어간다(없는 본문은 그대로)

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {
			"{\"categoryNum\":%d,\"transAmount\":0,\"transDate\":\"2026-10-01\"}",
			"{\"categoryNum\":%d,\"transAmount\":-5000,\"transDate\":\"2026-10-01\"}",
			"{\"categoryNum\":%d,\"transAmount\":1000000000001,\"transDate\":\"2026-10-01\"}",
			"{\"categoryNum\":%d,\"transDate\":\"2026-10-01\"}",
			"{\"categoryNum\":%d,\"transAmount\":5000}",
			"{\"transAmount\":5000,\"transDate\":\"2026-10-01\"}",
			"{\"categoryNum\":%d,\"transAmount\":5000,\"transDate\":\"2026-13-45\"}",
			"{\"categoryNum\":%d,\"transAmount\":"
	})
	void 금액_0_음수_1조_초과_빠진_칸_없는_날짜_깨진_JSON은_400(String body) throws Exception {
		request(HttpMethod.POST, TRANSACTIONS, token, body.formatted(expense))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 미래_날짜는_400() throws Exception {
		request(HttpMethod.POST, TRANSACTIONS, token, """
				{"categoryNum":%d,"transAmount":5000,"transDate":"%s"}
				""".formatted(expense, LocalDate.now().plusDays(1)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 메모_101자는_400() throws Exception {
		request(HttpMethod.POST, TRANSACTIONS, token, transactionBody(expense, 5000, "a".repeat(101)))
				.andExpect(status().isBadRequest());
	}

	// ── 실수 — 없는 것 · 빠진 파라미터 ─────────────────

	@Test
	void 없는_카테고리로_등록하면_404() throws Exception {
		request(HttpMethod.POST, TRANSACTIONS, token, transactionBody(999999, 5000, "x"))
				.andExpect(status().isNotFound());
	}

	@Test
	void 없는_거래를_수정_삭제하면_404() throws Exception {
		request(HttpMethod.PUT, TRANSACTIONS + "/999999?version=0", token, transactionBody(expense, 5000, "x"))
				.andExpect(status().isNotFound());
		request(HttpMethod.DELETE, TRANSACTIONS + "/999999?version=0", token, null)
				.andExpect(status().isNotFound());
	}

	@Test
	void 연도나_월이_빠진_목록_조회는_400() throws Exception {
		request(HttpMethod.GET, TRANSACTIONS + "?month=10", token, null)
				.andExpect(status().isBadRequest());
		request(HttpMethod.GET, TRANSACTIONS + "?year=2026", token, null)
				.andExpect(status().isBadRequest());
	}

	@Test
	void version_없이_수정_삭제하면_400() throws Exception {
		Integer trans = createTransaction(token, expense, 12000, "점심");

		request(HttpMethod.PUT, TRANSACTIONS + "/" + trans, token, transactionBody(expense, 5000, "x"))
				.andExpect(status().isBadRequest());
		request(HttpMethod.DELETE, TRANSACTIONS + "/" + trans, token, null)
				.andExpect(status().isBadRequest());
	}

	@Test
	void 월_경계_12월_거래는_12월_목록에만_나온다() throws Exception {
		Integer december = createTransactionOn(expense, 7777, "2025-12-15");

		request(HttpMethod.GET, TRANSACTIONS + "?year=2025&month=12", token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.transNum == " + december + ")]").isNotEmpty());
		request(HttpMethod.GET, TRANSACTIONS + "?year=2025&month=1", token, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.transNum == " + december + ")]").isEmpty());
	}

	// ── 악의적 ───────────────────────────────

	@Test
	void 토큰_없이_조회_등록하면_401() throws Exception {
		request(HttpMethod.GET, TRANSACTIONS + TRANS_MONTH_QUERY, null, null)
				.andExpect(status().isUnauthorized());
		request(HttpMethod.POST, TRANSACTIONS, null, transactionBody(expense, 5000, "x"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 남의_거래를_수정_삭제하면_403() throws Exception {
		String otherToken = signupAndLogin("it-trans-other@test.com");
		Integer othersCategory = createCategory(otherToken, "QA타인지출", "E", null);
		Integer othersTrans = createTransaction(otherToken, othersCategory, 9999, "타인");

		request(HttpMethod.PUT, TRANSACTIONS + "/" + othersTrans + "?version=0", token, transactionBody(expense, 1, "x"))
				.andExpect(status().isForbidden());
		request(HttpMethod.DELETE, TRANSACTIONS + "/" + othersTrans + "?version=0", token, null)
				.andExpect(status().isForbidden());
	}

	@Test
	void 남의_카테고리로_등록하거나_내_거래를_옮기면_403() throws Exception {
		String otherToken = signupAndLogin("it-trans-other@test.com");
		Integer othersCategory = createCategory(otherToken, "QA타인지출", "E", null);
		Integer mine = createTransaction(token, expense, 12000, "점심");

		request(HttpMethod.POST, TRANSACTIONS, token, transactionBody(othersCategory, 5000, "x"))
				.andExpect(status().isForbidden());
		request(HttpMethod.PUT, TRANSACTIONS + "/" + mine + "?version=0", token, transactionBody(othersCategory, 5000, "x"))
				.andExpect(status().isForbidden());
	}

	@Test
	void 삭제된_거래를_수정_삭제하면_404() throws Exception {
		Integer trans = createTransaction(token, income, 3_000_000, "월급");
		request(HttpMethod.DELETE, TRANSACTIONS + "/" + trans + "?version=0", token, null)
				.andExpect(status().isNoContent());

		request(HttpMethod.PUT, TRANSACTIONS + "/" + trans + "?version=1", token, transactionBody(expense, 5000, "x"))
				.andExpect(status().isNotFound());
		request(HttpMethod.DELETE, TRANSACTIONS + "/" + trans + "?version=1", token, null)
				.andExpect(status().isNotFound());
	}

	@Test
	void 옛_version으로_수정_삭제하면_409() throws Exception {
		Integer trans = createTransaction(token, expense, 12000, "점심");
		request(HttpMethod.PUT, TRANSACTIONS + "/" + trans + "?version=0", token, transactionBody(expense, 15000, "수정"))
				.andExpect(status().isOk());

		request(HttpMethod.PUT, TRANSACTIONS + "/" + trans + "?version=0", token, transactionBody(expense, 5000, "x"))
				.andExpect(status().isConflict());
		request(HttpMethod.DELETE, TRANSACTIONS + "/" + trans + "?version=99", token, null)
				.andExpect(status().isConflict());
	}

	@Test
	void 본문에_userNum_transType_version을_넣어도_무시한다() throws Exception {
		request(HttpMethod.POST, TRANSACTIONS, token, """
				{"categoryNum":%d,"transAmount":5000,"transDate":"%s","userNum":99999,"transType":"I","version":77}
				""".formatted(expense, TRANS_DATE))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.transType").value("E"))
				.andExpect(jsonPath("$.version").value(0));
	}

	// 날짜를 골라 등록 — 월 경계 장면용
	private Integer createTransactionOn(Integer categoryNum, long amount, String date) throws Exception {
		return read(request(HttpMethod.POST, TRANSACTIONS, token, """
				{"categoryNum":%d,"transAmount":%d,"transDate":"%s"}
				""".formatted(categoryNum, amount, date))
				.andExpect(status().isCreated()), "$.transNum");
	}
}
