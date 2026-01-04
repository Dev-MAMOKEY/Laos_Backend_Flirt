package com.Flirt.laos.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LocalLoginDTO { // 로컬 로그인시 전달되는 객체

    private String localId;

    private String password;
}
