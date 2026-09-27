package com.kyumin.dotoree.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.kyumin.dotoree.domain.RefreshToken;

@Mapper
public interface RefreshTokenMapper {

	void insertRefreshToken(RefreshToken refreshToken);

}
