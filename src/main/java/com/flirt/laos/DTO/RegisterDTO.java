package com.flirt.laos.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterDTO {

    private String localId;

    private String password;

    private String nickname;

    private String email;

    private String emailCode; // 이메일 인증 코드
}
