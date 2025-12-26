package com.MT.laos.socialLogin.userinfo;

import java.util.Map;

public abstract class OAuth2UserInfo {
    // 추상 클래스는 상속받는 클래스만 사용 가능하도록 protected로 선언
    protected Map<String, Object> attributes;

    public OAuth2UserInfo(Map<String, Object> attributes) {

        this.attributes = attributes;
    }

    public abstract String getId();

    public abstract String getNickname();

    public abstract String getEmail();

}
