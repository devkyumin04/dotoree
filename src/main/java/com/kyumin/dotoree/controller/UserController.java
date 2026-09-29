package com.kyumin.dotoree.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kyumin.dotoree.dto.LoginRequestDto;
import com.kyumin.dotoree.dto.LoginResponseDto;
import com.kyumin.dotoree.dto.LoginResult;
import com.kyumin.dotoree.dto.NicknameRequestDto;
import com.kyumin.dotoree.dto.PasswordChangeRequestDto;
import com.kyumin.dotoree.dto.PasswordConfirmRequestDto;
import com.kyumin.dotoree.dto.SignupRequestDto;
import com.kyumin.dotoree.dto.SignupResponseDto;
import com.kyumin.dotoree.dto.UserInfoResponseDto;
import com.kyumin.dotoree.security.RefreshCookieProvider;
import com.kyumin.dotoree.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
	private final RefreshCookieProvider refreshCookieProvider;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponseDto> signup(@Valid @RequestBody SignupRequestDto requestDto) {

        SignupResponseDto responseDto = userService.signup(requestDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }
    
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto requestDto) {

		LoginResult loginResult = userService.login(requestDto);
		ResponseCookie refreshCookie = refreshCookieProvider.create(loginResult.getRawRefreshToken());

		return ResponseEntity.status(HttpStatus.OK).header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
				.body(loginResult.getLoginResponse());
    }
    
    @GetMapping("/me")
    public ResponseEntity<UserInfoResponseDto> getMyInfo(@AuthenticationPrincipal Integer userNum) {
        UserInfoResponseDto responseDto = userService.getMyInfo(userNum);
        return ResponseEntity.status(HttpStatus.OK).body(responseDto);
    }

    // 마이페이지 관문 — 비밀번호가 맞으면 204. 화면은 이걸 통과해야 내용을 보여준다
    @PostMapping("/me/verify-password")
    public ResponseEntity<Void> verifyPassword(@AuthenticationPrincipal Integer userNum,
                                               @Valid @RequestBody PasswordConfirmRequestDto requestDto) {
        userService.verifyPassword(userNum, requestDto);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // 마이페이지 — 닉네임은 바뀐 내 정보를 돌려준다(화면이 다시 조회하지 않게), 비밀번호는 204
    @PutMapping("/me/nickname")
    public ResponseEntity<UserInfoResponseDto> updateNickname(@AuthenticationPrincipal Integer userNum,
                                                              @Valid @RequestBody NicknameRequestDto requestDto) {
        UserInfoResponseDto responseDto = userService.updateNickname(userNum, requestDto);
        return ResponseEntity.status(HttpStatus.OK).body(responseDto);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal Integer userNum,
                                               @Valid @RequestBody PasswordChangeRequestDto requestDto) {
        userService.changePassword(userNum, requestDto);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // 비밀번호를 본문으로 받으므로 DELETE 가 아니라 POST — DELETE 의 본문은 프록시·클라이언트가 버리기도 한다
    @PostMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Integer userNum,
                                         @Valid @RequestBody PasswordConfirmRequestDto requestDto) {
        userService.withdraw(userNum, requestDto);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}