package com.flirt.laos.socialLogin.handler;

import com.flirt.laos.DAO.Provider;
import com.flirt.laos.DAO.User;
import com.flirt.laos.repository.UserRepository;
import com.flirt.laos.service.JwtService;
import com.flirt.laos.socialLogin.userinfo.CustomOAuth2User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        log.info("oauth2 로그인 성공!");

        try {
            CustomOAuth2User customOAuth2User = (CustomOAuth2User) authentication.getPrincipal();

            // userRepository에서 사용자 이메일을 통해서 DB랑 일치하는지 찾아서 확인
            User findUser = userRepository
                    .findByEmailAndProvider(customOAuth2User.getEmail(), customOAuth2User.getProvider())
                    .orElse(null);

            // DB에서 조회해서 사용자 정보가 없을 경우 (처음 로그인한 사용자)
            if (findUser == null) {
                // 최초 로그인 사용자도 토큰 생성/저장을 동일하게 수행
                loginSuccess(response, customOAuth2User);
                response.sendRedirect("/");
                return; // 정보 입력받고 저장하는 로직은 별도의 컨트롤러, 서비스에서 작업
            }

            // 기존 사용자도 로그인 성공 처리
            loginSuccess(response, customOAuth2User);
            response.sendRedirect("/");

        } catch (Exception e) {
            log.error("OAuth2 로그인 성공 처리 중 오류가 발생했습니다.", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "소셜 로그인 처리 중 오류가 발생했습니다.");
        }
    }

    // 로그인 성공시 jwt 토큰 생성하고 응답에 추가하는 메서드
    private void loginSuccess(HttpServletResponse response, CustomOAuth2User customOAuth2User) throws IOException {

        // 토큰 생성 로직
        String accessToken = jwtService.createAccessToken(customOAuth2User.getEmail(), customOAuth2User.getProvider());
        String refreshToken = jwtService.createRefreshToken();

        // 응답 헤더에 토큰 추가 (표준 접두사 "Bearer " + 공백 포함)
        response.addHeader(jwtService.getAccessHeader(), "Bearer " + accessToken);
        response.addHeader(jwtService.getRefreshHeader(), "Bearer " + refreshToken);

        jwtService.sendRefreshToken(response, accessToken, refreshToken);
        jwtService.updateRefreshToken(
                customOAuth2User.getEmail(),
                Provider.valueOf(customOAuth2User.getProvider()),
                refreshToken
        );

        // 로그인 성공 토큰 로깅 (개발용, 운영에서는 마스킹/레벨 조정 권장)
        log.info("[OAUTH2-LOGIN] email={}, socialType={}, accessToken={}, refreshToken={}",
                customOAuth2User.getEmail(), customOAuth2User.getProvider(), accessToken, refreshToken);
    }
}
