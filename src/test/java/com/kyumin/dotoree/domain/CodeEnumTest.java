package com.kyumin.dotoree.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

// 한 글자 코드 enum — DB 에 들어 있는 글자(V1 · V4 · V5 · V9)와 enum 이 어긋나지 않는지 (단위 테스트)
class CodeEnumTest {

	@Test
	void 코드로_enum을_찾는다() {
		assertThat(UserStatus.fromCode("A")).isEqualTo(UserStatus.ACTIVE);
		assertThat(UserStatus.fromCode("W")).isEqualTo(UserStatus.WITHDRAWN);
		assertThat(CategoryType.fromCode("E")).isEqualTo(CategoryType.EXPENSE);
		assertThat(CategoryType.fromCode("I")).isEqualTo(CategoryType.INCOME);
		assertThat(AccountType.fromCode("C")).isEqualTo(AccountType.CASH);
		assertThat(AccountType.fromCode("B")).isEqualTo(AccountType.BANK);
		assertThat(AccountType.fromCode("M")).isEqualTo(AccountType.OVERDRAFT);
		assertThat(AccountType.fromCode("K")).isEqualTo(AccountType.CARD);
		assertThat(RepeatType.fromCode("M")).isEqualTo(RepeatType.MONTHLY);
		assertThat(RepeatType.fromCode("Y")).isEqualTo(RepeatType.YEARLY);
		assertThat(OccurrenceStatus.fromCode("C")).isEqualTo(OccurrenceStatus.CONFIRMED);
		assertThat(OccurrenceStatus.fromCode("S")).isEqualTo(OccurrenceStatus.SKIPPED);
	}

	@Test
	void 없는_코드는_조용히_넘기지_않고_예외() {
		assertThatThrownBy(() -> UserStatus.fromCode("X")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> UserStatus.fromCode(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CategoryType.fromCode("a")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 한_enum_안에서_코드는_겹치지_않는다() {
		assertUnique(UserStatus.values());
		assertUnique(CategoryType.values());
		assertUnique(AccountType.values());
		assertUnique(RepeatType.values());
		assertUnique(OccurrenceStatus.values());
	}

	private static void assertUnique(CodeEnum[] values) {
		assertThat(Arrays.stream(values).map(CodeEnum::getCode)).doesNotHaveDuplicates();
	}

	@Test
	void 마이너스가_될_수_있는_계좌는_마이너스통장과_카드뿐() {
		assertThat(AccountType.CASH.canBeNegative()).isFalse();
		assertThat(AccountType.BANK.canBeNegative()).isFalse();
		assertThat(AccountType.OVERDRAFT.canBeNegative()).isTrue();
		assertThat(AccountType.CARD.canBeNegative()).isTrue();
	}
}
