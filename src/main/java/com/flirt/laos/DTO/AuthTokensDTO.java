package com.flirt.laos.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokensDTO { // 로그인 성공시 AT/RT 클라이언트에게 전달

    private String accessToken;

    private String refreshToken;
}
