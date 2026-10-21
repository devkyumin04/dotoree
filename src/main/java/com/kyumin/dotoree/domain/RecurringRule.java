package com.kyumin.dotoree.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 반복 규칙 — "매달 25일 월세 500,000 · 국민통장" (욕조 ② — ADR-067)
// 예정은 저장하지 않고 이 규칙에서 계산한다 — 날짜 규칙(31일 없는 달 · 2/29)은 여기 한 곳
@Getter
@Setter
@NoArgsConstructor
public class RecurringRule {

	public static final String MONTHLY = "M";
	public static final String YEARLY = "Y";

	private Integer ruleNum;
	private Integer userNum;
	private Integer categoryNum;
	private Integer accountNum;
	private Integer fromAccountNum;
	private Integer toAccountNum;
	private Long ruleAmount;
	private String ruleMemo;
	private String repeatType;
	private Integer repeatMonth;
	private Integer repeatDay;
	private LocalDate startDate;
	private LocalDate endDate;
	private String useYn;
	private LocalDateTime createdAt;

	public boolean isTransfer() {
		return fromAccountNum != null;
	}

	// from ~ until 사이(둘 다 포함)의 예정일 — 시작일 · 끝일 밖은 뺀다. 오래된 날 → 최근 날
	public List<LocalDate> occurrencesBetween(LocalDate from, LocalDate until) {
		List<LocalDate> dates = new ArrayList<>();
		LocalDate first = from.isAfter(startDate) ? from : startDate;
		LocalDate last = (endDate != null && endDate.isBefore(until)) ? endDate : until;
		if (first.isAfter(last)) {
			return dates;
		}
		for (YearMonth ym = YearMonth.from(first); !ym.isAfter(YearMonth.from(last)); ym = ym.plusMonths(1)) {
			if (YEARLY.equals(repeatType) && ym.getMonthValue() != repeatMonth) {
				continue;
			}
			LocalDate date = dateIn(ym);
			if (!date.isBefore(first) && !date.isAfter(last)) {
				dates.add(date);
			}
		}
		return dates;
	}

	// 그날이 이 규칙의 예정일인가 — 확인 · 건너뛰기 요청이 아무 날짜나 보내지 못하게
	public boolean isScheduledOn(LocalDate date) {
		return occurrencesBetween(date, date).contains(date);
	}

	// 그 달의 예정일 — 31일 규칙은 30일까지인 달엔 30일, 2월엔 28·29일. 매년 2/29 도 평년엔 2/28 (10/22 확정)
	private LocalDate dateIn(YearMonth ym) {
		return ym.atDay(Math.min(repeatDay, ym.lengthOfMonth()));
	}
}
