package com.kyumin.dotoree.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.domain.CategoryType;
import com.kyumin.dotoree.domain.PersonalCategory;

@Mapper
public interface PersonalCategoryMapper {

    void insertCategory(PersonalCategory category);

    List<PersonalCategory> findByUserNum(@Param("userNum") Integer userNum);
    
    PersonalCategory findByCategoryNum(@Param("categoryNum") Integer categoryNum);
    
    void updateCategory(PersonalCategory category);
    
    void deleteCategory(@Param("categoryNum") Integer categoryNum);

    int countChildren(@Param("categoryNum") Integer categoryNum);
    
    PersonalCategory findDefaultCategory(@Param("userNum") Integer userNum,
            @Param("categoryType") CategoryType categoryType);
}