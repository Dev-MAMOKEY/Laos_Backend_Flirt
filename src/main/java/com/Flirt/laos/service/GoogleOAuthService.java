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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
     * @param code Google OAuth2 인가 코드 (URL 인코딩되어 있을 수 있음)
     * @param requestRedirectUri 프론트엔드에서 사용한 redirect_uri (null이면 설정값 사용)
     */
    public AuthTokensDTO loginWithCode(String code, String requestRedirectUri) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("인가 코드(code)가 필요합니다.");
        }

        // 인가 코드 URL 디코딩 (프론트엔드에서 URL 인코딩되어 올 수 있음)
        String decodedCode;
        try {
            decodedCode = URLDecoder.decode(code, StandardCharsets.UTF_8);
            if (!decodedCode.equals(code)) {
                log.info("[Google OAuth] 인가 코드 URL 디코딩 완료: {} -> {}", 
                        code.substring(0, Math.min(20, code.length())) + "...", 
                        decodedCode.substring(0, Math.min(20, decodedCode.length())) + "...");
            }
        } catch (Exception e) {
            log.warn("[Google OAuth] 인가 코드 디코딩 실패, 원본 사용: {}", e.getMessage());
            decodedCode = code; // 디코딩 실패 시 원본 사용
        }

        // 프론트엔드에서 redirect_uri를 전달한 경우 사용, 없으면 설정값 사용
        String finalRedirectUri = (requestRedirectUri != null && !requestRedirectUri.isBlank()) 
                ? requestRedirectUri 
                : redirectUri;
        
        log.info("[Google OAuth] 토큰 교환 시작 - redirect_uri: {}", finalRedirectUri);

        // 1. 인가 코드로 Google 토큰 요청 (redirect_uri 포함)
        GoogleTokenResponse googleToken = requestGoogleToken(decodedCode, finalRedirectUri);

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

        // 4. 우리 서버 JWT AT/RT 발급
        String accessToken = jwtService.createAccessToken(user.getEmail(), Provider.GOOGLE.name());
        String refreshToken = jwtService.createRefreshToken();
        jwtService.updateRefreshToken(user.getEmail(), Provider.GOOGLE, refreshToken);

        log.info("[Google OAuth] 소셜 로그인 처리 완료: email={}, provider={}, AccessToken={}, RefreshToken={}", user.getEmail(), user.getProvider(), accessToken, refreshToken);

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

        // 디버깅: 요청 파라미터 로깅 (민감 정보는 마스킹)
        log.info("[Google OAuth] 토큰 요청 파라미터 - client_id: {}, redirect_uri: {}, code: {}...", 
                clientId != null ? clientId.substring(0, Math.min(20, clientId.length())) + "..." : "null",
                redirectUri,
                code != null && code.length() > 10 ? code.substring(0, 10) + "..." : code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<GoogleTokenResponse> response =
                    restTemplate.postForEntity(GOOGLE_TOKEN_URL, request, GoogleTokenResponse.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[Google OAuth] 토큰 발급 실패. status={}", response.getStatusCode());
                throw new IllegalStateException("Google 토큰 발급에 실패했습니다.");
            }

            log.info("[Google OAuth] 토큰 발급 성공");
            return response.getBody();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 구글이 invalid_request, redirect_uri_mismatch 등을 응답할 때 상세 로그 남김
            String errorBody = e.getResponseBodyAsString();
            log.error("[Google OAuth] 토큰 발급 HTTP 오류. status={}", e.getStatusCode());
            log.error("[Google OAuth] 에러 응답 본문: {}", errorBody != null ? errorBody : "(비어있음)");
            log.error("[Google OAuth] 요청 파라미터:");
            log.error("  - redirect_uri: {}", redirectUri);
            log.error("  - client_id: {}...", clientId != null ? clientId.substring(0, Math.min(20, clientId.length())) + "..." : "null");
            log.error("  - code: {}...", code != null && code.length() > 20 ? code.substring(0, 20) + "..." : code);
            
            // Google의 에러 메시지를 더 명확하게 전달
            String errorMessage = "Google 토큰 발급 요청이 거절되었습니다: " + e.getStatusCode();
            
            // 일반적인 오류 원인 안내
            if (e.getStatusCode().value() == 401) {
                errorMessage += "\n가능한 원인:\n";
                errorMessage += "1. 인가 코드가 이미 사용되었거나 만료되었습니다 (인가 코드는 한 번만 사용 가능)\n";
                errorMessage += "2. redirect_uri가 Google 로그인 시 사용한 값과 일치하지 않습니다\n";
                errorMessage += "3. client_id 또는 client_secret이 잘못되었습니다\n";
                if (errorBody != null && !errorBody.isEmpty()) {
                    errorMessage += "\nGoogle 에러 상세: " + errorBody;
                }
            } else if (errorBody != null && !errorBody.isEmpty()) {
                errorMessage += " - " + errorBody;
            }
            
            throw new IllegalStateException(errorMessage);
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("[Google OAuth] 네트워크 오류 또는 기타 예외 발생", e);
            throw new IllegalStateException("Google 토큰 발급 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 2) Google access_token 으로 사용자 정보 조회
     */
    private GoogleUserResponse requestGoogleUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

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


