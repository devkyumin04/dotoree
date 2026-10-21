package com.kyumin.dotoree.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.kyumin.dotoree.domain.Account;
import com.kyumin.dotoree.domain.Transfer;
import com.kyumin.dotoree.dto.TransferRequestDto;
import com.kyumin.dotoree.dto.TransferResponseDto;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mapper.TransferMapper;

import lombok.RequiredArgsConstructor;

// 이체 — 등록 · 월 목록 · 수정 · 삭제(낙관적 락). 통계에는 안 잡히고 두 계좌의 잔액만 옮긴다 (ADR-065)
// 계좌 검사(내 것 · 열린 것)는 AccountService 한 곳 — 거래와 같은 규칙
@Service
@RequiredArgsConstructor
public class TransferService {

	private final TransferMapper transferMapper;
	private final AccountService accountService;

	public TransferResponseDto createTransfer(Integer userNum, TransferRequestDto requestDto) {
		Account from = accountService.accountForTransaction(userNum, requestDto.getFromAccountNum());
		Account to = accountService.accountForTransaction(userNum, requestDto.getToAccountNum());
		validateDifferent(from, to);

		Transfer transfer = new Transfer();
		transfer.setUserNum(userNum);
		apply(transfer, requestDto);
		transferMapper.insertTransfer(transfer);

		return toResponse(transfer, 0, from, to);
	}

	public List<TransferResponseDto> getTransfers(Integer userNum, int year, int month) {
		LocalDate startDate = LocalDate.of(year, month, 1);
		return transferMapper.findByMonth(userNum, startDate, startDate.plusMonths(1));
	}

	// 수정 — 두 계좌 다 열려 있어야 한다. 닫힌 계좌는 잔액 0 으로 닫혔으니, 그 계좌가 낀 지난 이체를 고치면 닫힌 계좌 잔액이 0 에서 벗어난다
	public TransferResponseDto updateTransfer(Integer userNum, Integer transferNum, Integer version, TransferRequestDto requestDto) {
		Transfer transfer = findOwnedTransfer(userNum, transferNum);
		Account from = accountService.accountForTransaction(userNum, requestDto.getFromAccountNum());
		Account to = accountService.accountForTransaction(userNum, requestDto.getToAccountNum());
		validateDifferent(from, to);

		apply(transfer, requestDto);
		transfer.setVersion(version);
		if (transferMapper.updateTransfer(transfer) == 0) {
			throw new BusinessException(ErrorCode.TRANSFER_CONFLICT);
		}
		return toResponse(transfer, version + 1, from, to);
	}

	public void deleteTransfer(Integer userNum, Integer transferNum, Integer version) {
		findOwnedTransfer(userNum, transferNum);
		if (transferMapper.deleteTransfer(transferNum, version) == 0) {
			throw new BusinessException(ErrorCode.TRANSFER_CONFLICT);
		}
	}

	private Transfer findOwnedTransfer(Integer userNum, Integer transferNum) {
		Transfer transfer = transferMapper.findByTransferNum(transferNum);
		if (transfer == null) {
			throw new BusinessException(ErrorCode.TRANSFER_NOT_FOUND);
		}
		if (!transfer.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.TRANSFER_ACCESS_DENIED);
		}
		return transfer;
	}

	// DB 의 CHECK(from ≠ to)도 막지만, 500 이 아니라 400 으로 알려 주려고 앞에서 한 번
	private static void validateDifferent(Account from, Account to) {
		if (from.getAccountNum().equals(to.getAccountNum())) {
			throw new BusinessException(ErrorCode.TRANSFER_SAME_ACCOUNT);
		}
	}

	private static void apply(Transfer transfer, TransferRequestDto requestDto) {
		transfer.setFromAccountNum(requestDto.getFromAccountNum());
		transfer.setToAccountNum(requestDto.getToAccountNum());
		transfer.setTransferAmount(requestDto.getTransferAmount());
		transfer.setTransferDate(requestDto.getTransferDate());
		transfer.setTransferMemo(requestDto.getTransferMemo());
	}

	private static TransferResponseDto toResponse(Transfer transfer, int version, Account from, Account to) {
		TransferResponseDto responseDto = new TransferResponseDto();
		responseDto.setTransferNum(transfer.getTransferNum());
		responseDto.setVersion(version);
		responseDto.setFromAccountNum(from.getAccountNum());
		responseDto.setFromAccountName(from.getAccountName());
		responseDto.setToAccountNum(to.getAccountNum());
		responseDto.setToAccountName(to.getAccountName());
		responseDto.setTransferAmount(transfer.getTransferAmount());
		responseDto.setTransferDate(transfer.getTransferDate());
		responseDto.setTransferMemo(transfer.getTransferMemo());
		return responseDto;
	}
}
