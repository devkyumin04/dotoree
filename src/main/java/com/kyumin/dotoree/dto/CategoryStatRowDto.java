package com.kyumin.dotoree.dto;

import com.kyumin.dotoree.domain.CategoryType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CategoryStatRowDto {
	private Integer categoryNum;
	private String categoryName;
	private String categoryEmoji;
	private Integer parentCategoryNum;
	private String  parentCategoryName;
	private String  parentCategoryEmoji;
	private Long amount;
	private CategoryType categoryType;
}
