package com.kyumin.dotoree.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.kyumin.dotoree.dto.CategoryStatRowDto;
import com.kyumin.dotoree.dto.MonthlyStatRowDto;

@Mapper
public interface PersonalStatisticsMapper {

    /*
     * 개인 통계 매퍼
     *
     * 매퍼는 '꺼내오기'만 한다. 묶기(계층)·비율 계산·0 나누기 판정은 전부 서비스 몫.
     * 다만 SUM / GROUP BY 같은 집계는 DB 가 훨씬 빠르므로 SQL 에 맡긴다.
     *   → 구분선: DB 가 잘하는 계산은 SQL, 우리 서비스의 규칙이 들어가는 계산은 서비스
     *
     * 파라미터가 2개 이상이므로 @Param 필수
     */

    /**
     * 기간 내 카테고리별 합계.
     *
     * 반환되는 한 줄 = 카테고리 하나의 합계 (소분류는 소분류대로, 대분류에 직접 달린 것은 그것대로).
     * 대분류로 묶는 것은 서비스가 parentCategoryNum 을 보고 한다.
     *
     * 날짜는 [startDate, endDate) — 시작일 포함, 종료일 미포함.
     * 9월 조회라면 2026-09-01 ~ 2026-10-01 을 넘긴다.
     */
    List<CategoryStatRowDto> findCategoryStats(@Param("userNum") Integer userNum,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    /**
     * 기간 내 월별 수입·지출 합계. (최근 6개월 추이용)
     *
     * 반환되는 한 줄 = 한 달. 수입과 지출이 한 행에 나란히 온다.
     * 거래가 0건인 달은 행이 나오지 않는다 → 빈 달 채우기와 balance 계산은 서비스 몫.
     *
     * 날짜는 [startDate, endDate) — findCategoryStats 와 같은 계약.
     */
    List<MonthlyStatRowDto> findMonthlyStats(@Param("userNum") Integer userNum,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);
}
