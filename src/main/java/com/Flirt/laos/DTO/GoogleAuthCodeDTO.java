package com.Flirt.laos.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프론트엔드에서 전달하는 구글 인가 코드 요청 DTO
 * - code : Google OAuth2 인가 코드
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthCodeDTO {

    private String code;
}
