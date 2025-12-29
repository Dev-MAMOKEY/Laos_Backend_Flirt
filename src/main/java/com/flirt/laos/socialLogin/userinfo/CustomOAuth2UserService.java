package com.flirt.laos.socialLogin.userinfo;

import com.flirt.laos.DAO.Provider;
import com.flirt.laos.DAO.User;
import com.flirt.laos.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("[OAuth] 소셜 로그인 요청 시작");

        // 1. Spring 기본 서비스 사용해서 사용자 정보 가져오기
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        // 2. Google, Facebook 등 소셜 타입 판별
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Provider provider = toProvider(registrationId);

        // 3. OAuth provider 식별자 key (Google - sub, Facebook - id)
        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        // 4. provider가 내려준 사용자 정보 원본(JSON 형태)
        Map<String, Object> attributes = oAuth2User.getAttributes();

        // 5. DTO 변환
        OAuthDTO extractDTO = OAuthDTO.of(provider, nameAttributeKey, attributes);

        // 6. DB에 저장 OR 조회
        User user = getOrSaveUser(extractDTO, provider);

        // 7. CustomOAuth2User 반환
        return new CustomOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                nameAttributeKey,
                user.getEmail(),
                user.getNickname(),
                user.getProvider()
        );
    }

    private Provider toProvider(String registrationId) {
        return Provider.GOOGLE;
    }

    private User getOrSaveUser(OAuthDTO oAuthDTO, Provider provider) {
        String email = oAuthDTO.getOAuth2UserInfo().getEmail();
        return userRepository.findByEmailAndProvider(email, provider.name())
                .orElseGet(() -> saveUser(oAuthDTO, provider));
    }

    private User saveUser(OAuthDTO oAuthDTO, Provider provider) {
        User createUser = oAuthDTO.toEntity(provider, oAuthDTO.getOAuth2UserInfo());
        log.info("신규 소셜 유저 저장: {}", createUser);
        return userRepository.save(createUser);
    }
}
