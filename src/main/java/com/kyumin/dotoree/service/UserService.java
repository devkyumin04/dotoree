package com.kyumin.dotoree.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kyumin.dotoree.domain.PersonalCategory;
import com.kyumin.dotoree.domain.User;
import com.kyumin.dotoree.dto.LoginRequestDto;
import com.kyumin.dotoree.dto.LoginResponseDto;
import com.kyumin.dotoree.dto.LoginResult;
import com.kyumin.dotoree.dto.NicknameRequestDto;
import com.kyumin.dotoree.dto.PasswordChangeRequestDto;
import com.kyumin.dotoree.dto.PasswordConfirmRequestDto;
import com.kyumin.dotoree.dto.SignupRequestDto;
import com.kyumin.dotoree.dto.SignupResponseDto;
import com.kyumin.dotoree.dto.UserInfoResponseDto;
import com.kyumin.dotoree.exception.BusinessException;
import com.kyumin.dotoree.exception.ErrorCode;
import com.kyumin.dotoree.mapper.PersonalCategoryMapper;
import com.kyumin.dotoree.mapper.UserMapper;
import com.kyumin.dotoree.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PersonalCategoryMapper personalCategoryMapper;
	private final RefreshTokenService refreshTokenService;
	private final LoginAttemptService loginAttemptService;
	private final Clock clock;

	private static final int MAX_FAIL_COUNT = 5;
	private static final int LOCK_MINUTES = 30;
    @Transactional
    public SignupResponseDto signup(SignupRequestDto requestDto) {

        User existingUser = userMapper.findByEmail(requestDto.getEmail());

        if (existingUser != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());
        
        User newUser = new User();
        newUser.setUserEmail(requestDto.getEmail());
        newUser.setUserPw(encodedPassword);
        newUser.setUserNickname(requestDto.getNickname());
        newUser.setUserStatus("A");
        newUser.setBookOpenYn("N");
        newUser.setLoginFailCount(0);
        
		try {
			userMapper.insertUser(newUser);
		} catch (DuplicateKeyException e) {
			throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
		}
        
        createDefaultCategory(newUser.getUserNum(), "E");
        createDefaultCategory(newUser.getUserNum(), "I");
        
        return new SignupResponseDto(
    	    newUser.getUserNum(),
    	    newUser.getUserEmail(),
    	    newUser.getUserNickname()
    	);
    }
    
    private void createDefaultCategory(Integer userNum, String categoryType) {
    	
    	PersonalCategory category = new PersonalCategory();
        category.setUserNum(userNum);
        category.setParentCategoryNum(null);
        category.setCategoryName(PersonalCategory.DEFAULT_CATEGORY_NAME);
        category.setCategoryEmoji(null);
        category.setCategoryType(categoryType);
        category.setIsDefaultYn("Y");
        category.setUseYn("Y");
        personalCategoryMapper.insertCategory(category);
    }
    
	// 실패 횟수를 올린 뒤 401·423 을 던진다. 예외 = 롤백이라, 여기서 +1 을 쓰면 되돌려져 잠금이 영영 안 걸린다
	// → 실패 기록(잠금 풀기 · +1 · 잠금)은 LoginAttemptService 가 따로 커밋하고, 이 메서드는 보통 트랜잭션 (ADR-062)
	@Transactional
	public LoginResult login(LoginRequestDto requestDto) {

        User loginUser = userMapper.findByEmail(requestDto.getEmail());

        if(loginUser == null) {
        	throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

		LocalDateTime now = LocalDateTime.now(clock);

		if (loginUser.isLockedAt(now)) {
			throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, buildLockedMessage(loginUser.getLockedUntil(), now));
		} else if (loginUser.getLockedUntil() != null) {
			loginAttemptService.resetLoginFail(loginUser.getUserNum());
		}

        boolean isPasswordMatch = passwordEncoder.matches(requestDto.getPassword(), loginUser.getUserPw());

        if (!isPasswordMatch) {
			LocalDateTime lockedUntil = now.plusMinutes(LOCK_MINUTES);
			boolean locked = loginAttemptService.recordFailure(loginUser.getUserNum(), lockedUntil, MAX_FAIL_COUNT);
			if (locked) {
				throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, buildLockedMessage(lockedUntil, now));
			}

			throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

		// 유예 중(파기 전) 탈퇴 계정의 로그인 = 탈퇴 취소 (ADR-052). 비밀번호 확인 뒤에만 복구한다.
		// 0행이면 그 사이 스케줄러가 파기한 것 — 없는 계정으로 토큰을 내주지 않는다
		if ("W".equals(loginUser.getUserStatus())) {
			int restoredRows = userMapper.restore(loginUser.getUserNum());

			if (restoredRows == 0) {
				throw new BusinessException(ErrorCode.LOGIN_FAILED);
			}
		}

		userMapper.resetLoginFail(loginUser.getUserNum());

        userMapper.updateLastLoginAt(loginUser.getUserNum(), now);
        String rawRefreshToken = refreshTokenService.issueNewFamily(loginUser.getUserNum());
        
		String accessToken = jwtTokenProvider.createAccessToken(loginUser.getUserNum());

		return new LoginResult(
				new LoginResponseDto(loginUser.getUserNum(), loginUser.getUserEmail(), loginUser.getUserNickname(),
						accessToken),
				rawRefreshToken
        );
    }

	private String buildLockedMessage(LocalDateTime lockedUntil, LocalDateTime now) {
		long remainingSeconds = Duration.between(now, lockedUntil).toSeconds();
		long remainingMinutes = (long) Math.ceil(remainingSeconds / 60.0);
		return "로그인 " + MAX_FAIL_COUNT + "회 실패로 계정이 잠겼습니다. " + remainingMinutes + "분 뒤 다시 시도해 주세요.";
	}

    // ── 마이페이지 ─────────────────────────────────────
	// 마이페이지 들어가기 전 비밀번호 확인 — 화면의 관문. 통과하면 204
	// 막는 것: 로그인된 채 둔 화면을 남이 여는 것. 토큰을 가진 사람이 API 를 직접 부르는 건 못 막는다 —
	//         그래서 비밀번호 변경·탈퇴는 이 관문과 별개로 각자 비밀번호를 다시 받는다 (닉네임은 피해가 작아 받지 않는다)
	public void verifyPassword(Integer userNum, PasswordConfirmRequestDto requestDto) {
		checkPassword(userNum, requestDto.getPassword(), ErrorCode.PASSWORD_MISMATCH);
	}

    public UserInfoResponseDto updateNickname(Integer userNum, NicknameRequestDto requestDto) {

        if (userMapper.updateNickname(userNum, requestDto.getNickname()) == 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return getMyInfo(userNum);
    }

	// 비밀번호 변경 — 거절(현재 비번 확인 · 같은 비번) → 준비(암호화) → 바꾸기 → 뒷정리(전 세션 폐기)
	// 암호화는 거절이 다 끝난 뒤 — BCrypt 는 일부러 느린 연산이라 돌려보낼 요청에 쓰지 않는다
	// 바꾼 뒤 모든 기기의 Refresh 폐기 — 비번을 바꾸는 흔한 이유가 "누가 내 계정을 쓰는 것 같다"라서, 옛 카드를 든 사람을 같이 내보낸다 (ADR-055 선택 16)
	// @Transactional — 비번만 바뀌고 폐기가 실패하면 도둑의 카드가 산다. 둘 다 되거나 둘 다 안 되게(실패하면 사용자가 다시 시도)
	@Transactional
	public void changePassword(Integer userNum, PasswordChangeRequestDto requestDto) {
		checkPassword(userNum, requestDto.getCurrentPassword(), ErrorCode.CURRENT_PASSWORD_MISMATCH);
		
		// 현재 비번은 바로 위에서 맞다고 확인됐으니 원문끼리 비교하면 된다 (해시를 다시 돌릴 필요 없음).
		// 왼쪽에 현재 비번 — 위 확인을 통과했으니 null 이 아니다 (@Valid @NotBlank 도 막는다)
		if (requestDto.getCurrentPassword().equals(requestDto.getNewPassword())) {
			throw new BusinessException(ErrorCode.SAME_PASSWORD);
		}
		
		String encodedPassword = passwordEncoder.encode(requestDto.getNewPassword());
		
		// 0행 = 확인과 UPDATE 사이에 탈퇴·파기된 계정 → 없는 사용자로 취급(401)
		int updatedRows = userMapper.updatePassword(userNum, encodedPassword);
		if (updatedRows == 0) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}

		refreshTokenService.revokeAllOfUser(userNum);
	}

	// 탈퇴 = 논리적 삭제 + 유예 시작. 30일 뒤 purgeUser 가 물리적으로 지운다 (ADR-052)
	// 감수 — 이미 발급된 액세스 토큰은 만료(15분)까지 유효하다. 필터가 매 요청 DB 를 보지 않기 때문(stateless JWT).
	//        그 사이 쓴 데이터도 유예가 끝나면 같이 지워진다
	// Refresh 는 전부 폐기 — 안 하면 유예 30일 동안 옛 카드로 재발급이 계속된다(재발급은 계정 상태를 보지 않는다). 다시 쓰려면 로그인 = 탈퇴 취소 (ADR-052·055 선택 16)
	// @Transactional — 상태만 'W' 가 되고 폐기가 실패하는 반쪽 탈퇴를 막는다
	@Transactional
	public void withdraw(Integer userNum, PasswordConfirmRequestDto requestDto) {

		checkPassword(userNum, requestDto.getPassword(), ErrorCode.PASSWORD_MISMATCH);

		// 'A' 인 계정만 바뀐다. 0행 = 이미 탈퇴한 계정의 남은 토큰 → 없는 사용자로 취급
		int withdrawnRows = userMapper.withdraw(userNum, LocalDateTime.now(clock));
		if (withdrawnRows == 0) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}
		refreshTokenService.revokeAllOfUser(userNum);
	}

	// 유예가 끝난 계정 하나를 물리적으로 지운다. 스케줄러가 계정마다 부른다 — 트랜잭션을 계정 단위로 둬 한 계정이 실패해도 다른 계정은 지워지게
	// true = 지움 · false = 그 사이 복구·재탈퇴돼 건너뜀 · 실패는 예외로 던진다(여기서 잡으면 롤백이 안 된다 — 스케줄러가 잡아 로그)
	@Transactional
	public boolean purgeUser(Integer userNum) {
		// 재확인 + 행 잠금 — 잠금은 이 트랜잭션이 끝날 때까지라 그 사이 로그인(restore)은 기다린다
		if (userMapper.lockPurgeTarget(userNum, LocalDateTime.now(clock).minusDays(User.WITHDRAW_GRACE_DAYS)) == null) {
			return false;
		}

		// FK 순서 — 가계부(거래 → 소분류 → 대분류) → 계정에 딸린 것(토큰) → 계정. 이유와 "새 테이블이 생기면" 은 UserMapper.xml
		userMapper.deleteTransactionsOf(userNum);
		userMapper.deleteChildCategoriesOf(userNum);
		userMapper.deleteCategoriesOf(userNum);
		userMapper.deleteRefreshTokensOf(userNum);
		userMapper.deleteUser(userNum);
		return true;
	}

	// 로그인한 사용자의 비밀번호 재확인 — 관문·비번 변경·탈퇴가 같이 쓴다
	// void + 예외 — boolean 을 돌려주면 부르는 쪽마다 if 가 필요하고, 하나라도 빠뜨리면 틀린 비번이 조용히 통과한다
	// 비번 틀림은 400(PASSWORD_MISMATCH) — 401 이면 apiClient.js 가 만료로 보고 재발급·재시도 뒤 로그인 화면으로 보낸다(오타 한 번에 로그아웃)
	// 계정 없음(탈퇴·파기 뒤 남은 토큰)은 401 — 그땐 로그인 화면으로 보내는 게 맞다
	// 어느 ErrorCode 인지는 부르는 쪽이 정한다 — 비번 변경은 칸이 둘(현재·새)이라 "현재 비밀번호가…" 로 어느 칸인지 알린다
	private void checkPassword(Integer userNum, String rawPassword, ErrorCode mismatchCode) {

		User user = userMapper.findByUserNum(userNum);

		if (user == null) {
			throw new BusinessException(ErrorCode.USER_NOT_FOUND);
		}

		if (!passwordEncoder.matches(rawPassword, user.getUserPw())) {
			throw new BusinessException(mismatchCode);
		}
	}

    public UserInfoResponseDto getMyInfo(Integer userNum) {

        User user = userMapper.findByUserNum(userNum);

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return new UserInfoResponseDto(
            user.getUserEmail(),
            user.getUserNickname()
        );
    }

}