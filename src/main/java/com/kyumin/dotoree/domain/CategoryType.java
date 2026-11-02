package com.kyumin.dotoree.domain;

// personal_categories.category_type · personal_transactions.trans_type
// 거래의 종류는 그 거래가 속한 카테고리의 종류를 그대로 물려받는다(PersonalTransactionService) — 그래서 enum 하나를 같이 쓴다
public enum CategoryType implements CodeEnum {

	EXPENSE("E"),	// 지출
	INCOME("I");	// 수입

	private final String code;

	CategoryType(String code) {
		this.code = code;
	}

	@Override
	public String getCode() {
		return code;
	}

	public static CategoryType fromCode(String code) {
		return CodeEnum.fromCode(CategoryType.class, code);
	}
}
