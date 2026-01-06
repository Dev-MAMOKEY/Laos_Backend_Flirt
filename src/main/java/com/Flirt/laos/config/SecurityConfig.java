package com.Flirt.laos.config;

import com.Flirt.laos.repository.UserRepository;
import com.Flirt.laos.service.JwtService;
import com.Flirt.laos.socialLogin.handler.OAuth2LoginFailureHandler;
import com.Flirt.laos.socialLogin.handler.OAuth2LoginSuccessHandler;
import com.Flirt.laos.socialLogin.userinfo.CustomOAuth2UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@Configuration
@Slf4j
public class SecurityConfig {

    /**
     * 🔐 Spring Security 핵심 설정
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler
    ) throws Exception {

        http
                // CSRF 비활성화 (JWT + REST API)
                .csrf(csrf -> csrf.disable())

                // CORS 설정 (아래 Bean 직접 사용)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 세션 사용 안 함 (JWT 기반)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 요청별 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight 허용
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 인증 없이 접근 허용
                        .requestMatchers(
                                "/", "/index",
                                "/login", "/register",
                                "/auth/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/oauth/callback/**",
                                "/mbti/**",
                                "/question"
                        ).permitAll()

                        // 인증 필요
                        .requestMatchers("/logout", "/delete/user").authenticated()

                        // 그 외는 전부 인증 필요
                        .anyRequest().authenticated()
                )

                // 인증 / 인가 실패 시 JSON 응답
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write(
                                    "{\"error\":\"인증이 필요합니다. AccessToken을 헤더에 포함해주세요.\"}"
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write(
                                    "{\"error\":\"접근 권한이 없습니다.\"}"
                            );
                        })
                )

                // 기본 로그아웃 비활성화 (JWT 방식)
                .logout(logout -> logout.disable())

                // OAuth2 소셜 로그인 (브라우저 리다이렉트 방식)
                // ※ 수동 코드 교환 엔드포인트(/oauth/callback/google)와 겹치지 않도록
                //   Spring Security 기본 리다이렉트 URI 패턴(/login/oauth2/code/*)을 사용한다.
                .oauth2Login(oauth -> oauth
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(customOAuth2UserService)
                        )
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler(oAuth2LoginFailureHandler)
                )

                // JWT 필터 등록
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * 🔑 JWT 인증 필터 Bean
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository
    ) {
        return new JwtAuthenticationFilter(jwtService, userRepository);
    }

    /**
     * 🔐 비밀번호 암호화
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 🌐 CORS 설정
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 허용할 프론트엔드 주소
        config.setAllowedOrigins(List.of(
                "http://localhost:8080",
                "http://localhost:5173",
                "http://localhost:3000"
        ));

        // 허용 HTTP 메서드
        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));

        // 허용 헤더
        config.setAllowedHeaders(List.of("*"));

        // 쿠키 / 인증 정보 허용
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
