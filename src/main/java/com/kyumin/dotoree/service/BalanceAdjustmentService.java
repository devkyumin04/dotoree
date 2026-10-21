package com.kyumin.dotoree.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kyumin.dotoree.domain.Account;
import com.kyumin.dotoree.domain.BalanceAdjustment;
import com.kyumin.dotoree.dto.BalanceAdjustmentResponseDto;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mapper.AccountMapper;
import com.kyumin.dotoree.mapper.BalanceAdjustmentMapper;

import lombok.RequiredArgsConstructor;

// 잔액 맞추기 — 실제 잔액을 받아 앱 잔액과의 차이만큼 보정 한 줄 (ADR-066)
// 계좌 검사(내 것 · 열린 것)는 AccountService 한 곳 — 거래 · 이체와 같은 규칙
@Service
@RequiredArgsConstructor
public class BalanceAdjustmentService {

	private final BalanceAdjustmentMapper adjustmentMapper;
	private final AccountMapper accountMapper;
	private final AccountService accountService;
	private final Clock clock;

	// 보정 날짜는 오늘 — "지금 은행 앱에 보이는 잔액"으로 맞추는 것이라서. 차이가 0 이면 아무것도 안 넣고 null
	@Transactional
	public BalanceAdjustmentResponseDto adjust(Integer userNum, Integer accountNum, long actualBalance) {
		Account account = accountService.accountForTransaction(userNum, accountNum);
		long difference = actualBalance - accountMapper.calculateBalance(accountNum, null);
		if (difference == 0) {
			return null;
		}

		BalanceAdjustment adjustment = new BalanceAdjustment();
		adjustment.setUserNum(userNum);
		adjustment.setAccountNum(accountNum);
		adjustment.setAdjustmentAmount(difference);
		adjustment.setAdjustmentDate(LocalDate.now(clock));
		adjustmentMapper.insertAdjustment(adjustment);

		BalanceAdjustmentResponseDto responseDto = new BalanceAdjustmentResponseDto();
		responseDto.setAdjustmentNum(adjustment.getAdjustmentNum());
		responseDto.setAccountNum(accountNum);
		responseDto.setAccountName(account.getAccountName());
		responseDto.setAdjustmentAmount(difference);
		responseDto.setAdjustmentDate(adjustment.getAdjustmentDate());
		return responseDto;
	}

	public List<BalanceAdjustmentResponseDto> getAdjustments(Integer userNum, int year, int month) {
		LocalDate startDate = LocalDate.of(year, month, 1);
		return adjustmentMapper.findByMonth(userNum, startDate, startDate.plusMonths(1));
	}

	// 지우기 — 계좌가 열려 있을 때만. 닫힌 계좌는 잔액 0 으로 닫혔으니, 보정을 지우면 닫힌 계좌 잔액이 0 에서 벗어난다
	public void deleteAdjustment(Integer userNum, Integer adjustmentNum) {
		BalanceAdjustment adjustment = adjustmentMapper.findByAdjustmentNum(adjustmentNum);
		if (adjustment == null) {
			throw new BusinessException(ErrorCode.ADJUSTMENT_NOT_FOUND);
		}
		if (!adjustment.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.ADJUSTMENT_ACCESS_DENIED);
		}
		accountService.accountForTransaction(userNum, adjustment.getAccountNum());
		adjustmentMapper.deleteAdjustment(adjustmentNum);
	}
}
