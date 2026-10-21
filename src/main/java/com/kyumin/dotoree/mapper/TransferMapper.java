package com.kyumin.dotoree.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.domain.Transfer;
import com.kyumin.dotoree.dto.TransferResponseDto;

@Mapper
public interface TransferMapper {

	void insertTransfer(Transfer transfer);

	Transfer findByTransferNum(@Param("transferNum") Integer transferNum);

	List<TransferResponseDto> findByMonth(@Param("userNum") Integer userNum,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	int updateTransfer(Transfer transfer);

	int deleteTransfer(@Param("transferNum") Integer transferNum, @Param("version") Integer version);
}
