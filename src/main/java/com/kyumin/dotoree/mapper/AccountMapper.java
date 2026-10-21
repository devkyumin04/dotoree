package com.kyumin.dotoree.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.domain.Account;
import com.kyumin.dotoree.dto.AccountResponseDto;

// 잔액을 계산하는 쿼리는 전부 untilDate 를 받는다 — null 이면 "지금", 날짜면 "그날 기준"(AccountMapper.xml balanceOf)
@Mapper
public interface AccountMapper {

	void insertAccount(Account account);

	Account findDefaultAccount(@Param("userNum") Integer userNum);

	// 닫힌 계좌도 돌려준다 — 부르는 쪽이 "없음(404)"과 "닫힘"을 가른다
	Account findByAccountNum(@Param("accountNum") Integer accountNum);

	// 열린 계좌 + 잔액. 기본 계좌가 맨 앞
	List<AccountResponseDto> findOpenAccounts(@Param("userNum") Integer userNum, @Param("untilDate") LocalDate untilDate);

	long calculateBalance(@Param("accountNum") Integer accountNum, @Param("untilDate") LocalDate untilDate);

	// 그 사용자의 모든 계좌(닫힌 것 포함) 잔액 합 — 지난 날짜엔 닫힌 계좌에도 돈이 있었다
	long totalBalance(@Param("userNum") Integer userNum, @Param("untilDate") LocalDate untilDate);

	// 그날 시작한 계좌(닫힌 것 포함)의 시작 잔액 + 그날 한 보정의 합 — 전날 대비에서 뺀다
	long sumOpeningBalanceOn(@Param("userNum") Integer userNum, @Param("date") LocalDate date);

	// 열린 계좌 중 같은 이름 수 — 수정 때는 자기 자신(excludeAccountNum)을 빼고 센다
	int countOpenByName(@Param("userNum") Integer userNum, @Param("accountName") String accountName,
			@Param("excludeAccountNum") Integer excludeAccountNum);

	int updateAccount(Account account);

	int closeAccount(@Param("accountNum") Integer accountNum);
}
