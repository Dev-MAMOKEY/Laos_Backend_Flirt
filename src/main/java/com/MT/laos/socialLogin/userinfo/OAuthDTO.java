package com.MT.laos.socialLogin.userinfo;

import com.MT.laos.DAO.Provider;
import com.MT.laos.DAO.User;
import lombok.Builder;
import lombok.Getter;

import java.util.Date;
import java.util.Map;

@Getter
public class OAuthDTO {

    private String nameAttributeKey; // 소셜 로그인별 사용자를 식별하는 고유키 ex) 구글 : sub (PK 같은 역할)
    private OAuth2UserInfo oAuth2UserInfo; // 소셜 로그인 진행시 사용자 정보 ex) 닉네임, 프로필 사진 등

    @Builder
    public OAuthDTO(String nameAttributeKey, OAuth2UserInfo oAuth2UserInfo) {
        this.nameAttributeKey = nameAttributeKey;
        this.oAuth2UserInfo = oAuth2UserInfo;
    }

    // 구글 등 각 소셜 로그인별로 맞는 메서드를 호출해서 객체를 반환하는 로직
    /**
     * of : DTO 객체를 생성할 때 소셜 타입 별로 초기화
     * Map 사용 이유 : 소셜 제공자가 사용자 정보를 JSON 형태로 제공 자바에서는 Map<String, Object> 형태로 받음
     * attributes : 소셜 제공자가 제공해주는 사용자 정보 전체
     */

    public static OAuthDTO of(Provider provider, String nameAttributeKey, Map<String, Object> attributes) {
        // 현재는 Google만 지원
        if (provider == Provider.GOOGLE) {
            return ofGoogle(nameAttributeKey, attributes);
        }
        throw new IllegalArgumentException("지원하지 않는 소셜 로그인입니다.");
    }

    private static OAuthDTO ofGoogle(String nameAttributeKey, Map<String, Object> attributes) {
        return OAuthDTO.builder()
                .nameAttributeKey(nameAttributeKey)
                .oAuth2UserInfo(new GoogleOAuth2UserInfo(attributes))
                .build();
    }

    public User toEntity(Provider provider, OAuth2UserInfo oAuth2UserInfo) {
        return User.builder()
                .provider(String.valueOf(provider))
                .socialId(oAuth2UserInfo.getId())
                .nickname(oAuth2UserInfo.getNickname())
                .email(oAuth2UserInfo.getEmail())
                .createdAt(new Date())
                .build();
    }
}
