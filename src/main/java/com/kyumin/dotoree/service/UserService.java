package com.kyumin.dotoree.service;

import java.time.Duration;
import java.time.LocalDateTime;

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
import com.kyumin.dotoree.exception.AccountLockedException;
import com.kyumin.dotoree.exception.DuplicateEmailException;
import com.kyumin.dotoree.exception.InvalidCredentialsException;
import com.kyumin.dotoree.exception.PasswordMismatchException;
import com.kyumin.dotoree.exception.SamePasswordException;
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

    // ── 로그인 잠금 (ADR-057) — 계정 기준 5회 실패 → 30분. 성공하면 0 부터. 값은 여기 한 곳(환경마다 다르지 않고 비밀도 아니다)
    private static final int MAX_FAIL_COUNT = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    // 없음·불일치·파기됨을 한 문구로 — 어느 쪽인지 알려 주지 않는다 (계정 존재 비노출은 가입 409 때문에 이미 반쪽이지만, 굳이 더 열지 않는다)
    private static final String LOGIN_FAIL_MESSAGE = "이메일 또는 비밀번호가 일치하지 않습니다.";

    @Transactional
    public SignupResponseDto signup(SignupRequestDto requestDto) {

        User existingUser = userMapper.findByEmail(requestDto.getEmail());

        if (existingUser != null) {
            throw new DuplicateEmailException("이미 사용 중인 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());
        
        User newUser = new User();
        newUser.setUserEmail(requestDto.getEmail());
        newUser.setUserPw(encodedPassword);
        newUser.setUserNickname(requestDto.getNickname());
        newUser.setUserStatus("A");
        newUser.setBookOpenYn("N");
        newUser.setLoginFailCount(0);
        
        userMapper.insertUser(newUser);
        
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
    
	// noRollbackFor — 실패 횟수를 올리고 나서 401·423 을 던진다. 기본값이면 그 UPDATE 가 롤백돼 잠금이 영영 안 걸린다.
	// 여기서 두 예외는 고장이 아니라 "거절" — 거절 기록은 커밋, 고장(DB 오류 등)만 롤백 (RefreshTokenService.refresh 와 같은 생각, ADR-055 선택 13)
	@Transactional(noRollbackFor = { InvalidCredentialsException.class, AccountLockedException.class })
	public LoginResult login(LoginRequestDto requestDto) {

        User loginUser = userMapper.findByEmail(requestDto.getEmail());
      	
        if(loginUser == null) {
            // 없는 이메일은 세지 않는다 — 셀 계정이 없다. 잠금은 계정 기준이라 IP 로 세는 장치는 두지 않았다 (ADR-057)
        	throw new InvalidCredentialsException(LOGIN_FAIL_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now();

        // 잠금 검사는 비밀번호보다 먼저 — 잠긴 동안은 맞는 비밀번호도 거절(시도 자체를 막는 게 목적). 세지도 않는다(잠금을 늘리면 남이 잠그는 피해가 커진다)
        if (loginUser.getLockedUntil() != null) {
            if (loginUser.getLockedUntil().isAfter(now)) {
                throw new AccountLockedException(lockedMessage(loginUser.getLockedUntil(), now));
            }
            // 잠금이 풀렸다 — 0 부터 다시 센다. 안 그러면 30분 뒤 한 번만 틀려도 바로 다시 잠긴다
            userMapper.resetLoginFail(loginUser.getUserNum());
        }

        boolean isPasswordMatch = passwordEncoder.matches(requestDto.getPassword(), loginUser.getUserPw());

        if (!isPasswordMatch) {
            userMapper.increaseLoginFailCount(loginUser.getUserNum());
            // 잠글지는 DB 의 현재 횟수로(매퍼 WHERE). 1행이면 이번 실패로 잠긴 것 → 423, 0행이면 아직 → 401
            LocalDateTime lockedUntil = now.plus(LOCK_DURATION);
            if (userMapper.lockIfFailedTooMany(loginUser.getUserNum(), MAX_FAIL_COUNT, lockedUntil) == 1) {
                throw new AccountLockedException(lockedMessage(lockedUntil, now));
            }
            throw new InvalidCredentialsException(LOGIN_FAIL_MESSAGE);
        }

        // 유예 중(파기 전) 탈퇴 계정의 로그인 = 탈퇴 취소 (ADR-052). 비밀번호 확인 뒤에만 복구한다.
        // 0행이면 그 사이 스케줄러가 파기한 것 — 없는 계정으로 토큰을 내주지 않는다
        if ("W".equals(loginUser.getUserStatus())) {
            if (userMapper.restore(loginUser.getUserNum()) == 0) {
                throw new InvalidCredentialsException(LOGIN_FAIL_MESSAGE);
            }
        }

        userMapper.updateLastLoginAt(loginUser.getUserNum());
        userMapper.resetLoginFail(loginUser.getUserNum());   // 성공 = 실패 횟수 초기화 (ADR-057)
        String rawRefreshToken = refreshTokenService.issueNewFamily(loginUser.getUserNum());
        
		String accessToken = jwtTokenProvider.createAccessToken(loginUser.getUserNum());

		return new LoginResult(
				new LoginResponseDto(loginUser.getUserNum(), loginUser.getUserEmail(), loginUser.getUserNickname(),
						accessToken),
				rawRefreshToken
        );
    }

    // "N분 뒤" 는 올림 — 29분 1초 남았으면 30분. 1분 미만도 1분 (0분이라 하면 바로 되는 줄 안다)
    private String lockedMessage(LocalDateTime lockedUntil, LocalDateTime now) {
        long minutes = Math.max(1, Duration.between(now, lockedUntil).plusSeconds(59).toMinutes());
        return "로그인 " + MAX_FAIL_COUNT + "회 실패로 계정이 잠겼습니다. " + minutes + "분 뒤 다시 시도해 주세요.";
    }
    
    // ── 마이페이지 ─────────────────────────────────────
    // 마이페이지 들어가기 전 비밀번호 확인 — 화면의 관문. 통과하면 204
    // 막는 것: 로그인된 채 둔 화면을 남이 여는 것. 토큰을 가진 사람이 API 를 직접 부르는 건 못 막는다 —
    //         그래서 비밀번호 변경·탈퇴는 이 관문과 별개로 각자 비밀번호를 다시 받는다 (닉네임은 피해가 작아 받지 않는다)
    public void verifyPassword(Integer userNum, PasswordConfirmRequestDto requestDto) {
        checkPassword(userNum, requestDto.getPassword(), "비밀번호가 일치하지 않습니다.");
    }

    public UserInfoResponseDto updateNickname(Integer userNum, NicknameRequestDto requestDto) {

        if (userMapper.updateNickname(userNum, requestDto.getNickname()) == 0) {
            throw new InvalidCredentialsException("사용자를 찾을 수 없습니다.");
        }
        return getMyInfo(userNum);
    }

    // 현재 비밀번호를 확인한 뒤에만 바꾼다. 토큰이 있어도 비번을 모르면 못 바꾼다 (탈퇴와 같은 이유)
    // 바꾼 뒤 모든 기기의 Refresh 폐기 — 비번을 바꾸는 흔한 이유가 "누가 내 계정을 쓰는 것 같다"라서, 옛 카드를 든 사람을 같이 내보낸다 (ADR-055 선택 16)
    // @Transactional — 비번만 바뀌고 폐기가 실패하면 도둑의 카드가 산다. 둘 다 되거나 둘 다 안 되게(실패하면 사용자가 다시 시도)
    @Transactional
    public void changePassword(Integer userNum, PasswordChangeRequestDto requestDto) {

        checkPassword(userNum, requestDto.getCurrentPassword(), "현재 비밀번호가 일치하지 않습니다.");

        // 현재 비번은 바로 위에서 맞다고 확인됐으니, 새 비번과 문자열로 비교하면 된다 (해시를 다시 돌릴 필요 없음)
        if (requestDto.getNewPassword().equals(requestDto.getCurrentPassword())) {
            throw new SamePasswordException("현재 비밀번호와 다른 비밀번호를 입력해주세요.");
        }

        String encoded = passwordEncoder.encode(requestDto.getNewPassword());
        if (userMapper.updatePassword(userNum, encoded) == 0) {
            throw new InvalidCredentialsException("사용자를 찾을 수 없습니다.");
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

        checkPassword(userNum, requestDto.getPassword(), "비밀번호가 일치하지 않습니다.");

        // 'A' 인 계정만 바뀐다. 0행 = 이미 탈퇴한 계정의 남은 토큰 → 없는 사용자로 취급
        if (userMapper.withdraw(userNum) == 0) {
            throw new InvalidCredentialsException("사용자를 찾을 수 없습니다.");
        }
        refreshTokenService.revokeAllOfUser(userNum);
    }

    // 유예가 끝난 계정 하나를 물리적으로 지운다. 스케줄러가 계정마다 부른다 — 계정 하나가 실패해도 다른 계정은 지워지게 트랜잭션을 계정 단위로
    @Transactional
    public boolean purgeUser(Integer userNum) {

        // 목록을 뽑은 뒤 로그인으로 복구됐으면 건너뛴다. 동시에 행을 잠가 복구와 겹치지 않게
        if (userMapper.lockPurgeTarget(userNum, User.WITHDRAW_GRACE_DAYS) == null) {
            return false;
        }

        // FK 순서 — 거래 → 소분류 → 나머지 카테고리 → Refresh 장부 → 계정 (이유와 "새 테이블이 생기면" 은 UserMapper.xml)
        userMapper.deleteTransactionsOf(userNum);
        userMapper.deleteChildCategoriesOf(userNum);
        userMapper.deleteCategoriesOf(userNum);
		userMapper.deleteRefreshTokensOf(userNum);
        userMapper.deleteUser(userNum);
        return true;
    }

    // 로그인한 사용자의 비밀번호 재확인 — 관문·비번 변경·탈퇴가 같이 쓴다.
    // 틀리면 400(PasswordMismatchException). 401 이면 apiClient.js 가 로그아웃시킨다
    private void checkPassword(Integer userNum, String rawPassword, String mismatchMessage) {

        User user = userMapper.findByUserNum(userNum);

        if (user == null) {
            throw new InvalidCredentialsException("사용자를 찾을 수 없습니다.");
        }

        if (!passwordEncoder.matches(rawPassword, user.getUserPw())) {
            throw new PasswordMismatchException(mismatchMessage);
        }
    }

    public UserInfoResponseDto getMyInfo(Integer userNum) {

        User user = userMapper.findByUserNum(userNum);

        if (user == null) {
            throw new InvalidCredentialsException("사용자를 찾을 수 없습니다.");
        }

        return new UserInfoResponseDto(
            user.getUserEmail(),
            user.getUserNickname()
        );
    }

}