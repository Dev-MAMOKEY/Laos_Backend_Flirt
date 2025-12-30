package com.MT.laos.config;

import com.MT.laos.repository.UserRepository;
import com.MT.laos.service.JwtService;
import com.MT.laos.socialLogin.handler.OAuth2LoginFailureHandler;
import com.MT.laos.socialLogin.handler.OAuth2LoginSuccessHandler;
import com.MT.laos.socialLogin.userinfo.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler
    ) throws Exception {
        http
                // CSRF는 REST API 테스트/개발 단계에서는 보통 끄고 시작
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 회원가입, 로그인, 소셜 로그인 콜백 등은 모두 인증 없이 허용
                        .requestMatchers("/", "/index", "/login", "/register",
                                "/auth/**", "/oauth2/**", "/login/oauth2/**", "/oauth/callback/**",
                                // MBTI 테스트 API는 토큰 검증을 컨트롤러 내부에서 직접 처리하므로 일단 permitAll
                                "/mbti/**"
                        ).permitAll()
                        // 나머지는 인증 필요
                        .anyRequest().authenticated()
                )
                // OAuth2 소셜 로그인 설정
                .oauth2Login(oauth -> oauth
                        // 별도 로그인 페이지 없이, /oauth2/authorization/{provider}로 직접 진입
                        .redirectionEndpoint(redir -> redir.baseUri("/oauth/callback/*"))
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler)
                )
                // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        // JWT 필터를 빈으로 등록해 필터 체인에서 사용할 수 있게 함
        return new JwtAuthenticationFilter(jwtService, userRepository);
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // BCryptPasswordEncoder 생성자를 빈으로 등록하여 비밀번호 암호화에 사용
    }
}
