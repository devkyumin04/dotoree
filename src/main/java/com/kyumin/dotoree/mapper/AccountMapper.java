package com.kyumin.dotoree.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.domain.Account;

@Mapper
public interface AccountMapper {

	void insertAccount(Account account);

	Account findDefaultAccount(@Param("userNum") Integer userNum);
}
