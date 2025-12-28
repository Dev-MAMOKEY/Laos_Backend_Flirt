package com.flirt.laos.socialLogin.userinfo;

import java.util.Map;

public class FacebookOAuth2UserInfo extends OAuth2UserInfo {

    public FacebookOAuth2UserInfo(Map<String, Object> attributes) {
        super(attributes);
    }

    @Override
    public String getId() {
        return (String) attributes.get("id");   // Facebook PK
    }

    @Override
    public String getNickname() {
        return (String) attributes.get("name"); // Facebook은 nickname 필드가 없어서 name 사용
    }

    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }
}
