package com.kyumin.dotoree.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.kyumin.dotoree.domain.User;
import com.kyumin.dotoree.mapper.UserMapper;

// 셸 QA(qa/test-*.sh) → JUnit 통합 테스트 번역의 공통 부품 — _lib.sh 의 login · req · jnum 자리
// 진짜 DB(CI build 잡의 MySQL, 로컬은 맥 MySQL)를 쓰고 @Transactional 이라 테스트마다 되돌린다 → 계정도 테스트 안에서 가입해 쓴다(QA 의 test@test.com 전제가 없다)
// 메일은 보내지 않는다 — RecordingEmailSender 가 받아 두고 테스트가 거기서 인증 코드를 꺼낸다 (ADR-068)
@SpringBootTest(properties = "dotoree.mail.sender=log")
@Import(RecordingEmailSender.class)
@Transactional
public abstract class IntegrationTestSupport {

	protected static final String PASSWORD = "Qarace12!";

	// 거래 날짜는 지난 날 하나로 고정 — 오늘로 하면 월말·월초에 조회 달이 어긋난다. 미래는 @PastOrPresent 로 막힌다
	protected static final String TRANS_DATE = "2026-10-01";
	protected static final String TRANS_MONTH_QUERY = "?year=2026&month=10";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	protected RecordingEmailSender mailbox;

	@Autowired
	private JdbcTemplate supportJdbcTemplate;

	@Autowired
	private UserMapper supportUserMapper;

	protected MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		// 응답을 UTF-8 로 읽는다 — 기본(ISO-8859-1)이면 한글 이름 비교가 깨진다
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.apply(springSecurity())
				.defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
				.build();
	}

	// 가입 → Access 토큰. 가입하면 바로 로그인된다(ADR-068) · '미분류' 지출·수입과 '현금' 계좌가 같이 생긴다(UserService)
	protected String signupAndLogin(String email) throws Exception {
		return read(signupRequest(email).andExpect(status().isCreated()), "$.accessToken");
	}

	// ── 가입 · 인증 코드 부품 (ADR-068) ─────────────────

	// 코드 받기(204) → 테스트 메일함에서 코드. purpose "S" 가입 · "P" 비밀번호 재설정
	protected String emailCode(String email, String purpose) throws Exception {
		mailbox.clear(email);
		mockMvc.perform(post("/api/auth/email-codes").contentType(MediaType.APPLICATION_JSON)
				.content(emailCodeBody(email, purpose)))
				.andExpect(status().isNoContent());
		return mailbox.lastCode(email);
	}

	protected static String emailCodeBody(String email, String purpose) {
		return "{\"email\":\"" + email + "\",\"purpose\":\"" + purpose + "\"}";
	}

	protected static String signupBody(String email, String code) {
		return """
				{"email":"%s","code":"%s","password":"%s","nickname":"dotoree"}
				""".formatted(email, code, PASSWORD);
	}

	// 가입 코드를 받아 바로 가입 요청 — 기대 상태는 부르는 쪽이 본다
	protected ResultActions signupRequest(String email) throws Exception {
		String code = emailCode(email, "S");
		return mockMvc.perform(post("/api/users/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody(email, code)));
	}

	// 가입(201) → 사용자 번호
	protected Integer signup(String email) throws Exception {
		return read(signupRequest(email).andExpect(status().isCreated()), "$.userNum");
	}

	// 커밋된 계정 지우기 — 테스트 트랜잭션을 끈 클래스(NOT_SUPPORTED)가 앞뒤로 부른다. 파기(UserService.purgeUser)와 같은 FK 순서, 상태('A'·'W') 상관없이
	// 인증 코드 줄은 계정이 없어도 지운다 — 남으면 다음 실행이 60초 · 24시간 5번에 걸린다
	protected void deleteCommittedAccount(String email) {
		supportJdbcTemplate.update("DELETE FROM auth_verifications WHERE email = ?", email);
		User user = supportUserMapper.findByEmail(email);
		if (user == null) {
			return;
		}
		Integer target = user.getUserNum();
		supportUserMapper.deleteOccurrencesOf(target);
		supportUserMapper.deleteRecurringRulesOf(target);
		supportUserMapper.deleteTransactionsOf(target);
		supportUserMapper.deleteTransfersOf(target);
		supportUserMapper.deleteAdjustmentsOf(target);
		supportUserMapper.deleteAccountsOf(target);
		supportUserMapper.deleteChildCategoriesOf(target);
		supportUserMapper.deleteCategoriesOf(target);
		supportUserMapper.deleteRefreshTokensOf(target);
		// deleteUser 는 'W'(탈퇴 유예) 계정만 지운다(파기 안전장치) — 활성 계정도 지워야 해서 여기만 직접
		supportJdbcTemplate.update("DELETE FROM users WHERE user_num = ?", target);
	}

	// 토큰을 실은 요청 — body 가 null 이면 본문 없이
	protected ResultActions request(HttpMethod method, String path, String token, String body) throws Exception {
		MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.request(method, path);
		if (token != null) {
			builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
		}
		if (body != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(builder);
	}

	// 응답 본문에서 값 하나 — jsonPath 예) "$.categoryNum"
	protected static <T> T read(ResultActions result, String jsonPath) throws Exception {
		String body = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return JsonPath.read(body, jsonPath);
	}

	// ── 카테고리 · 거래 부품 — 여러 묶음이 같이 쓴다 ──────────────

	protected static String categoryBody(String name, String type, Integer parentCategoryNum) {
		String parent = parentCategoryNum == null ? "" : ",\"parentCategoryNum\":" + parentCategoryNum;
		return "{\"categoryName\":\"" + name + "\",\"categoryType\":\"" + type + "\"" + parent + "}";
	}

	protected Integer createCategory(String token, String name, String type, Integer parentCategoryNum) throws Exception {
		return read(request(HttpMethod.POST, "/api/categories", token, categoryBody(name, type, parentCategoryNum))
				.andExpect(status().isCreated()), "$.categoryNum");
	}

	// 가입 때 생긴 '미분류' 번호 — type 은 "E"(지출) · "I"(수입)
	protected Integer uncategorized(String token, String type) throws Exception {
		List<Integer> found = read(request(HttpMethod.GET, "/api/categories", token, null)
				.andExpect(status().isOk()), "$[?(@.categoryName == '미분류' && @.categoryType == '" + type + "')].categoryNum");
		return found.get(0);
	}

	protected static String transactionBody(Integer categoryNum, long amount, String memo) {
		return """
				{"categoryNum":%d,"transAmount":%d,"transDate":"%s","transMemo":"%s"}
				""".formatted(categoryNum, amount, TRANS_DATE, memo);
	}

	protected Integer createTransaction(String token, Integer categoryNum, long amount, String memo) throws Exception {
		return read(request(HttpMethod.POST, "/api/transactions", token, transactionBody(categoryNum, amount, memo))
				.andExpect(status().isCreated()), "$.transNum");
	}

	// TRANS_DATE 달의 거래 목록 (삭제된 거래는 안 나온다)
	protected List<Map<String, Object>> monthTransactions(String token) throws Exception {
		return read(request(HttpMethod.GET, "/api/transactions" + TRANS_MONTH_QUERY, token, null)
				.andExpect(status().isOk()), "$");
	}

	// 목록에서 거래 하나 — 없으면 null
	protected Map<String, Object> findTransaction(String token, Integer transNum) throws Exception {
		return monthTransactions(token).stream()
				.filter(row -> transNum.equals(row.get("transNum")))
				.findFirst()
				.orElse(null);
	}

	// ── 계좌 부품 (ADR-065) ─────────────────────────

	// 계좌의 시작 날짜 — TRANS_DATE 보다 앞으로 고정해, 테스트가 적는 거래가 잔액에 다 들어가게 (시작 날짜 전 거래는 빠진다, ADR-065 ⑦)
	protected static final String OPENING_DATE = "2026-09-01";

	protected static String accountBody(String name, String type, long openingBalance, Long creditLimit) {
		String limit = creditLimit == null ? "" : ",\"creditLimit\":" + creditLimit;
		return "{\"accountName\":\"" + name + "\",\"accountType\":\"" + type + "\",\"openingBalance\":" + openingBalance
				+ ",\"openingDate\":\"" + OPENING_DATE + "\"" + limit + "}";
	}

	protected Integer createAccount(String token, String name, String type, long openingBalance) throws Exception {
		return read(request(HttpMethod.POST, "/api/accounts", token, accountBody(name, type, openingBalance, null))
				.andExpect(status().isCreated()), "$.accountNum");
	}

	// 가입 때 생긴 '현금' 기본 계좌 번호
	protected Integer defaultAccount(String token) throws Exception {
		List<Integer> found = read(request(HttpMethod.GET, "/api/accounts", token, null)
				.andExpect(status().isOk()), "$[?(@.isDefault == true)].accountNum");
		return found.get(0);
	}

	// 계좌 목록 API 가 계산한 지금 잔액
	protected long balanceOf(String token, Integer accountNum) throws Exception {
		List<Number> found = read(request(HttpMethod.GET, "/api/accounts", token, null)
				.andExpect(status().isOk()), "$[?(@.accountNum == " + accountNum + ")].balance");
		return found.get(0).longValue();
	}
}
