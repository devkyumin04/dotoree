package com.kyumin.dotoree.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

// 가입 형식 검사 (@Valid 가 쓰는 규칙) — 어떤 칸이 거절되는지만 본다 (단위 테스트, 스프링 없이)
class SignupRequestDtoTest {

	private static Validator validator;

	@BeforeAll
	static void setUp() {
		validator = Validation.buildDefaultValidatorFactory().getValidator();
	}

	private static SignupRequestDto signup(String email, String password, String nickname) {
		return signup(email, "012345", password, nickname);
	}

	private static SignupRequestDto signup(String email, String code, String password, String nickname) {
		SignupRequestDto dto = new SignupRequestDto();
		dto.setEmail(email);
		dto.setCode(code);
		dto.setPassword(password);
		dto.setNickname(nickname);
		return dto;
	}

	// 거절된 칸 이름들 (예: [password])
	private static Set<String> rejectedFields(SignupRequestDto dto) {
		return validator.validate(dto).stream()
				.map(violation -> violation.getPropertyPath().toString())
				.collect(Collectors.toSet());
	}

	@Test
	void 올바른_값은_통과한다() {
		assertThat(rejectedFields(signup("a@test.com", "Qarace12!", "dotoree"))).isEmpty();
	}

	@Test
	void 이메일_형식이_아니면_거절한다() {
		assertThat(rejectedFields(signup("not-an-email", "Qarace12!", "dotoree"))).containsExactly("email");
	}

	@Test
	void 비밀번호가_8자보다_짧으면_거절한다() {
		assertThat(rejectedFields(signup("a@test.com", "Qa1!", "dotoree"))).containsExactly("password");
	}

	@Test
	void 비밀번호가_20자보다_길면_거절한다() {
		String password21 = "Qa1!" + "a".repeat(17);

		assertThat(rejectedFields(signup("a@test.com", password21, "dotoree"))).containsExactly("password");
	}

	@Test
	void 비밀번호에_특수문자가_없으면_거절한다() {
		assertThat(rejectedFields(signup("a@test.com", "Qarace123", "dotoree"))).containsExactly("password");
	}

	@Test
	void 비밀번호에_숫자가_없으면_거절한다() {
		assertThat(rejectedFields(signup("a@test.com", "Qarace!!x", "dotoree"))).containsExactly("password");
	}

	@Test
	void 닉네임이_공백뿐이면_거절한다() {
		assertThat(rejectedFields(signup("a@test.com", "Qarace12!", "  "))).containsExactly("nickname");
	}

	// 인증 코드 (ADR-068) — 숫자 6자리만. 앞자리 0 도 6자리로 온다(위 기본값 012345)
	@Test
	void 인증_코드가_6자리_숫자가_아니면_거절한다() {
		assertThat(rejectedFields(signup("a@test.com", "12345", "Qarace12!", "dotoree"))).containsExactly("code");
		assertThat(rejectedFields(signup("a@test.com", "12345a", "Qarace12!", "dotoree"))).containsExactly("code");
		assertThat(rejectedFields(signup("a@test.com", null, "Qarace12!", "dotoree"))).containsExactly("code");
	}
}
