package com.kyumin.dotoree.service;

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
    
	@Transactional
	public LoginResult login(LoginRequestDto requestDto) {

        User loginUser = userMapper.findByEmail(requestDto.getEmail());
      	
        if(loginUser == null) {
        	throw new InvalidCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        boolean isPasswordMatch = passwordEncoder.matches(requestDto.getPassword(), loginUser.getUserPw());

        if (!isPasswordMatch) {
            throw new InvalidCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        // 유예 중(파기 전) 탈퇴 계정의 로그인 = 탈퇴 취소 (ADR-052). 비밀번호 확인 뒤에만 복구한다.
        // 0행이면 그 사이 스케줄러가 파기한 것 — 없는 계정으로 토큰을 내주지 않는다
        if ("W".equals(loginUser.getUserStatus())) {
            if (userMapper.restore(loginUser.getUserNum()) == 0) {
                throw new InvalidCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다.");
            }
        }

        userMapper.updateLastLoginAt(loginUser.getUserNum());
        String rawRefreshToken = refreshTokenService.issueNewFamily(loginUser.getUserNum());
        
		String accessToken = jwtTokenProvider.createAccessToken(loginUser.getUserNum());

		return new LoginResult(
				new LoginResponseDto(loginUser.getUserNum(), loginUser.getUserEmail(), loginUser.getUserNickname(),
						accessToken),
				rawRefreshToken
        );
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
    }

    // 탈퇴 = 논리적 삭제 + 유예 시작. 30일 뒤 purgeUser 가 물리적으로 지운다 (ADR-052)
    // 감수 — 이미 발급된 액세스 토큰은 만료(15분)까지 유효하다. 필터가 매 요청 DB 를 보지 않기 때문(stateless JWT).
    //        그 사이 쓴 데이터도 유예가 끝나면 같이 지워진다
    public void withdraw(Integer userNum, PasswordConfirmRequestDto requestDto) {

        checkPassword(userNum, requestDto.getPassword(), "비밀번호가 일치하지 않습니다.");

        // 'A' 인 계정만 바뀐다. 0행 = 이미 탈퇴한 계정의 남은 토큰 → 없는 사용자로 취급
        if (userMapper.withdraw(userNum) == 0) {
            throw new InvalidCredentialsException("사용자를 찾을 수 없습니다.");
        }
    }

    // 유예가 끝난 계정 하나를 물리적으로 지운다. 스케줄러가 계정마다 부른다 — 계정 하나가 실패해도 다른 계정은 지워지게 트랜잭션을 계정 단위로
    @Transactional
    public boolean purgeUser(Integer userNum) {

        // 목록을 뽑은 뒤 로그인으로 복구됐으면 건너뛴다. 동시에 행을 잠가 복구와 겹치지 않게
        if (userMapper.lockPurgeTarget(userNum, User.WITHDRAW_GRACE_DAYS) == null) {
            return false;
        }

        // FK 순서 — 거래 → 소분류 → 나머지 카테고리 → 계정 (이유와 "새 테이블이 생기면" 은 UserMapper.xml)
        userMapper.deleteTransactionsOf(userNum);
        userMapper.deleteChildCategoriesOf(userNum);
        userMapper.deleteCategoriesOf(userNum);
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