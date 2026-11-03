package com.kyumin.dotoree.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CategoryResponseDto {
	private Integer categoryNum;
	private String categoryName;
	private String categoryEmoji;
	@Schema(description = "종류 — I 수입 · E 지출", allowableValues = {"I", "E"})
	private String categoryType;
	private Integer parentCategoryNum;

	// 프론트가 규칙을 알 필요 없도록 서버가 판단한 값을 실어 보낸다 (ADR-038)
	private Boolean isDefault;      // 기본 카테고리('미분류') 여부 -> 수정/삭제 버튼, 부모 선택지에서 제외
	private Boolean hasChildren;    // 소분류를 가진 대분류 여부 -> 부모 변경 불가
}
