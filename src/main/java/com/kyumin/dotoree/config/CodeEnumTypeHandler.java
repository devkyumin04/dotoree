package com.kyumin.dotoree.config;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import com.kyumin.dotoree.domain.CodeEnum;

// CodeEnum ↔ CHAR(1) 변환. MyBatis 기본 EnumTypeHandler 는 이름("ACTIVE")으로 저장하는데,
// 이미 운영 DB 에 'A' · 'W' 가 들어 있으니 컬럼은 그대로 두고 이 핸들러가 글자를 enum 으로 바꾼다 — 마이그레이션 없이 코드만 바뀐다
public class CodeEnumTypeHandler<E extends Enum<E> & CodeEnum> extends BaseTypeHandler<E> {

	private final Class<E> type;

	public CodeEnumTypeHandler(Class<E> type) {
		this.type = type;
	}

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
		ps.setString(i, parameter.getCode());
	}

	@Override
	public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
		return toEnum(rs.getString(columnName));
	}

	@Override
	public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		return toEnum(rs.getString(columnIndex));
	}

	@Override
	public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		return toEnum(cs.getString(columnIndex));
	}

	private E toEnum(String code) {
		return code == null ? null : CodeEnum.fromCode(type, code);
	}
}
