package com.kyumin.dotoree.service;

import org.springframework.stereotype.Service;

import com.kyumin.dotoree.domain.Account;
import com.kyumin.dotoree.mapper.AccountMapper;

import lombok.RequiredArgsConstructor;

// 계좌 규칙은 여기 한 곳 — 기본 계좌 만들기 · 찾기 (ADR-065). 계좌 등록 · 닫기 · 잔액 계산은 다음 PR 에서 여기에 붙인다
@Service
@RequiredArgsConstructor
public class AccountService {

	private final AccountMapper accountMapper;

	// 가입 때 '현금' 기본 계좌 — 가입 '미분류' 카테고리와 같은 자리. 부르는 쪽(가입)의 트랜잭션에 같이 묶인다
	public Integer createDefaultAccount(Integer userNum) {
		Account account = new Account();
		account.setUserNum(userNum);
		account.setAccountName(Account.DEFAULT_ACCOUNT_NAME);
		account.setAccountType(Account.TYPE_CASH);
		account.setOpeningBalance(0L);
		account.setIsDefaultYn("Y");
		accountMapper.insertAccount(account);
		return account.getAccountNum();
	}

	// 거래가 들어갈 기본 계좌 번호. 없으면 만든다 —
	// 이 버전을 롤백했다 다시 올리면 그 사이 옛 jar 로 가입한 계정엔 기본 계좌가 없다(V4 는 적용 시점의 계정에만 만들었다)
	public Integer defaultAccountNum(Integer userNum) {
		Account account = accountMapper.findDefaultAccount(userNum);
		if (account == null) {
			return createDefaultAccount(userNum);
		}
		return account.getAccountNum();
	}
}
