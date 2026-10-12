package com.kyumin.dotoree.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.kyumin.dotoree.domain.PersonalCategory;
import com.kyumin.dotoree.dto.CategoryRequestDto;
import com.kyumin.dotoree.dto.CategoryResponseDto;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mapper.PersonalCategoryMapper;
import com.kyumin.dotoree.mapper.PersonalTransactionMapper;

@Service
@RequiredArgsConstructor
public class PersonalCategoryService {

    private final PersonalCategoryMapper personalCategoryMapper;
    private final PersonalTransactionMapper personalTransactionMapper;

    public CategoryResponseDto createCategory(Integer userNum, CategoryRequestDto requestDto) {
    	
    	if (PersonalCategory.DEFAULT_CATEGORY_NAME.equals(requestDto.getCategoryName())) {
    	    throw new BusinessException(ErrorCode.RESERVED_CATEGORY_NAME);
    	}
    	
    	validateParent(userNum, requestDto.getParentCategoryNum(), requestDto.getCategoryType(), null);
    	  
        // 1. requestDto의 값 + userNum으로 PersonalCategory 객체 만들기
    	PersonalCategory newPersonalCategory = new PersonalCategory();
    	newPersonalCategory.setUserNum(userNum);
    	newPersonalCategory.setParentCategoryNum(requestDto.getParentCategoryNum());
    	newPersonalCategory.setCategoryName(requestDto.getCategoryName());
    	newPersonalCategory.setCategoryEmoji(requestDto.getCategoryEmoji());
    	newPersonalCategory.setCategoryType(requestDto.getCategoryType());
    	newPersonalCategory.setIsDefaultYn("N");
    	newPersonalCategory.setUseYn("Y");
    	// 2. Mapper로 insert
    	personalCategoryMapper.insertCategory(newPersonalCategory);
        // 3. CategoryResponseDto로 변환해서 반환 (방금 만든 카테고리는 자식이 있을 수 없다)
    	return toResponseDto(newPersonalCategory, false);
    }

    // PersonalCategory -> CategoryResponseDto 변환. 세 곳(생성/목록/수정)에서 같은 변환을 쓰므로 한 곳에 모음
    private CategoryResponseDto toResponseDto(PersonalCategory category, boolean hasChildren) {
        return new CategoryResponseDto(
            category.getCategoryNum(),
            category.getCategoryName(),
            category.getCategoryEmoji(),
            category.getCategoryType(),
            category.getParentCategoryNum(),
            "Y".equals(category.getIsDefaultYn()),
            hasChildren
        );
    }
    
    private void validateParent(Integer userNum, Integer parentNum, String categoryType, Integer selfNum) {
        if (parentNum == null) return;                                   // 대분류
        if (selfNum != null && parentNum.equals(selfNum)) throw new BusinessException(ErrorCode.CATEGORY_SELF_PARENT);     // 자기 자신 (조회 전)

        PersonalCategory parent = personalCategoryMapper.findByCategoryNum(parentNum);
        if (parent == null) throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        if (!parent.getUserNum().equals(userNum)) throw new BusinessException(ErrorCode.CATEGORY_ACCESS_DENIED);
        if (!parent.getCategoryType().equals(categoryType)) throw new BusinessException(ErrorCode.CATEGORY_TYPE_MISMATCH);
        if (parent.getParentCategoryNum() != null) throw new BusinessException(ErrorCode.CATEGORY_DEPTH_EXCEEDED);            // 부모가 소분류 → 3계층
        if ("Y".equals(parent.getIsDefaultYn())) throw new BusinessException(ErrorCode.CATEGORY_UNDER_DEFAULT);              // 미분류 아래
    }

    public List<CategoryResponseDto> getCategories(Integer userNum) {
        // 1. Mapper로 조회
    	List<PersonalCategory> categories = personalCategoryMapper.findByUserNum(userNum);
        // 2. 누가 부모로 쓰이고 있나 - 목록 안에서 parentCategoryNum 으로 등장하는 번호를 모아둔다
        //    (카테고리마다 countChildren 쿼리를 날리면 N+1 이라 목록 한 번으로 계산)
        Set<Integer> parentNums = categories.stream()
            .map(PersonalCategory::getParentCategoryNum)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        // 3. List<PersonalCategory>를 List<CategoryResponseDto>로 변환해서 반환
    	return categories.stream()
		    .map(category -> toResponseDto(category, parentNums.contains(category.getCategoryNum())))
		    .collect(Collectors.toList());
    }
    
    public CategoryResponseDto updateCategory(Integer userNum, Integer categoryNum, CategoryRequestDto requestDto) {
    	
    	if (PersonalCategory.DEFAULT_CATEGORY_NAME.equals(requestDto.getCategoryName())) {
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
        
        validateParent(userNum, requestDto.getParentCategoryNum(), category.getCategoryType(), categoryNum);

        boolean hasChildren = personalCategoryMapper.countChildren(categoryNum) > 0;
        if (requestDto.getParentCategoryNum() != null && hasChildren) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN_TO_SUB);
        }
        
        // 값 채우기 (requestDto의 값으로 category 객체 업데이트)
        category.setCategoryName(requestDto.getCategoryName());
        category.setCategoryEmoji(requestDto.getCategoryEmoji());
        category.setParentCategoryNum(requestDto.getParentCategoryNum());
        
        // Mapper로 update 호출
        personalCategoryMapper.updateCategory(category);
        // ResponseDto로 변환해서 반환
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
        
        //미분류 카테고리는 singup에서 항상 생성, sql로 직접 넣은 유저만 null
        PersonalCategory defaultCategory = personalCategoryMapper.findDefaultCategory(userNum, category.getCategoryType());
        personalTransactionMapper.moveToDefaultCategory(categoryNum, defaultCategory.getCategoryNum());
        
        // Mapper로 delete 호출
        personalCategoryMapper.deleteCategory(categoryNum);
    }
}