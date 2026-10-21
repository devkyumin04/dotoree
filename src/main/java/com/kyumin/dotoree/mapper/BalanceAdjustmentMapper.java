package com.kyumin.dotoree.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.domain.BalanceAdjustment;
import com.kyumin.dotoree.dto.BalanceAdjustmentResponseDto;

// 잔액에 더하는 건 여기가 아니라 AccountMapper.xml balanceOf 한 곳 (ADR-066)
@Mapper
public interface BalanceAdjustmentMapper {

	void insertAdjustment(BalanceAdjustment adjustment);

	BalanceAdjustment findByAdjustmentNum(@Param("adjustmentNum") Integer adjustmentNum);

	List<BalanceAdjustmentResponseDto> findByMonth(@Param("userNum") Integer userNum,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	int deleteAdjustment(@Param("adjustmentNum") Integer adjustmentNum);
}
