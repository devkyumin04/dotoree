package com.kyumin.dotoree.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kyumin.dotoree.domain.Account;
import com.kyumin.dotoree.dto.AccountRequestDto;
import com.kyumin.dotoree.dto.AccountResponseDto;
import com.kyumin.dotoree.dto.BalanceSummaryResponseDto;
import com.kyumin.dotoree.dto.MonthlyBalanceDto;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mapper.AccountMapper;

import lombok.RequiredArgsConstructor;

// 계좌 규칙은 여기 한 곳 — 기본 계좌 · 등록 · 수정 · 닫기 · 거래가 쓸 계좌 고르기 (ADR-065)
// 잔액 계산식은 AccountMapper.xml 의 balanceOf 한 곳
@Service
@RequiredArgsConstructor
public class AccountService {

	private final AccountMapper accountMapper;
	private final Clock clock;

	// 월말 잔액 추이 — 통계의 수입 · 지출 추이와 같은 6개월
	private static final int TREND_MONTHS = 6;

	// 가입 때 '현금' 기본 계좌 — 가입 '미분류' 카테고리와 같은 자리. 부르는 쪽(가입)의 트랜잭션에 같이 묶인다
	public Integer createDefaultAccount(Integer userNum) {
		Account account = new Account();
		account.setUserNum(userNum);
		account.setAccountName(Account.DEFAULT_ACCOUNT_NAME);
		account.setAccountType(Account.TYPE_CASH);
		account.setOpeningBalance(0L);
		account.setOpeningDate(null);    // 시작 날짜 없음 = 처음부터 — 아무도 "그날 현금 0원"이라고 말한 적이 없다 (V6)
		account.setIsDefaultYn("Y");
		accountMapper.insertAccount(account);
		return account.getAccountNum();
	}

	// 기본 계좌. 없으면 만든다 —
	// V4 를 롤백했다 다시 올리면 그 사이 옛 jar 로 가입한 계정엔 기본 계좌가 없다(V4 는 적용 시점의 계정에만 만들었다)
	public Account defaultAccount(Integer userNum) {
		Account account = accountMapper.findDefaultAccount(userNum);
		if (account == null) {
			Integer accountNum = createDefaultAccount(userNum);
			return accountMapper.findByAccountNum(accountNum);
		}
		return account;
	}

	public AccountResponseDto createAccount(Integer userNum, AccountRequestDto requestDto) {
		validateBalanceRule(requestDto);
		validateUniqueName(userNum, requestDto.getAccountName(), null);

		Account account = new Account();
		account.setUserNum(userNum);
		account.setAccountName(requestDto.getAccountName());
		account.setAccountType(requestDto.getAccountType());
		account.setOpeningBalance(requestDto.getOpeningBalance());
		account.setCreditLimit(requestDto.getCreditLimit());
		// 시작 날짜 — 비우면 오늘. 시작 잔액은 "그날 기준 잔액" (ADR-065 ⑦)
		account.setOpeningDate(requestDto.getOpeningDate() != null ? requestDto.getOpeningDate() : LocalDate.now(clock));
		account.setIsDefaultYn("N");
		accountMapper.insertAccount(account);

		// 시작 날짜를 과거로 잡으면 그 뒤에 이미 적어 둔 거래가 있을 수 있다 — 계산 한 곳에 맡긴다
		return toResponse(account, accountMapper.calculateBalance(account.getAccountNum(), null));
	}

	public List<AccountResponseDto> getAccounts(Integer userNum) {
		return accountMapper.findOpenAccounts(userNum, null);
	}

	// 그날 기준 전체 잔액 합계 + 전날 대비. date 를 비우면 오늘
	public BalanceSummaryResponseDto getSummary(Integer userNum, LocalDate date) {
		LocalDate target = (date != null) ? date : LocalDate.now(clock);
		long total = accountMapper.totalBalance(userNum, target);
		long previous = accountMapper.totalBalance(userNum, target.minusDays(1));
		return new BalanceSummaryResponseDto(target, total, previous, total - previous);
	}

	// year-month 를 마지막으로 6개월, 각 달 말일 기준 전체 잔액 합계 (오래된 달 → 최근 달)
	public List<MonthlyBalanceDto> getMonthlyBalances(Integer userNum, int year, int month) {
		YearMonth last = YearMonth.of(year, month);
		List<MonthlyBalanceDto> result = new ArrayList<>();
		for (int i = TREND_MONTHS - 1; i >= 0; i--) {
			YearMonth target = last.minusMonths(i);
			result.add(new MonthlyBalanceDto(target.toString(), accountMapper.totalBalance(userNum, target.atEndOfMonth())));
		}
		return result;
	}

	public AccountResponseDto updateAccount(Integer userNum, Integer accountNum, AccountRequestDto requestDto) {
		Account account = findOpenOwnedAccount(userNum, accountNum);
		validateBalanceRule(requestDto);
		validateUniqueName(userNum, requestDto.getAccountName(), accountNum);

		account.setAccountName(requestDto.getAccountName());
		account.setAccountType(requestDto.getAccountType());
		account.setOpeningBalance(requestDto.getOpeningBalance());
		account.setCreditLimit(requestDto.getCreditLimit());
		if (requestDto.getOpeningDate() != null) {
			account.setOpeningDate(requestDto.getOpeningDate());
		}
		accountMapper.updateAccount(account);

		return toResponse(account, accountMapper.calculateBalance(accountNum, null));
	}

	// 닫기 — 잔액 0 일 때만. 거래 · 이체 기록은 이 계좌에 그대로 남는다 (ADR-065 ⑥)
	@Transactional
	public void closeAccount(Integer userNum, Integer accountNum) {
		Account account = findOpenOwnedAccount(userNum, accountNum);
		if (account.isDefault()) {
			throw new BusinessException(ErrorCode.DEFAULT_ACCOUNT_NOT_CLOSABLE);
		}
		if (accountMapper.calculateBalance(accountNum, null) != 0) {
			throw new BusinessException(ErrorCode.ACCOUNT_BALANCE_NOT_ZERO);
		}
		accountMapper.closeAccount(accountNum);
	}

	// 거래가 들어갈 계좌 — 비우면 기본 계좌. 고르면 내 것 · 열린 것만
	public Account accountForTransaction(Integer userNum, Integer accountNum) {
		if (accountNum == null) {
			return defaultAccount(userNum);
		}
		Account account = accountMapper.findByAccountNum(accountNum);
		if (account == null) {
			throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
		}
		if (!account.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.ACCOUNT_ACCESS_DENIED);
		}
		if (!account.isOpen()) {
			throw new BusinessException(ErrorCode.ACCOUNT_CLOSED);
		}
		return account;
	}

	// 거래 수정에서 계좌를 안 바꿀 때 — 지금 붙어 있는 계좌(닫힌 것이어도 기록은 그대로)
	public Account findAccount(Integer accountNum) {
		return accountMapper.findByAccountNum(accountNum);
	}

	// 수정 · 닫기 대상 — 닫힌 계좌는 없는 것으로(404)
	private Account findOpenOwnedAccount(Integer userNum, Integer accountNum) {
		Account account = accountMapper.findByAccountNum(accountNum);
		if (account == null || !account.isOpen()) {
			throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
		}
		if (!account.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.ACCOUNT_ACCESS_DENIED);
		}
		return account;
	}

	// 종류별 시작 잔액 · 한도 규칙 (10/20 확정)
	// 현금 · 통장 — 0 이상, 한도 없음 / 마이너스통장 · 카드 — −한도까지(한도를 안 적으면 제한 없음)
	private void validateBalanceRule(AccountRequestDto requestDto) {
		boolean canBeNegative = Account.canBeNegative(requestDto.getAccountType());
		Long creditLimit = requestDto.getCreditLimit();
		long openingBalance = requestDto.getOpeningBalance();

		if (!canBeNegative && creditLimit != null) {
			throw new BusinessException(ErrorCode.ACCOUNT_CREDIT_LIMIT_NOT_ALLOWED);
		}
		if (!canBeNegative && openingBalance < 0) {
			throw new BusinessException(ErrorCode.ACCOUNT_NEGATIVE_NOT_ALLOWED);
		}
		if (canBeNegative && creditLimit != null && openingBalance < -creditLimit) {
			throw new BusinessException(ErrorCode.ACCOUNT_OVER_CREDIT_LIMIT);
		}
	}

	// 열린 계좌끼리 같은 이름 금지 — 화면에서 고를 때 헷갈리지 않게. 닫힌 계좌 이름은 다시 쓸 수 있다
	private void validateUniqueName(Integer userNum, String accountName, Integer excludeAccountNum) {
		if (accountMapper.countOpenByName(userNum, accountName, excludeAccountNum) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE_ACCOUNT_NAME);
		}
	}

	private static AccountResponseDto toResponse(Account account, long balance) {
		AccountResponseDto responseDto = new AccountResponseDto();
		responseDto.setAccountNum(account.getAccountNum());
		responseDto.setAccountName(account.getAccountName());
		responseDto.setAccountType(account.getAccountType());
		responseDto.setOpeningBalance(account.getOpeningBalance());
		responseDto.setCreditLimit(account.getCreditLimit());
		responseDto.setOpeningDate(account.getOpeningDate());
		responseDto.setIsDefault(account.isDefault());
		responseDto.setBalance(balance);
		return responseDto;
	}
}
