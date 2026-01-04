package com.Flirt.laos.DTO;

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
}
