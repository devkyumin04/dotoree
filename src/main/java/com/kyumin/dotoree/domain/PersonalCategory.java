package com.kyumin.dotoree.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PersonalCategory {
	
	public static final String DEFAULT_CATEGORY_NAME = "미분류";
	
	private Integer categoryNum;
	private Integer userNum;
	private Integer parentCategoryNum;
	private String categoryName;
	private String categoryEmoji;
	private CategoryType categoryType;
	private String isDefaultYn;
	private String useYn;
	private LocalDateTime createdAt;

}
