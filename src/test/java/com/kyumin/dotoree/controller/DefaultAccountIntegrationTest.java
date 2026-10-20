package com.kyumin.dotoree.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.kyumin.dotoree.support.IntegrationTestSupport;

// 욕조 ① 테이블 — 가입하면 '현금' 기본 계좌가 생기고, 거래는 그 계좌로 저장된다 (ADR-065, 통합 테스트)
// 계좌 API 는 다음 PR 이라 DB 를 직접 본다
class DefaultAccountIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 가입하면_현금_기본_계좌가_하나_생긴다() throws Exception {
		String token = signupAndLogin("it-account@test.com");
		Integer userNum = userNum("it-account@test.com");

		List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
				"SELECT account_name, account_type, opening_balance, is_default_yn, use_yn FROM accounts WHERE user_num = ?", userNum);

		assertThat(token).isNotBlank();
		assertThat(accounts).hasSize(1);
		Map<String, Object> cash = accounts.get(0);
		assertThat(cash.get("account_name")).isEqualTo("현금");
		assertThat(cash.get("account_type")).isEqualTo("C");
		assertThat(((Number) cash.get("opening_balance")).longValue()).isZero();
		assertThat(cash.get("is_default_yn")).isEqualTo("Y");
		assertThat(cash.get("use_yn")).isEqualTo("Y");
	}

	@Test
	void 거래는_기본_계좌로_저장된다() throws Exception {
		String token = signupAndLogin("it-account@test.com");
		Integer userNum = userNum("it-account@test.com");
		Integer category = createCategory(token, "QA계좌지출", "E", null);

		Integer trans = createTransaction(token, category, 12000, "점심");

		Integer accountNum = jdbcTemplate.queryForObject(
				"SELECT account_num FROM personal_transactions WHERE trans_num = ?", Integer.class, trans);
		Integer defaultAccount = jdbcTemplate.queryForObject(
				"SELECT account_num FROM accounts WHERE user_num = ? AND is_default_yn = 'Y'", Integer.class, userNum);
		assertThat(accountNum).isEqualTo(defaultAccount);
	}

	private Integer userNum(String email) {
		return jdbcTemplate.queryForObject("SELECT user_num FROM users WHERE user_email = ?", Integer.class, email);
	}
}
