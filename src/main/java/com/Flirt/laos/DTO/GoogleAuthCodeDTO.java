package com.Flirt.laos.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

/**
 * 프론트엔드에서 전달하는 구글 인가 코드 요청 DTO
 * - code : Google OAuth2 인가 코드
 * - redirectUri : 프론트엔드에서 Google 로그인 시 사용한 redirect_uri (선택적, 없으면 설정값 사용)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthCodeDTO {

    private String code;
    private String redirectUri; // 프론트엔드에서 사용한 redirect_uri
}

