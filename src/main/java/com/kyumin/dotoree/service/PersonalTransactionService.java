package com.kyumin.dotoree.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.kyumin.dotoree.domain.Account;
import com.kyumin.dotoree.domain.PersonalCategory;
import com.kyumin.dotoree.domain.PersonalTransaction;
import com.kyumin.dotoree.dto.PersonalTransactionRequestDto;
import com.kyumin.dotoree.dto.PersonalTransactionResponseDto;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mapper.PersonalCategoryMapper;
import com.kyumin.dotoree.mapper.PersonalTransactionMapper;

@Service
@RequiredArgsConstructor
public class PersonalTransactionService {

	// 규칙은 전부 여기 — Controller 는 HTTP 입출력만, Mapper 는 SQL 만
	// 설계: 예외는 ErrorCode 로 구분(ADR-062 · 035) · insert 직후 version 은 0 을 직접(ADR-036) · TRANS_TYPE 은 카테고리에서 파생(ADR-008) · 낙관적 락은 영향 행 0 이면 409(ADR-006)

	private final PersonalTransactionMapper personalTransactionMapper;

	private final AccountService accountService;
	private final PersonalCategoryMapper personalCategoryMapper;

	public List<PersonalTransactionResponseDto> getTransactions(Integer userNum, int year, int month) {
		LocalDate startDate = LocalDate.of(year, month, 1);
		LocalDate endDate = startDate.plusMonths(1);
		
		return personalTransactionMapper.findByMonth(userNum, startDate, endDate);
	}


	// 등록 — transType 은 요청에서 받지 않고 고른 카테고리에서 꺼낸다("지출 카테고리인데 수입으로 등록" 불일치를 막는다)
	// version 은 0 을 직접 넣는다 — INSERT 에 version 칸이 없어 DB DEFAULT 0 이 채우지만 useGeneratedKeys 는 PK 만 가져와
	// 객체의 version 은 null 로 남는다. 그대로 응답하면 {"version": null} 이 나가 등록 직후 수정이 ?version=null 로 깨진다
	public PersonalTransactionResponseDto createTransaction(Integer userNum,
			PersonalTransactionRequestDto requestDto) {
		
		PersonalCategory category = personalCategoryMapper.findByCategoryNum(requestDto.getCategoryNum());
		
		if(category == null) {
			throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
		}
		
		if(!category.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.CATEGORY_ACCESS_DENIED);
		}
		
		PersonalTransaction transaction = new PersonalTransaction();
		transaction.setCategoryNum(requestDto.getCategoryNum());
		transaction.setTransAmount(requestDto.getTransAmount());
		transaction.setTransDate(requestDto.getTransDate());
		transaction.setTransMemo(requestDto.getTransMemo());
		transaction.setUserNum(userNum);
		// 계좌 — 비우면 기본 '현금', 고르면 내 것 · 열린 것만 (ADR-065)
		Account account = accountService.accountForTransaction(userNum, requestDto.getAccountNum());
		transaction.setAccountNum(account.getAccountNum());
		transaction.setTransType(category.getCategoryType());
		transaction.setVersion(0);
		
		personalTransactionMapper.insertTransaction(transaction);
		
		PersonalTransactionResponseDto responseDto = new PersonalTransactionResponseDto();
		responseDto.setTransNum(transaction.getTransNum());
		responseDto.setVersion(transaction.getVersion());
		responseDto.setTransAmount(transaction.getTransAmount());
		responseDto.setTransDate(transaction.getTransDate());
		responseDto.setTransMemo(transaction.getTransMemo());
		responseDto.setTransType(transaction.getTransType());
		responseDto.setAccountNum(account.getAccountNum());
		responseDto.setAccountName(account.getAccountName());
		responseDto.setCategoryNum(category.getCategoryNum());
		
		responseDto.setCategoryName(category.getCategoryName());
		responseDto.setCategoryEmoji(category.getCategoryEmoji());
		
		return responseDto;
	}


	// 삭제(소프트 딜리트) — 거래가 있는 걸 위에서 확인했는데도 0행이면 version 조건만 안 맞은 것 = "내가 조회한 뒤 누가 먼저 고치거나 지웠다".
	// 예외가 아니라 조용히 0 이 오므로 이 숫자를 안 보면 감지할 방법이 없다
	public void deleteTransaction(Integer userNum, Integer transNum, Integer version) {
		
		PersonalTransaction transaction = personalTransactionMapper.findByTransNum(transNum);
		
		if(transaction == null) {
			throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND);
		}
		
		if(!transaction.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.TRANSACTION_ACCESS_DENIED);
		}
		
		int affected = personalTransactionMapper.deleteTransaction(transNum, version);
		
		if(affected == 0) {
			throw new BusinessException(ErrorCode.TRANSACTION_CONFLICT);
		}
		
	}


	// 수정 — 바꿀 카테고리도 내 것인지 검증한다
	// version 은 반드시 클라이언트가 들고 온 값으로 덮어쓴다. 조회한 객체의 version 은 DB 현재값이라 그대로 update 하면
	// WHERE version = 자기자신 이 되어 항상 성공 → 낙관적 락이 통째로 무력화된다(코드는 멀쩡해 보이고 단위 테스트도 통과한다)
	// 응답엔 version + 1 을 담는다 — DB 에서는 +1 됐지만 자바 객체엔 반영되지 않아, 안 담으면 프론트가 연속으로 수정할 수 없다
	public PersonalTransactionResponseDto updateTransaction(Integer userNum, Integer transNum,
			Integer version, PersonalTransactionRequestDto requestDto) {
		
		PersonalTransaction transaction = personalTransactionMapper.findByTransNum(transNum);
		
		if(transaction == null) {
			throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND);
		}
		
		if(!transaction.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.TRANSACTION_ACCESS_DENIED);
		}
		
		PersonalCategory category = personalCategoryMapper.findByCategoryNum(requestDto.getCategoryNum());
		
		if(category == null) {
			throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
		}
		
		if(!category.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.CATEGORY_ACCESS_DENIED);
		}
		
		// 계좌 — 고르면 내 것 · 열린 것만, 비우면 지금 계좌 그대로(닫힌 계좌의 지난 거래도 고칠 수 있다).
		// 지금 계좌가 비어 있으면(V4 롤백 사이 옛 jar 가 넣은 거래) 기본 계좌로 채운다
		Account account;
		if (requestDto.getAccountNum() != null) {
			account = accountService.accountForTransaction(userNum, requestDto.getAccountNum());
		} else if (transaction.getAccountNum() != null) {
			account = accountService.findAccount(transaction.getAccountNum());
		} else {
			account = accountService.defaultAccount(userNum);
		}
		
		transaction.setCategoryNum(requestDto.getCategoryNum());
		transaction.setAccountNum(account.getAccountNum());
		transaction.setTransAmount(requestDto.getTransAmount());
		transaction.setTransDate(requestDto.getTransDate());
		transaction.setTransMemo(requestDto.getTransMemo());
		transaction.setTransType(category.getCategoryType());
		transaction.setVersion(version);
		
		int affected = personalTransactionMapper.updateTransaction(transaction);
		
		if(affected == 0) {
			throw new BusinessException(ErrorCode.TRANSACTION_CONFLICT);
		}
		
		PersonalTransactionResponseDto responseDto = new PersonalTransactionResponseDto();
		responseDto.setTransNum(transaction.getTransNum());
		responseDto.setVersion(transaction.getVersion()+1);
		responseDto.setTransAmount(transaction.getTransAmount());
		responseDto.setTransDate(transaction.getTransDate());
		responseDto.setTransMemo(transaction.getTransMemo());
		responseDto.setTransType(transaction.getTransType());
		responseDto.setAccountNum(account.getAccountNum());
		responseDto.setAccountName(account.getAccountName());
		responseDto.setCategoryName(category.getCategoryName());
		responseDto.setCategoryEmoji(category.getCategoryEmoji());
		responseDto.setCategoryNum(transaction.getCategoryNum());
		
		return responseDto;
	}
}
