package com.kyumin.dotoree.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

// 반복 규칙의 날짜 계산 — 31일 없는 달 · 2/29 · 시작 · 끝 (ADR-067, 단위 테스트)
class RecurringRuleTest {

	@Test
	void 매달_31일은_없는_달엔_말일() {
		RecurringRule rule = monthly(31, "2026-01-01", null);

		assertThat(rule.occurrencesBetween(d("2026-01-01"), d("2026-04-30")))
				.containsExactly(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30"));
	}

	@Test
	void 윤년_2월은_29일() {
		RecurringRule rule = monthly(31, "2028-02-01", null);

		assertThat(rule.occurrencesBetween(d("2028-02-01"), d("2028-02-29"))).containsExactly(d("2028-02-29"));
	}

	@Test
	void 매년_2월_29일은_평년엔_28일() {
		RecurringRule rule = yearly(2, 29, "2027-01-01", null);

		assertThat(rule.occurrencesBetween(d("2027-01-01"), d("2028-12-31")))
				.containsExactly(d("2027-02-28"), d("2028-02-29"));
	}

	@Test
	void 매년은_그_달에만() {
		RecurringRule rule = yearly(3, 15, "2026-01-01", null);

		assertThat(rule.occurrencesBetween(d("2026-01-01"), d("2027-12-31")))
				.containsExactly(d("2026-03-15"), d("2027-03-15"));
	}

	@Test
	void 시작일_전과_끝일_뒤는_없다() {
		RecurringRule rule = monthly(10, "2026-01-11", "2026-03-31");

		assertThat(rule.occurrencesBetween(d("2025-01-01"), d("2026-12-31")))
				.containsExactly(d("2026-02-10"), d("2026-03-10"));       // 1/10 은 시작 전, 4/10 은 끝 뒤
	}

	@Test
	void 범위가_비면_빈_목록() {
		RecurringRule rule = monthly(10, "2026-05-01", null);

		assertThat(rule.occurrencesBetween(d("2026-01-01"), d("2026-04-30"))).isEmpty();
	}

	@Test
	void 예정일인지() {
		RecurringRule rule = monthly(31, "2026-01-01", null);

		assertThat(rule.isScheduledOn(d("2026-02-28"))).isTrue();
		assertThat(rule.isScheduledOn(d("2026-02-27"))).isFalse();
		assertThat(rule.isScheduledOn(d("2025-12-31"))).isFalse();         // 시작 전
	}

	private static RecurringRule monthly(int day, String start, String end) {
		RecurringRule rule = new RecurringRule();
		rule.setRepeatType(RecurringRule.MONTHLY);
		rule.setRepeatDay(day);
		rule.setStartDate(d(start));
		rule.setEndDate(end == null ? null : d(end));
		return rule;
	}

	private static RecurringRule yearly(int month, int day, String start, String end) {
		RecurringRule rule = monthly(day, start, end);
		rule.setRepeatType(RecurringRule.YEARLY);
		rule.setRepeatMonth(month);
		return rule;
	}

	private static LocalDate d(String date) {
		return LocalDate.parse(date);
	}
}
