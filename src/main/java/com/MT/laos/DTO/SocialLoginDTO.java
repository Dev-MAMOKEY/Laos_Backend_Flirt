package com.MT.laos.DTO;

import lombok.Getter;

@Getter
public class SocialLoginDTO {

    private String socialId;

    private String email;

    private String name;

    private String provider; // ex) google
}
