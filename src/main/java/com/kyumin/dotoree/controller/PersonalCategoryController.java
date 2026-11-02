package com.kyumin.dotoree.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.kyumin.dotoree.dto.CategoryRequestDto;
import com.kyumin.dotoree.dto.CategoryResponseDto;
import com.kyumin.dotoree.service.PersonalCategoryService;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class PersonalCategoryController {

    private final PersonalCategoryService personalCategoryService;

    @PostMapping
    public ResponseEntity<CategoryResponseDto> createCategory(
            @AuthenticationPrincipal Integer userNum,
            @Valid @RequestBody CategoryRequestDto requestDto) {
    	
    	CategoryResponseDto responseDto = personalCategoryService.createCategory(userNum, requestDto);
    	
    	return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    	
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponseDto>> getCategories(
            @AuthenticationPrincipal Integer userNum) {

    	List<CategoryResponseDto> responseDtoList = personalCategoryService.getCategories(userNum);
        
        return ResponseEntity.status(HttpStatus.OK).body(responseDtoList);
    }
    
    @PutMapping("/{categoryNum}")
    public ResponseEntity<CategoryResponseDto> updateCategory(
            @AuthenticationPrincipal Integer userNum,
            @PathVariable Integer categoryNum,
            @Valid @RequestBody CategoryRequestDto requestDto) {

        CategoryResponseDto responseDto = personalCategoryService.updateCategory(userNum, categoryNum, requestDto);
        return ResponseEntity.status(HttpStatus.OK).body(responseDto);
    }

    @DeleteMapping("/{categoryNum}")
    public ResponseEntity<Void> deleteCategory(
            @AuthenticationPrincipal Integer userNum,
            @PathVariable Integer categoryNum) {

    	personalCategoryService.deleteCategory(userNum, categoryNum);
    	
    	return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
    
}