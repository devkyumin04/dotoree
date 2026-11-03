package com.kyumin.dotoree.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.kyumin.dotoree.domain.CategoryType;
import com.kyumin.dotoree.domain.PersonalCategory;
import com.kyumin.dotoree.dto.CategoryRequestDto;
import com.kyumin.dotoree.dto.CategoryResponseDto;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mapper.PersonalCategoryMapper;
import com.kyumin.dotoree.mapper.PersonalTransactionMapper;
import com.kyumin.dotoree.mapper.RecurringMapper;

@Service
@RequiredArgsConstructor
public class PersonalCategoryService {

    private final PersonalCategoryMapper personalCategoryMapper;
    private final PersonalTransactionMapper personalTransactionMapper;
    private final RecurringMapper recurringMapper;

    public CategoryResponseDto createCategory(Integer userNum, CategoryRequestDto requestDto) {
    	
    	if (PersonalCategory.DEFAULT_CATEGORY_NAME.equals(requestDto.categoryName())) {
    	    throw new BusinessException(ErrorCode.RESERVED_CATEGORY_NAME);
    	}
    	
    	CategoryType categoryType = CategoryType.fromCode(requestDto.categoryType());
    	validateParent(userNum, requestDto.parentCategoryNum(), categoryType, null);
    	  
    	PersonalCategory newPersonalCategory = new PersonalCategory();
    	newPersonalCategory.setUserNum(userNum);
    	newPersonalCategory.setParentCategoryNum(requestDto.parentCategoryNum());
    	newPersonalCategory.setCategoryName(requestDto.categoryName());
    	newPersonalCategory.setCategoryEmoji(requestDto.categoryEmoji());
    	newPersonalCategory.setCategoryType(categoryType);
    	newPersonalCategory.setIsDefaultYn("N");
    	newPersonalCategory.setUseYn("Y");
    	personalCategoryMapper.insertCategory(newPersonalCategory);
        // 방금 만든 카테고리는 자식이 있을 수 없다
    	return toResponseDto(newPersonalCategory, false);
    }

    // PersonalCategory -> CategoryResponseDto 변환. 세 곳(생성/목록/수정)에서 같은 변환을 쓰므로 한 곳에 모음
    private CategoryResponseDto toResponseDto(PersonalCategory category, boolean hasChildren) {
        return new CategoryResponseDto(
            category.getCategoryNum(),
            category.getCategoryName(),
            category.getCategoryEmoji(),
            category.getCategoryType().getCode(),
            category.getParentCategoryNum(),
            "Y".equals(category.getIsDefaultYn()),
            hasChildren
        );
    }
    
    private void validateParent(Integer userNum, Integer parentNum, CategoryType categoryType, Integer selfNum) {
        if (parentNum == null) return;                                   // 대분류
        if (selfNum != null && parentNum.equals(selfNum)) throw new BusinessException(ErrorCode.CATEGORY_SELF_PARENT);     // 자기 자신 (조회 전)

        PersonalCategory parent = personalCategoryMapper.findByCategoryNum(parentNum);
        if (parent == null) throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        if (!parent.getUserNum().equals(userNum)) throw new BusinessException(ErrorCode.CATEGORY_ACCESS_DENIED);
        if (parent.getCategoryType() != categoryType) throw new BusinessException(ErrorCode.CATEGORY_TYPE_MISMATCH);
        if (parent.getParentCategoryNum() != null) throw new BusinessException(ErrorCode.CATEGORY_DEPTH_EXCEEDED);            // 부모가 소분류 → 3계층
        if ("Y".equals(parent.getIsDefaultYn())) throw new BusinessException(ErrorCode.CATEGORY_UNDER_DEFAULT);              // 미분류 아래
    }

    public List<CategoryResponseDto> getCategories(Integer userNum) {
    	List<PersonalCategory> categories = personalCategoryMapper.findByUserNum(userNum);
        // 누가 부모로 쓰이나 — 카테고리마다 countChildren 을 날리면 N+1 이라, 목록 안에서 parentCategoryNum 으로 등장하는 번호를 한 번에 모은다
        Set<Integer> parentNums = categories.stream()
            .map(PersonalCategory::getParentCategoryNum)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    	return categories.stream()
		    .map(category -> toResponseDto(category, parentNums.contains(category.getCategoryNum())))
		    .collect(Collectors.toList());
    }
    
    public CategoryResponseDto updateCategory(Integer userNum, Integer categoryNum, CategoryRequestDto requestDto) {
    	
    	if (PersonalCategory.DEFAULT_CATEGORY_NAME.equals(requestDto.categoryName())) {
    	    throw new BusinessException(ErrorCode.RESERVED_CATEGORY_NAME);
    	}
    	
        PersonalCategory category = personalCategoryMapper.findByCategoryNum(categoryNum);

        if (category == null) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        if (!category.getUserNum().equals(userNum)) {
            throw new BusinessException(ErrorCode.CATEGORY_ACCESS_DENIED);
        }

        if ("Y".equals(category.getIsDefaultYn())) {
            throw new BusinessException(ErrorCode.DEFAULT_CATEGORY_NOT_EDITABLE);
        }
        
        validateParent(userNum, requestDto.parentCategoryNum(), category.getCategoryType(), categoryNum);

        boolean hasChildren = personalCategoryMapper.countChildren(categoryNum) > 0;
        if (requestDto.parentCategoryNum() != null && hasChildren) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN_TO_SUB);
        }
        
        category.setCategoryName(requestDto.categoryName());
        category.setCategoryEmoji(requestDto.categoryEmoji());
        category.setParentCategoryNum(requestDto.parentCategoryNum());
        
        personalCategoryMapper.updateCategory(category);
        return toResponseDto(category, hasChildren);
    }
    
    @Transactional
    public void deleteCategory(Integer userNum, Integer categoryNum) {

        PersonalCategory category = personalCategoryMapper.findByCategoryNum(categoryNum);

        if (category == null) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        if (!category.getUserNum().equals(userNum)) {
            throw new BusinessException(ErrorCode.CATEGORY_ACCESS_DENIED);
        }

        // '미분류' 카테고리는 삭제 불가 
        if ("Y".equals(category.getIsDefaultYn())) {
        	throw new BusinessException(ErrorCode.DEFAULT_CATEGORY_NOT_DELETABLE);
        }

        if (personalCategoryMapper.countChildren(categoryNum) > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        
        // 미분류 카테고리는 signup에서 항상 생성, sql로 직접 넣은 유저만 null
        PersonalCategory defaultCategory = personalCategoryMapper.findDefaultCategory(userNum, category.getCategoryType());
        personalTransactionMapper.moveToDefaultCategory(categoryNum, defaultCategory.getCategoryNum());
        // 반복 규칙도 거래처럼 '미분류'로 — 지운 카테고리로 예정을 확인하면 404 가 난다 (ADR-067)
        recurringMapper.moveToDefaultCategory(categoryNum, defaultCategory.getCategoryNum());
        
        personalCategoryMapper.deleteCategory(categoryNum);
    }
}