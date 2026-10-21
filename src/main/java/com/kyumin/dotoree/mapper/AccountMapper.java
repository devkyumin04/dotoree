package com.kyumin.dotoree.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.domain.Account;
import com.kyumin.dotoree.dto.AccountResponseDto;

@Mapper
public interface AccountMapper {

	void insertAccount(Account account);

	Account findDefaultAccount(@Param("userNum") Integer userNum);

	// 닫힌 계좌도 돌려준다 — 부르는 쪽이 "없음(404)"과 "닫힘"을 가른다
	Account findByAccountNum(@Param("accountNum") Integer accountNum);

	// 열린 계좌 + 잔액. 기본 계좌가 맨 앞
	List<AccountResponseDto> findOpenAccounts(@Param("userNum") Integer userNum);

	long calculateBalance(@Param("accountNum") Integer accountNum);

	// 열린 계좌 중 같은 이름 수 — 수정 때는 자기 자신(excludeAccountNum)을 빼고 센다
	int countOpenByName(@Param("userNum") Integer userNum, @Param("accountName") String accountName,
			@Param("excludeAccountNum") Integer excludeAccountNum);

	int updateAccount(Account account);

	int closeAccount(@Param("accountNum") Integer accountNum);
}
