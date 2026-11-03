package com.kyumin.dotoree.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CategoryRequestDto {
	
	private Integer parentCategoryNum;
	
	@NotBlank(message = "카테고리 이름을 입력하시오.") 
	@Size(max = 20, message = "카테고리 이름은 20자 이하로 입력하시오")
	private String categoryName;
	
	@Size(max = 10, message = "이모지는 10자 이하로 입력하시오")
	private String categoryEmoji;
	
	@NotBlank(message = "카테고리 타입을 지정하시오.")
	@Pattern(regexp="[IE]", message = "잘못된 카테고리 타입입니다!")
	@Schema(description = "종류 — I 수입 · E 지출", allowableValues = {"I", "E"}, example = "E")
	private String categoryType;

	// 이름은 입구에서 한 번만 정규화한다 (앞뒤 공백 제거). Lombok 은 직접 쓴 세터가 있으면 그 필드 세터를 만들지 않는다.
	// Jackson 이 이 세터로 값을 넣고 그 뒤에 @Valid 가 돌기 때문에, @NotBlank·@Size·예약어 검사·저장이 전부 정리된 값만 본다.
	// trim() 이 아니라 strip() — 한글 입력기의 전각 공백(U+3000)까지 지운다.
	// DB collation(PAD SPACE / NO PAD)에 "식비" = "식비 " 판단을 맡기지 않기 위함 (판단은 서버, ADR-038)
	public void setCategoryName(String categoryName) {
		this.categoryName = (categoryName == null) ? null : categoryName.strip();
	}
}
