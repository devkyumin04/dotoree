package com.kyumin.dotoree.domain;

// DB 에는 한 글자 코드(CHAR(1))로 저장하고 자바에서는 enum 으로 다루는 값들의 공통 모양.
// 글자 ↔ enum 변환은 MyBatis 가 CodeEnumTypeHandler 로 한 곳에서 한다 — 서비스 · 도메인이 "A" · "W" 를 직접 다루지 않게
public interface CodeEnum {

	String getCode();

	// 없는 코드는 조용히 null 로 넘기지 않고 바로 터뜨린다 — 오타 · 엉뚱한 값이 DB 까지 흘러가면 나중에 찾기 어렵다
	static <E extends Enum<E> & CodeEnum> E fromCode(Class<E> type, String code) {
		for (E constant : type.getEnumConstants()) {
			if (constant.getCode().equals(code)) {
				return constant;
			}
		}
		throw new IllegalArgumentException(type.getSimpleName() + " 에 없는 코드: " + code);
	}
}
