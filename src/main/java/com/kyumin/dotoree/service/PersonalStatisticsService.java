package com.kyumin.dotoree.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import com.kyumin.dotoree.dto.CategoryStatDto;
import com.kyumin.dotoree.dto.CategoryStatRowDto;
import com.kyumin.dotoree.dto.MonthlyStatDto;
import com.kyumin.dotoree.dto.MonthlyStatRowDto;
import com.kyumin.dotoree.dto.StatisticsResponseDto;
import com.kyumin.dotoree.dto.SubCategoryStatDto;
import com.kyumin.dotoree.mapper.PersonalStatisticsMapper;

@Service
@RequiredArgsConstructor
public class PersonalStatisticsService {

	/*
	 * ============================================================
	 *  이 클래스의 역할 : 매퍼가 가져온 '평평한 표' 를 화면이 쓸 모양으로 바꾼다.
	 *    Mapper = SQL 집계만 / Service = 묶기·계산·규칙 / Controller = HTTP
	 *
	 *  확정된 설계
	 *    - API 는 하나. GET /api/statistics?year=&month=
	 *    - 지출 목록과 수입 목록을 나눈다 (비율의 분모가 달라지므로)
	 *    - 대분류가 소분류를 품는 중첩 구조 (봉투 - 종이)
	 *    - 비율은 '자기 부모 대비'. 대분류의 부모는 그 타입 전체
	 *    - 수입 대비 지출 = 지출 ÷ 수입 × 100
	 *    - 6개월 추이는 '보고 있는 달' 기준 롤링 6개 (1월이어도 점 6개)
	 *    - 계산은 전부 서버. 프론트는 그리기만
	 * ============================================================
	 */

	private final PersonalStatisticsMapper personalStatisticsMapper;

	/*
	 * 봉투(대분류)의 이름표. 번호·이름·이모지만 담는 값 묶음.
	 *
	 * record 는 자바 16+ 문법으로, 아래 한 줄이 이것들을 자동으로 만들어준다
	 *   - final 필드 3개
	 *   - 생성자        new ParentInfo(71, "식비", "(이모지)")
	 *   - 읽기 메서드   info.num() / info.name() / info.emoji()   <- get 접두사가 없다
	 *   - equals / hashCode / toString
	 *
	 * 값만 담고 절대 안 바뀌므로 불변인 record 가 알맞다.
	 * 이 서비스 안에서만 쓰므로 별도 파일 없이 중첩 선언.
	 */
	private record ParentInfo(Integer num, String name, String emoji) {}

	/*
	 * 매퍼가 돌려주는 한 줄(CategoryStatRowDto) 의 모양 — 실제 데이터로 확인한 것
	 *
	 *   categoryNum  categoryName  parentCategoryNum  parentCategoryName  amount   categoryType
	 *   ----------------------------------------------------------------------------------------
	 *   86           월세           73                 주거                9,900,000  E   <- 소분류
	 *   71           식비           null               null                  195,933  E   <- 대분류(직접)
	 *   92           월급           76                 급여                3,200,000  I   <- 소분류
	 *
	 *   · 대분류와 소분류가 한 목록에 섞여서 온다
	 *   · 대분류 행은 parentCategoryNum 이 null
	 *   · 소분류 행에는 부모 이름·이모지가 함께 실려 있다
	 *     (급여처럼 직접 거래가 없는 대분류는 이 목록에 안 나오므로, 부모 정보를 여기서 얻어야 한다)
	 */

	/*
	 * 매퍼가 준 줄들(지출·수입 섞임)을 한 타입만 골라 봉투로 묶는다.
	 * 조회는 밖(getStatistics)에서 한 번만 하고 여기는 받은 리스트만 다룬다 — DB 없이도 테스트할 수 있는 순수 함수.
	 */
	private List<CategoryStatDto> buildCategoryStats(List<CategoryStatRowDto> categoryRows, String categoryType) {
		List<CategoryStatRowDto> filteredRows = categoryRows.stream()
												.filter(row -> categoryType.equals(row.getCategoryType()))
												.toList();
		
		// 해당 타입 전체 합계. 봉투 비율의 분모가 된다.
		// 봉투를 다 만든 뒤에 더해도 같은 값이지만, 비율 계산이 봉투를 만드는 도중에 필요하므로
		// 먼저 구해둔다. 모든 줄이 정확히 한 봉투에만 들어가므로 '줄 전체 합 = 봉투 합의 합' 이다.
		long totalAmount = filteredRows.stream()
							.mapToLong(row -> row.getAmount())
							.sum();
		// -- 준비물 두 개 ------------------------------------------
		//
		// childrenMap : 대분류번호 -> 그 봉투에 들어갈 줄들
		//   예) 71 -> [식비 줄, 닭가슴살 줄, 프로틴 줄]
		Map<Integer, List<CategoryStatRowDto>> childrenMap = new LinkedHashMap<>();
		//
		// parentInfoMap : 대분류번호 -> 그 봉투의 이름표
		//   예) 71 -> ParentInfo(71, "식비", "(이모지)")
		//   왜 따로 기억하냐면, 급여(76)처럼 직접 거래가 없는 대분류는
		//   집계 결과에 자기 줄이 아예 없다. 자식 줄이 물고 온 정보로 채워야 한다
		Map<Integer, ParentInfo> parentInfoMap = new LinkedHashMap<>();

		// -- 한 줄씩 훑으면서 두 맵에 담는다 ------------------------
		for (CategoryStatRowDto row : filteredRows) {

			// 이 줄이 '어느 봉투에 속하는지' 를 담을 변수 셋.
			// if 블록 밖에서 선언해야 블록이 끝난 뒤에도 쓸 수 있다
			Integer parentNum;    // 봉투 번호
			String parentName;    // 봉투 이름
			String parentEmoji;   // 봉투 이모지

			if (row.getParentCategoryNum() == null) {
				// - 대분류 줄 (부모가 없다)
				//   예) 식비 줄 : categoryNum=71, categoryName="식비", parentCategoryName=null
				//   자기가 곧 봉투이므로 '자기 필드' 를 읽는다
				parentNum = row.getCategoryNum();
				parentName = row.getCategoryName();
				parentEmoji = row.getCategoryEmoji();
			} else {
				// - 소분류 줄 (부모가 있다)
				//   예) 닭가슴살 줄 : categoryNum=78, parentCategoryNum=71, parentCategoryName="식비"
				//   봉투는 내 부모이므로 'parent 필드' 를 읽는다
				parentNum = row.getParentCategoryNum();
				parentName = row.getParentCategoryName();
				parentEmoji = row.getParentCategoryEmoji();
			}

			// 이 줄을 해당 봉투에 담는다.
			// 봉투가 아직 없으면 빈 리스트를 만들어 넣고, 어쨌든 그 리스트를 받아 add
			childrenMap.computeIfAbsent(parentNum, k -> new ArrayList<>()).add(row);

			// 봉투 이름표를 기억한다. 이미 있으면 덮어쓰지 않는다
			parentInfoMap.putIfAbsent(parentNum , new ParentInfo(parentNum, parentName, parentEmoji));
		}
		
		List<CategoryStatDto> result = new ArrayList<>();

		for (Map.Entry<Integer, ParentInfo> entry : parentInfoMap.entrySet()) {
		    Integer parentNum = entry.getKey();
		    ParentInfo info = entry.getValue();
		    
		    List<CategoryStatRowDto> children = childrenMap.get(parentNum);
		    
		    long parentAmount = children.stream()
                    .mapToLong(row -> row.getAmount())
                    .sum();
		    
		    List<SubCategoryStatDto> childDtos = new ArrayList<>();

		    for (CategoryStatRowDto child : children) {
		        BigDecimal childRatio = BigDecimal.valueOf(child.getAmount())
		                .multiply(BigDecimal.valueOf(100))
		                .divide(BigDecimal.valueOf(parentAmount), 1, RoundingMode.HALF_UP);
		        childDtos.add(new SubCategoryStatDto(
		                child.getCategoryNum(),
		                child.getCategoryName(),
		                child.getCategoryEmoji(),
		                child.getAmount(),
		                childRatio));
		    } 
		    // 0 으로 나누는 방어가 없는 이유
		    //   totalAmount 가 0 이면 filteredRows 가 비어 있다는 뜻이고,
		    //   그러면 위 for 문이 한 번도 돌지 않아 parentInfoMap 이 비고, 이 루프에도 못 들어온다.
		    //   parentAmount 도 마찬가지 - 자식이 최소 하나 있어야 봉투가 만들어지므로 0 이 될 수 없다.
		    //   즉 코드가 막는 게 아니라 데이터 흐름상 도달하지 않는다. 흐름을 바꿀 때 주의.
		    BigDecimal parentRatio = BigDecimal.valueOf(parentAmount)
		            .multiply(BigDecimal.valueOf(100))
		            .divide(BigDecimal.valueOf(totalAmount), 1, RoundingMode.HALF_UP);

		    result.add(new CategoryStatDto(
		            info.num(),
		            info.name(),
		            info.emoji(),
		            parentAmount,
		            parentRatio,
		            childDtos));
		}
		
		return result;
	}
	
	public List<MonthlyStatDto> getMonthlyStats(Integer userNum, int year, int month){
		YearMonth ym = YearMonth.of(year, month);
		YearMonth startMonth = ym.minusMonths(5);
		LocalDate startDate = startMonth.atDay(1);
		LocalDate endDate = ym.plusMonths(1).atDay(1);
		
		List<MonthlyStatRowDto> monthlyRows = personalStatisticsMapper.findMonthlyStats(userNum, startDate, endDate);
		
		Map<String, MonthlyStatRowDto> rowMap = new LinkedHashMap<>();
		
		for(MonthlyStatRowDto row : monthlyRows) {
			rowMap.put(row.getYearMonth(), row);
		}
		
		List<MonthlyStatDto> result = new ArrayList<>();
		
		for (int i = 0; i<6 ; i++) {
			
			YearMonth currentMonth = startMonth.plusMonths(i);
			String key = currentMonth.toString();
			MonthlyStatRowDto row = rowMap.get(key);
			
			long income = row != null ? row.getTotalIncome() : 0;
			long expense = row != null ? row.getTotalExpense() : 0;
			long balance = income - expense;
		
			result.add(new MonthlyStatDto(key, income, expense, balance));
		}

		return result;
	}
	
	public StatisticsResponseDto getStatistics(Integer userNum, int year, int month) {
		// 카테고리 집계는 한 번만 조회하고 지출·수입으로 나눠 묶는다 (같은 표를 두 번 가져올 이유가 없다)
		LocalDate startDate = LocalDate.of(year, month, 1);
		LocalDate endDate = startDate.plusMonths(1);
		List<CategoryStatRowDto> categoryRows = personalStatisticsMapper.findCategoryStats(userNum, startDate, endDate);

		List<CategoryStatDto> expenseList = buildCategoryStats(categoryRows, "E");
		List<CategoryStatDto> incomeList = buildCategoryStats(categoryRows, "I");
		List<MonthlyStatDto> monthlyList = getMonthlyStats(userNum, year, month);
	
		long totalExpense = expenseList.stream().mapToLong(CategoryStatDto::getAmount).sum();
		long totalIncome  = incomeList.stream().mapToLong(CategoryStatDto::getAmount).sum();
		long balance = totalIncome - totalExpense;
		
		// 저축률 = (수입 − 지출) ÷ 수입 × 100 — 번 돈 중 얼마를 남겼나 (10/16 결정, ADR-065 ⑧). 지출이 더 크면 마이너스
		BigDecimal savingsRate;
		if (totalIncome == 0) {
			// 봉투 비율과 달리 여기는 방어가 필요하다.
			// 봉투는 분모(totalAmount)가 0 이면 줄이 없어 나눗셈에 도달하지 않지만,
			// 저축률은 수입이 0 이어도 지출은 있을 수 있어 나눗셈이 무조건 실행된다.
			// 0 을 보내면 "하나도 못 남겼다" 는 거짓이 되므로 "계산 불가" 를 null 로 내려보낸다.
			// 프론트는 null 이면 '-' 로 표시한다 (판단은 서버, 표시는 뷰 - ADR-038).
		    savingsRate = null;
		} else {
		    savingsRate = BigDecimal.valueOf(balance)
		            .multiply(BigDecimal.valueOf(100))
		            .divide(BigDecimal.valueOf(totalIncome), 1, RoundingMode.HALF_UP);
		}
		
		return new StatisticsResponseDto(totalIncome, totalExpense, balance, savingsRate, expenseList, incomeList, monthlyList);
	}
	
}
