package com.kyumin.dotoree.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kyumin.dotoree.domain.Account;
import com.kyumin.dotoree.domain.PersonalCategory;
import com.kyumin.dotoree.domain.RecurringOccurrence;
import com.kyumin.dotoree.domain.RecurringRule;
import com.kyumin.dotoree.dto.AccountResponseDto;
import com.kyumin.dotoree.dto.ForecastResponseDto;
import com.kyumin.dotoree.dto.OccurrenceRequestDto;
import com.kyumin.dotoree.dto.OccurrenceResponseDto;
import com.kyumin.dotoree.dto.PendingOccurrenceDto;
import com.kyumin.dotoree.dto.PersonalTransactionRequestDto;
import com.kyumin.dotoree.dto.RecurringRuleRequestDto;
import com.kyumin.dotoree.dto.RecurringRuleResponseDto;
import com.kyumin.dotoree.dto.TransferRequestDto;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mapper.AccountMapper;
import com.kyumin.dotoree.mapper.PersonalCategoryMapper;
import com.kyumin.dotoree.mapper.RecurringMapper;

import lombok.RequiredArgsConstructor;

// 반복 예정 — 규칙 · 남은 예정 · 확인 · 건너뛰기 · 예상 잔액 (욕조 ② — ADR-067)
// 예정은 저장하지 않고 규칙에서 계산한다. 확인하면 그때 거래 · 이체가 생긴다(자동 생성 안 함 — 10/21)
// 거래 · 이체를 만드는 규칙(계좌 · 카테고리 검사)은 기존 서비스를 그대로 부른다 — 반복으로 만든 거래도 손으로 적은 거래와 같은 길
@Service
@RequiredArgsConstructor
public class RecurringService {

	private final RecurringMapper recurringMapper;
	private final PersonalCategoryMapper personalCategoryMapper;
	private final AccountMapper accountMapper;
	private final AccountService accountService;
	private final PersonalTransactionService personalTransactionService;
	private final TransferService transferService;
	private final Clock clock;

	// 남은 예정 · 예상 잔액을 너무 멀리까지 계산하지 않게 — 기본은 다음 달 말(10/22 확정), 많아야 2년
	private static final int MAX_FORECAST_YEARS = 2;

	// ───── 규칙

	public RecurringRuleResponseDto createRule(Integer userNum, RecurringRuleRequestDto requestDto) {
		LocalDate today = LocalDate.now(clock);
		RecurringRule rule = new RecurringRule();
		rule.setUserNum(userNum);
		applyTarget(userNum, rule, requestDto);
		applySchedule(rule, requestDto);

		LocalDate start = requestDto.getStartDate() != null ? requestDto.getStartDate() : today;
		if (start.isBefore(today)) {
			throw new BusinessException(ErrorCode.RECURRING_START_IN_PAST);
		}
		rule.setStartDate(start);
		applyEndDate(rule, requestDto.getEndDate());

		recurringMapper.insertRule(rule);
		return findRuleResponse(userNum, rule.getRuleNum());
	}

	public List<RecurringRuleResponseDto> getRules(Integer userNum) {
		return recurringMapper.findRules(userNum);
	}

	// 고치면 남은 예정만 바뀐다 — 이미 확인한 거래 · 이체는 그대로
	public RecurringRuleResponseDto updateRule(Integer userNum, Integer ruleNum, RecurringRuleRequestDto requestDto) {
		RecurringRule rule = findOwnedRule(userNum, ruleNum);
		applyTarget(userNum, rule, requestDto);
		applySchedule(rule, requestDto);

		LocalDate start = requestDto.getStartDate();
		if (start != null && !start.equals(rule.getStartDate())) {
			if (start.isBefore(LocalDate.now(clock))) {
				throw new BusinessException(ErrorCode.RECURRING_START_IN_PAST);
			}
			rule.setStartDate(start);
		}
		applyEndDate(rule, requestDto.getEndDate());

		recurringMapper.updateRule(rule);
		return findRuleResponse(userNum, ruleNum);
	}

	// 지우면 남은 예정이 사라진다. 이미 확인한 거래 · 이체는 그대로
	public void deleteRule(Integer userNum, Integer ruleNum) {
		findOwnedRule(userNum, ruleNum);
		recurringMapper.deleteRule(ruleNum);
	}

	// ───── 남은 예정

	// 시작일부터 until(비우면 다음 달 말)까지 계산한 예정 − 처리한 것. 밀린 것(오늘 전)도 남는다. 날짜순
	public List<PendingOccurrenceDto> getPending(Integer userNum, LocalDate until) {
		LocalDate today = LocalDate.now(clock);
		LocalDate limit = limitOf(today, until);
		Set<String> handled = handledKeys(userNum);

		List<PendingOccurrenceDto> result = new ArrayList<>();
		for (RecurringRuleResponseDto ruleDto : recurringMapper.findRules(userNum)) {
			RecurringRule rule = ruleDto.toRule();
			for (LocalDate date : rule.occurrencesBetween(rule.getStartDate(), limit)) {
				if (!handled.contains(key(rule.getRuleNum(), date))) {
					result.add(new PendingOccurrenceDto(date, date.isBefore(today), ruleDto));
				}
			}
		}
		result.sort(Comparator.comparing(PendingOccurrenceDto::getScheduledDate)
				.thenComparing(p -> p.getRule().getRuleNum()));
		return result;
	}

	// 확인 — 거래 · 이체를 만들고 "처리함"을 남긴다. 한 트랜잭션이라 하나가 실패하면 둘 다 없던 일
	// 금액 · 날짜는 고칠 수 있다. 비우면 규칙 금액 · 예정일(예정일이 미래면 오늘 — 미리 낸 것)
	@Transactional
	public OccurrenceResponseDto confirm(Integer userNum, Integer ruleNum, OccurrenceRequestDto requestDto) {
		RecurringRule rule = findOwnedRule(userNum, ruleNum);
		LocalDate scheduled = requestDto.getScheduledDate();
		validateOpenOccurrence(userNum, rule, scheduled);

		LocalDate today = LocalDate.now(clock);
		LocalDate actualDate = requestDto.getActualDate() != null ? requestDto.getActualDate()
				: (scheduled.isAfter(today) ? today : scheduled);
		long amount = requestDto.getAmount() != null ? requestDto.getAmount() : rule.getRuleAmount();

		RecurringOccurrence occurrence = newOccurrence(userNum, rule, scheduled, RecurringOccurrence.CONFIRMED);
		if (rule.isTransfer()) {
			TransferRequestDto transfer = new TransferRequestDto();
			transfer.setFromAccountNum(rule.getFromAccountNum());
			transfer.setToAccountNum(rule.getToAccountNum());
			transfer.setTransferAmount(amount);
			transfer.setTransferDate(actualDate);
			transfer.setTransferMemo(rule.getRuleMemo());
			occurrence.setTransferNum(transferService.createTransfer(userNum, transfer).getTransferNum());
		} else {
			PersonalTransactionRequestDto transaction = new PersonalTransactionRequestDto();
			transaction.setCategoryNum(rule.getCategoryNum());
			transaction.setAccountNum(rule.getAccountNum());
			transaction.setTransAmount(amount);
			transaction.setTransDate(actualDate);
			transaction.setTransMemo(rule.getRuleMemo());
			occurrence.setTransNum(personalTransactionService.createTransaction(userNum, transaction).getTransNum());
		}
		insertOccurrence(occurrence);
		return toResponse(occurrence);
	}

	// 건너뛰기 — 이번만 안 나갈 때(미리 냈다 등). 그 예정만 사라진다
	public OccurrenceResponseDto skip(Integer userNum, Integer ruleNum, OccurrenceRequestDto requestDto) {
		RecurringRule rule = findOwnedRule(userNum, ruleNum);
		validateOpenOccurrence(userNum, rule, requestDto.getScheduledDate());

		RecurringOccurrence occurrence = newOccurrence(userNum, rule, requestDto.getScheduledDate(), RecurringOccurrence.SKIPPED);
		insertOccurrence(occurrence);
		return toResponse(occurrence);
	}

	// ───── 예상 잔액

	// 지금 잔액 + 남은 예정(밀린 것 포함)을 날짜순으로 — 합계 + 계좌별 가장 낮아지는 날 (10/22 B)
	// 같은 날 예정은 그날 합으로 더한 뒤 낮은 점을 본다 — 월급 · 월세가 같은 날이면 순서 때문에 잠깐 모자란 것처럼 보이지 않게
	public ForecastResponseDto forecast(Integer userNum, LocalDate until) {
		LocalDate today = LocalDate.now(clock);
		LocalDate limit = limitOf(today, until);
		List<AccountResponseDto> accounts = accountMapper.findOpenAccounts(userNum, null);

		Map<Integer, Long> running = new LinkedHashMap<>();
		Map<Integer, Long> lowest = new LinkedHashMap<>();
		Map<Integer, LocalDate> lowestDate = new LinkedHashMap<>();
		for (AccountResponseDto a : accounts) {
			running.put(a.getAccountNum(), a.getBalance());
			lowest.put(a.getAccountNum(), a.getBalance());
			lowestDate.put(a.getAccountNum(), today);
		}

		TreeMap<LocalDate, Map<Integer, Long>> byDay = new TreeMap<>();
		for (PendingOccurrenceDto p : getPending(userNum, limit)) {
			RecurringRuleResponseDto rule = p.getRule();
			Map<Integer, Long> day = byDay.computeIfAbsent(p.getScheduledDate(), d -> new LinkedHashMap<>());
			if ("T".equals(rule.getKind())) {
				day.merge(rule.getFromAccountNum(), -rule.getRuleAmount(), Long::sum);
				day.merge(rule.getToAccountNum(), rule.getRuleAmount(), Long::sum);
			} else {
				long signed = "I".equals(rule.getKind()) ? rule.getRuleAmount() : -rule.getRuleAmount();
				day.merge(rule.getAccountNum(), signed, Long::sum);
			}
		}
		byDay.forEach((date, deltas) -> deltas.forEach((accountNum, delta) -> {
			if (!running.containsKey(accountNum)) {
				return;     // 닫힌 계좌 — 닫을 때 그 계좌의 규칙도 끝냈으니 보통은 없다
			}
			long next = running.get(accountNum) + delta;
			running.put(accountNum, next);
			if (next < lowest.get(accountNum)) {
				lowest.put(accountNum, next);
				lowestDate.put(accountNum, date);
			}
		}));

		List<ForecastResponseDto.AccountForecast> result = new ArrayList<>();
		long currentTotal = 0;
		long forecastTotal = 0;
		for (AccountResponseDto a : accounts) {
			long low = lowest.get(a.getAccountNum());
			result.add(new ForecastResponseDto.AccountForecast(a.getAccountNum(), a.getAccountName(), a.getAccountType(),
					a.getBalance(), running.get(a.getAccountNum()), low, lowestDate.get(a.getAccountNum()),
					isShortage(a, low)));
			currentTotal += a.getBalance();
			forecastTotal += running.get(a.getAccountNum());
		}
		return new ForecastResponseDto(limit, currentTotal, forecastTotal, result);
	}

	// ───── 부품

	// 거래 규칙(카테고리 + 계좌) 또는 이체 규칙(보내는 · 받는 계좌) — 둘 중 하나만. 계좌 검사는 거래 · 이체와 같은 곳
	private void applyTarget(Integer userNum, RecurringRule rule, RecurringRuleRequestDto requestDto) {
		boolean isTransaction = requestDto.getCategoryNum() != null;
		boolean isTransfer = requestDto.getFromAccountNum() != null || requestDto.getToAccountNum() != null;
		if (isTransaction == isTransfer) {
			throw new BusinessException(ErrorCode.RECURRING_TARGET_INVALID);
		}

		if (isTransaction) {
			PersonalCategory category = personalCategoryMapper.findByCategoryNum(requestDto.getCategoryNum());
			if (category == null) {
				throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
			}
			if (!category.getUserNum().equals(userNum)) {
				throw new BusinessException(ErrorCode.CATEGORY_ACCESS_DENIED);
			}
			Account account = accountService.accountForTransaction(userNum, requestDto.getAccountNum());
			rule.setCategoryNum(category.getCategoryNum());
			rule.setAccountNum(account.getAccountNum());
			rule.setFromAccountNum(null);
			rule.setToAccountNum(null);
			return;
		}

		if (requestDto.getFromAccountNum() == null || requestDto.getToAccountNum() == null) {
			throw new BusinessException(ErrorCode.RECURRING_TARGET_INVALID);
		}
		Account from = accountService.accountForTransaction(userNum, requestDto.getFromAccountNum());
		Account to = accountService.accountForTransaction(userNum, requestDto.getToAccountNum());
		if (from.getAccountNum().equals(to.getAccountNum())) {
			throw new BusinessException(ErrorCode.TRANSFER_SAME_ACCOUNT);
		}
		rule.setCategoryNum(null);
		rule.setAccountNum(null);
		rule.setFromAccountNum(from.getAccountNum());
		rule.setToAccountNum(to.getAccountNum());
	}

	private static void applySchedule(RecurringRule rule, RecurringRuleRequestDto requestDto) {
		rule.setRuleAmount(requestDto.getRuleAmount());
		rule.setRuleMemo(requestDto.getRuleMemo());
		rule.setRepeatType(requestDto.getRepeatType());
		rule.setRepeatDay(requestDto.getRepeatDay());
		if (RecurringRule.YEARLY.equals(requestDto.getRepeatType())) {
			if (requestDto.getRepeatMonth() == null) {
				throw new BusinessException(ErrorCode.RECURRING_MONTH_REQUIRED);
			}
			rule.setRepeatMonth(requestDto.getRepeatMonth());
		} else {
			rule.setRepeatMonth(null);
		}
	}

	private static void applyEndDate(RecurringRule rule, LocalDate endDate) {
		if (endDate != null && endDate.isBefore(rule.getStartDate())) {
			throw new BusinessException(ErrorCode.RECURRING_END_BEFORE_START);
		}
		rule.setEndDate(endDate);
	}

	private RecurringRule findOwnedRule(Integer userNum, Integer ruleNum) {
		RecurringRule rule = recurringMapper.findRule(ruleNum);
		if (rule == null) {
			throw new BusinessException(ErrorCode.RECURRING_NOT_FOUND);
		}
		if (!rule.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.RECURRING_ACCESS_DENIED);
		}
		return rule;
	}

	private RecurringRuleResponseDto findRuleResponse(Integer userNum, Integer ruleNum) {
		return recurringMapper.findRules(userNum).stream()
				.filter(r -> r.getRuleNum().equals(ruleNum))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.RECURRING_NOT_FOUND));
	}

	// 아무 날짜나 확인 · 건너뛰기 못 하게 — 그 규칙의 예정일이고 아직 처리 안 한 것만
	private void validateOpenOccurrence(Integer userNum, RecurringRule rule, LocalDate scheduled) {
		if (!rule.isScheduledOn(scheduled)) {
			throw new BusinessException(ErrorCode.OCCURRENCE_NOT_SCHEDULED);
		}
		if (handledKeys(userNum).contains(key(rule.getRuleNum(), scheduled))) {
			throw new BusinessException(ErrorCode.OCCURRENCE_ALREADY_HANDLED);
		}
	}

	// 두 번 눌러 동시에 들어와도 (rule_num, scheduled_date) UNIQUE 가 한 줄만 받는다 — 진 쪽은 409, 트랜잭션째 되돌린다
	private void insertOccurrence(RecurringOccurrence occurrence) {
		try {
			recurringMapper.insertOccurrence(occurrence);
		} catch (DuplicateKeyException e) {
			throw new BusinessException(ErrorCode.OCCURRENCE_ALREADY_HANDLED);
		}
	}

	private Set<String> handledKeys(Integer userNum) {
		Set<String> keys = new HashSet<>();
		for (RecurringOccurrence o : recurringMapper.findOccurrences(userNum)) {
			keys.add(key(o.getRuleNum(), o.getScheduledDate()));
		}
		return keys;
	}

	private static String key(Integer ruleNum, LocalDate date) {
		return ruleNum + ":" + date;
	}

	private static RecurringOccurrence newOccurrence(Integer userNum, RecurringRule rule, LocalDate scheduled, String status) {
		RecurringOccurrence occurrence = new RecurringOccurrence();
		occurrence.setRuleNum(rule.getRuleNum());
		occurrence.setUserNum(userNum);
		occurrence.setScheduledDate(scheduled);
		occurrence.setOccurrenceStatus(status);
		return occurrence;
	}

	private static OccurrenceResponseDto toResponse(RecurringOccurrence o) {
		return new OccurrenceResponseDto(o.getRuleNum(), o.getScheduledDate(), o.getOccurrenceStatus(), o.getTransNum(), o.getTransferNum());
	}

	private static LocalDate limitOf(LocalDate today, LocalDate until) {
		LocalDate limit = until != null ? until : YearMonth.from(today).plusMonths(1).atEndOfMonth();
		LocalDate max = today.plusYears(MAX_FORECAST_YEARS);
		return limit.isAfter(max) ? max : limit;
	}

	// 현금 · 통장은 0 아래가 모자람, 마이너스통장 · 카드는 한도 밖이 모자람(한도를 안 적었으면 모자람 없음)
	private static boolean isShortage(AccountResponseDto account, long lowestBalance) {
		if (Account.canBeNegative(account.getAccountType())) {
			return account.getCreditLimit() != null && lowestBalance < -account.getCreditLimit();
		}
		return lowestBalance < 0;
	}
}
