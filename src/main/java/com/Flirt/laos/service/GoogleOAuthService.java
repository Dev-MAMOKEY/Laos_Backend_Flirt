package com.Flirt.laos.service;

import com.Flirt.laos.DAO.Provider;
import com.Flirt.laos.DAO.User;
import com.Flirt.laos.DTO.AuthTokensDTO;
import com.Flirt.laos.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Date;

/**
 * Google OAuth2 인가 코드로부터
 *  - Google access_token / userinfo 조회
 *  - 우리 서비스 User 생성/조회
 *  - 우리 서비스 JWT(AT/RT) 발급
 * 까지를 한 번에 처리하는 서비스.
 *
 * 프론트엔드에서는 인가 코드만 전달하면 되고,
 * 이 서비스에서 나머지 로직을 모두 담당한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {

    private final RestTemplate restTemplate;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    // Google 로그인 시 사용한 redirect_uri (application.yml 에 설정)
    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    // Google 표준 엔드포인트 (설정으로 뺄 수도 있지만, 기본값으로 사용)
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    /**
     * 인가 코드 기반 Google 로그인 전체 플로우
     */
    public AuthTokensDTO loginWithCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("인가 코드(code)가 필요합니다.");
        }

        // 1. 인가 코드로 Google 토큰 요청 (redirect_uri 포함)
        GoogleTokenResponse googleToken = requestGoogleToken(code, redirectUri);

        if (googleToken.getAccessToken() == null || googleToken.getAccessToken().isBlank()) {
            throw new IllegalStateException("Google 토큰 응답에 access_token 이 없습니다.");
        }

        // 2. Google 사용자 정보 조회
        GoogleUserResponse googleUser = requestGoogleUserInfo(googleToken.getAccessToken());

        if (googleUser.getEmail() == null || googleUser.getEmail().isBlank()) {
            throw new IllegalStateException("Google 사용자 정보에 이메일이 없습니다.");
        }

        // 3. User 엔티티 조회/생성
        User user = getOrCreateUser(googleUser);

        // 4. 우리 서버 JWT AT/RT 발급 (프론트에서 사용할 자체 토큰)
        String accessToken = jwtService.createAccessToken(user.getEmail(), Provider.GOOGLE.name());
        String refreshToken = jwtService.createRefreshToken();
        jwtService.updateRefreshToken(user.getEmail(), Provider.GOOGLE, refreshToken);

        // 토큰 문자열은 로그에 남기지 않고, 어떤 사용자인지만 기록
        log.info("[Google OAuth] 소셜 로그인 처리 완료: email={}, provider={}", user.getEmail(), user.getProvider());

        return new AuthTokensDTO(accessToken, refreshToken);
    }

    /**
     * 1) 인가 코드로 Google 토큰 엔드포인트 호출
     */
    private GoogleTokenResponse requestGoogleToken(String code, String redirectUri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<GoogleTokenResponse> response =
                    restTemplate.postForEntity(GOOGLE_TOKEN_URL, request, GoogleTokenResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[Google OAuth] 토큰 발급 실패. status={}", response.getStatusCode());
                throw new IllegalStateException("Google 토큰 발급에 실패했습니다.");
            }

            return response.getBody();
        } catch (RestClientException e) {
            // 구글이 invalid_request, redirect_uri_mismatch 등을 응답하거나
            // 네트워크 오류가 발생했을 때 상세 로그 남김
            log.error("[Google OAuth] 토큰 발급 HTTP 통신 오류: {}", e.getMessage(), e);
            throw new IllegalStateException("Google 토큰 발급 중 통신 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 2) Google access_token 으로 사용자 정보 조회
     */
    private GoogleUserResponse requestGoogleUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<GoogleUserResponse> response = restTemplate.exchange(
                    GOOGLE_USERINFO_URL,
                    HttpMethod.GET,
                    request,
                    GoogleUserResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[Google OAuth] 사용자 정보 조회 실패. status={}", response.getStatusCode());
                throw new IllegalStateException("Google 사용자 정보 조회에 실패했습니다.");
            }

            return response.getBody();
        } catch (RestClientException e) {
            log.error("[Google OAuth] 사용자 정보 조회 통신 오류: {}", e.getMessage(), e);
            throw new IllegalStateException("Google 사용자 정보 조회 중 통신 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 3) 이메일 기준으로 사용자 조회, 없으면 신규 생성
     */
    private User getOrCreateUser(GoogleUserResponse googleUser) {
        String email = googleUser.getEmail();

        return userRepository.findByEmailAndProvider(email, Provider.GOOGLE.name())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .provider(Provider.GOOGLE.name())
                            .socialId(googleUser.getSub())
                            .nickname(googleUser.getName())
                            .email(email)
                            .createdAt(new Date())
                            .build();
                    return userRepository.save(newUser);
                });
    }

    /**
     * Google 토큰 응답 DTO
     */
    @Getter
    @Setter
    public static class GoogleTokenResponse {
        private String access_token;
        private String refresh_token;
        private String scope;
        private String token_type;
        private Integer expires_in;

        public String getAccessToken() {
            return access_token;
        }

        public String getRefreshToken() {
            return refresh_token;
        }
    }

    /**
     * Google 사용자 정보 DTO
     */
    @Getter
    @Setter
    public static class GoogleUserResponse {
        private String sub;
        private String name;
        private String given_name;
        private String family_name;
        private String picture;
        private String email;
        private boolean email_verified;
        private String locale;
    }
}
