package com.kyumin.dotoree.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 내 정보 — GET /api/users/me · PATCH /api/users/me/nickname 의 응답
// 로그인 응답(LoginResponseDto)과 나눈 이유: 로그인 응답은 Refresh 토큰 도입으로 모양이 바뀌는데, 내 정보까지 같이 흔들리지 않게.
// 전엔 같은 DTO 를 써서 /me 가 accessToken: null 을 내려보냈다. userNum 은 화면이 쓰지 않아 뺐다(내부 번호를 밖에 내지 않는다)
@Getter
@AllArgsConstructor
public class UserInfoResponseDto {
    private String email;
    private String nickname;
}
