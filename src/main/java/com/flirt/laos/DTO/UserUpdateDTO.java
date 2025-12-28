package com.flirt.laos.DTO;

import lombok.Getter;

@Getter
public class UserUpdateDTO { // 회원 정보 수정시 전달되는 객체

    private String nickname;

    private String password; // 비밀 번호 변경시 사용
}
