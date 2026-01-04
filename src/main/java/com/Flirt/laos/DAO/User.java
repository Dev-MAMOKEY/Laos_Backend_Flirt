package com.Flirt.laos.DAO;

import jakarta.persistence.*;
import lombok.*;

import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_num") // PK, 사용자 고유 번호(자동으로 1씩 증가함)
    private Integer userNum;

    @Column(name = "local_id") // 로컬 로그인시 사용자가 입력하는 아이디
    private String localId;

    @Column(name= "password") // 로컬 로그인시 사용자가 입력하는 비밀번호
    private String password;

    @Column(name = "nickname") // 사용자가 입력하는 닉네임
    private String nickname;

    @Column(name = "email") // 사용자가 입력하는 이메일
    private String email;

    @Column(name = "social_id") // 소셜 로그인시 제공되는 아이디
    private String socialId;

    @Column(name = "provider") // 소셜 로그인 제공자 (예: GOOGLE, LOCAL)
    private String provider;

    @Column(name = "refresh_token") // 리프레시 토큰
    private String refreshToken;

    @Column(name = "created_at") // 계정 생성 일시
    private Date createdAt;

    // 엔티티가 처음 저장될 때 생성 시간 자동 설정
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = new Date();
        }
    }

    public void updateRefreshToken(String newRefreshToken) {
        this.refreshToken = newRefreshToken;
    }
}
