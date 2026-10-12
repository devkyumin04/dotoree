package com.kyumin.dotoree.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import com.kyumin.dotoree.domain.PersonalCategory;
import com.kyumin.dotoree.domain.PersonalTransaction;
import com.kyumin.dotoree.dto.PersonalTransactionRequestDto;
import com.kyumin.dotoree.dto.PersonalTransactionResponseDto;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mapper.PersonalCategoryMapper;
import com.kyumin.dotoree.mapper.PersonalTransactionMapper;

@Service
@RequiredArgsConstructor
public class PersonalTransactionService {

	/*
	 * ============================================================
	 *  이 클래스의 역할 : 판단은 전부 여기.
	 *    Controller = HTTP 입출력만 / Mapper = SQL만 / Service = 규칙
	 *
	 *  확정된 설계 (docs/decisions.md 참고)
	 *    - 예외는 ErrorCode 로 구분 : TRANSACTION_NOT_FOUND / TRANSACTION_ACCESS_DENIED (ADR-062)
	 *                              / TransactionConflict  (ADR-035)
	 *    - insert 직후 version 은 서비스에서 0 을 직접 세팅  (ADR-036)
	 *    - TRANS_TYPE 은 클라이언트가 안 보냄. 카테고리에서 파생  (ADR-008)
	 *    - 낙관적 락 : affected rows 0 이면 충돌 → 409         (ADR-006)
	 * ============================================================
	 */

	private final PersonalTransactionMapper personalTransactionMapper;
	private final PersonalCategoryMapper personalCategoryMapper;

	public List<PersonalTransactionResponseDto> getTransactions(Integer userNum, int year, int month) {
		LocalDate startDate = LocalDate.of(year, month, 1);
		LocalDate endDate = startDate.plusMonths(1);
		
		return personalTransactionMapper.findByMonth(userNum, startDate, endDate);
	}


	/* ── ② 등록 ───────────────────────────────────────────────────
	 *
	 * [카테고리 검증]
	 * 1. personalCategoryMapper.findByCategoryNum(requestDto.getCategoryNum())
	 * 2. null 이면            → CATEGORY_NOT_FOUND          (404)
	 * 3. 내 카테고리가 아니면 → CATEGORY_ACCESS_DENIED      (403)
	 *      category.getUserNum() 과 userNum 비교
	 *
	 * [도메인 객체 만들기]
	 * 4. new PersonalTransaction() 후 값 채우기
	 *      userNum / categoryNum / transAmount / transDate / transMemo
	 *
	 *    ★ transType = category.getCategoryType()
	 *        requestDto 에는 transType 이 없다. 위에서 조회한 카테고리에서 꺼낸다.
	 *        이렇게 하면 "지출 카테고리인데 수입으로 등록" 같은 불일치가 불가능해진다.
	 *
	 *    ★ version = 0
	 *        INSERT 문에 version 컬럼이 없어서 DB 의 DEFAULT 0 이 채운다.
	 *        그런데 useGeneratedKeys 는 PK(transNum) 하나만 자바 객체로 가져오므로
	 *        이 객체의 version 은 null 로 남는다.
	 *        그대로 응답하면 {"version": null} 이 나가고,
	 *        프론트가 등록 직후 바로 수정하려 할 때 ?version=null 로 깨진다.
	 *        → DDL 의 DEFAULT 와 같은 0 을 여기서 직접 넣어준다.
	 *
	 * [저장]
	 * 5. personalTransactionMapper.insertTransaction(t)
	 *      useGeneratedKeys 로 t.transNum 이 자동으로 채워진다
	 *
	 * [응답]
	 * 6. ResponseDto 로 변환해서 반환
	 *      categoryName / categoryEmoji 는 1번에서 조회한 category 객체에서 꺼낸다.
	 *      재조회할 필요 없다.
	 * ───────────────────────────────────────────────────────────── */
	public PersonalTransactionResponseDto createTransaction(Integer userNum,
			PersonalTransactionRequestDto requestDto) {
		
		PersonalCategory category = personalCategoryMapper.findByCategoryNum(requestDto.getCategoryNum());
		
		if(category == null) {
			throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
		}
		
		if(!category.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.CATEGORY_ACCESS_DENIED);
		}
		
		PersonalTransaction transaction = new PersonalTransaction();
		transaction.setCategoryNum(requestDto.getCategoryNum());
		transaction.setTransAmount(requestDto.getTransAmount());
		transaction.setTransDate(requestDto.getTransDate());
		transaction.setTransMemo(requestDto.getTransMemo());
		transaction.setUserNum(userNum);
		transaction.setTransType(category.getCategoryType());
		transaction.setVersion(0);
		
		personalTransactionMapper.insertTransaction(transaction);
		
		PersonalTransactionResponseDto responseDto = new PersonalTransactionResponseDto();
		responseDto.setTransNum(transaction.getTransNum());
		responseDto.setVersion(transaction.getVersion());
		responseDto.setTransAmount(transaction.getTransAmount());
		responseDto.setTransDate(transaction.getTransDate());
		responseDto.setTransMemo(transaction.getTransMemo());
		responseDto.setTransType(transaction.getTransType());
		responseDto.setCategoryNum(category.getCategoryNum());
		
		responseDto.setCategoryName(category.getCategoryName());
		responseDto.setCategoryEmoji(category.getCategoryEmoji());
		
		return responseDto;
	}


	/* ── ③ 삭제 (소프트 딜리트) ───────────────────────────────────
	 *
	 * [거래 검증]
	 * 1. personalTransactionMapper.findByTransNum(transNum)
	 * 2. null 이면          → TRANSACTION_NOT_FOUND          (404)
	 *      매퍼가 use_yn = 'Y' 로 거르므로 이미 삭제된 건도 여기서 null 이 된다
	 * 3. 내 거래가 아니면   → TRANSACTION_ACCESS_DENIED      (403)
	 *
	 * [실행 + 충돌 판정]
	 * 4. int affected = personalTransactionMapper.deleteTransaction(transNum, version)
	 * 5. affected == 0 이면 → TRANSACTION_CONFLICT           (409)
	 *
	 *    0 이 의미하는 것
	 *      3번에서 거래가 존재하는 걸 이미 확인했다.
	 *      그런데도 0 행이라는 건 version 조건만 안 맞았다는 뜻이고,
	 *      곧 "내가 조회한 뒤 누군가 먼저 수정/삭제했다" 는 신호다.
	 *      예외가 아니라 조용히 0 이 오므로, 이 숫자를 안 보면 감지할 방법이 없다.
	 * ───────────────────────────────────────────────────────────── */
	public void deleteTransaction(Integer userNum, Integer transNum, Integer version) {
		
		PersonalTransaction transaction = personalTransactionMapper.findByTransNum(transNum);
		
		if(transaction == null) {
			throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND);
		}
		
		if(!transaction.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.TRANSACTION_ACCESS_DENIED);
		}
		
		int affected = personalTransactionMapper.deleteTransaction(transNum, version);
		
		if(affected == 0) {
			throw new BusinessException(ErrorCode.TRANSACTION_CONFLICT);
		}
		
	}


	/* ── ④ 수정 ───────────────────────────────────────────────────
	 *
	 * [거래 검증]
	 * 1. findByTransNum → null 이면 TRANSACTION_NOT_FOUND      (404)
	 * 2. 내 거래가 아니면          TRANSACTION_ACCESS_DENIED  (403)
	 *
	 * [카테고리 검증]  ← 바꿀 카테고리도 검증해야 한다
	 * 3. findByCategoryNum → null 이면 CATEGORY_NOT_FOUND      (404)
	 * 4. 내 카테고리가 아니면         CATEGORY_ACCESS_DENIED  (403)
	 *
	 * [값 채우기]
	 * 5. 1번에서 조회한 거래 객체에 requestDto 값들을 세팅
	 *      categoryNum / transAmount / transDate / transMemo
	 *
	 *    ★ transType = category.getCategoryType()
	 *        등록과 같은 이유. 카테고리를 바꾸면 타입도 따라 바뀐다.
	 *
	 *    ★ setVersion( 파라미터로 받은 version )
	 *        여기가 이 기능에서 제일 틀리기 쉬운 지점이다.
	 *        1번에서 조회한 객체의 version 은 "DB 의 현재값" 이다.
	 *        그걸 그대로 두고 update 하면 WHERE version = 자기자신 이 되어
	 *        항상 1행이 성공한다 → 낙관적 락이 통째로 무력화된다.
	 *        코드는 멀쩡해 보이고 단위 테스트도 통과한다.
	 *        반드시 클라이언트가 들고 온 version 으로 덮어쓴다.
	 *
	 * [실행 + 충돌 판정]
	 * 6. int affected = personalTransactionMapper.updateTransaction(t)
	 * 7. affected == 0 이면 → TRANSACTION_CONFLICT            (409)
	 *
	 * [응답]
	 * 8. ResponseDto 로 변환해서 반환
	 *      ★ version 은 DB 에서 +1 되었는데 자바 객체에는 반영되지 않는다.
	 *        응답에 version + 1 을 담아야 프론트가 연속으로 수정할 수 있다.
	 *        (등록 때 version = 0 을 직접 넣은 것과 같은 이유)
	 *      categoryName / categoryEmoji 는 3번에서 조회한 category 에서 꺼낸다.
	 * ───────────────────────────────────────────────────────────── */
	public PersonalTransactionResponseDto updateTransaction(Integer userNum, Integer transNum,
			Integer version, PersonalTransactionRequestDto requestDto) {
		
		PersonalTransaction transaction = personalTransactionMapper.findByTransNum(transNum);
		
		if(transaction == null) {
			throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND);
		}
		
		if(!transaction.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.TRANSACTION_ACCESS_DENIED);
		}
		
		PersonalCategory category = personalCategoryMapper.findByCategoryNum(requestDto.getCategoryNum());
		
		if(category == null) {
			throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
		}
		
		if(!category.getUserNum().equals(userNum)) {
			throw new BusinessException(ErrorCode.CATEGORY_ACCESS_DENIED);
		}
		
		transaction.setCategoryNum(requestDto.getCategoryNum());
		transaction.setTransAmount(requestDto.getTransAmount());
		transaction.setTransDate(requestDto.getTransDate());
		transaction.setTransMemo(requestDto.getTransMemo());
		transaction.setTransType(category.getCategoryType());
		transaction.setVersion(version);
		
		int affected = personalTransactionMapper.updateTransaction(transaction);
		
		if(affected == 0) {
			throw new BusinessException(ErrorCode.TRANSACTION_CONFLICT);
		}
		
		PersonalTransactionResponseDto responseDto = new PersonalTransactionResponseDto();
		responseDto.setTransNum(transaction.getTransNum());
		responseDto.setVersion(transaction.getVersion()+1);
		responseDto.setTransAmount(transaction.getTransAmount());
		responseDto.setTransDate(transaction.getTransDate());
		responseDto.setTransMemo(transaction.getTransMemo());
		responseDto.setTransType(transaction.getTransType());
		responseDto.setCategoryName(category.getCategoryName());
		responseDto.setCategoryEmoji(category.getCategoryEmoji());
		responseDto.setCategoryNum(transaction.getCategoryNum());
		
		return responseDto;
	}
}
