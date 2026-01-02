package com.MT.laos.config;

import com.MT.laos.repository.UserRepository;
import com.MT.laos.service.JwtService;
import com.MT.laos.socialLogin.handler.OAuth2LoginFailureHandler;
import com.MT.laos.socialLogin.handler.OAuth2LoginSuccessHandler;
import com.MT.laos.socialLogin.userinfo.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Configuration
@Slf4j
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oAuth2LoginFailureHandler,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                // CSRF는 REST API 테스트/개발 단계에서는 보통 끄고 시작
                .csrf(csrf -> csrf.disable())
                // CORS 설정 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // 세션을 사용하지 않도록 설정 (JWT 기반 인증)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // OPTIONS 요청(CORS preflight)은 모두 허용
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        // 회원가입, 로그인, 소셜 로그인 콜백 등은 모두 인증 없이 허용
                        .requestMatchers("/", "/index", "/login", "/register",
                                "/auth/**", "/oauth2/**", "/login/oauth2/**", "/oauth/callback/**",
                                // MBTI 테스트 API는 토큰 검증을 컨트롤러 내부에서 직접 처리하므로 일단 permitAll
                                "/mbti/**",
                                // OpenAI API는 토큰 검증을 컨트롤러 내부에서 직접 처리하므로 permitAll
                                "/question")
                        .permitAll()
                        // 로그아웃, 회원탈퇴는 JWT 인증 필요
                        .requestMatchers("/logout", "/delete/user")
                        .authenticated()
                        // 나머지는 인증 필요
                        .anyRequest()
                        .authenticated()
                )
                // 인증 실패 시 JSON 응답 반환 (HTML 로그인 페이지 대신)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write("{\"error\":\"인증이 필요합니다. AccessToken을 헤더에 포함해주세요.\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"error\":\"접근 권한이 없습니다.\"}");
                        })
                )
                // Spring Security의 기본 로그아웃 비활성화 (커스텀 로그아웃 컨트롤러 사용)
                .logout(logout -> logout.disable())
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() { // CorsConfigurationSource는 CORS 규칙 담고 있는 Bean 객체
        CorsConfiguration config = new CorsConfiguration();

        // CORS 정책 허용할 ORIGIN들 지정 -> 지정된 주소에서 오는 요청만 허용
        config.setAllowedOrigins(List.of("http://localhost:8080", "http://localhost:5173", "http://localhost:3000"));

        // CORS 정책 허용할 메서드들 지정 -> GET, POST, PUT, DELETE 요청만 허용
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 클라이언트가 서버로 요청할 때 사용할 수 있는 HTTP 헤더들을 의미하며 '*' : 모든 헤더 사용을 허용
        config.setAllowedHeaders(List.of("*"));

        // 주의 : setAllowCredentials(true) 설정할 경우 allowedOrigins는 "*" 와일드카드를 사용할 수 없음. 반드시 명시된 도메인 사용
        // 클라이언트가 쿠키, 인증 토큰 등 자격 증명 정보를 포함해서 요청하도록 허용하는 설정
        config.setAllowCredentials(true);

        // 스프링에서 CORS 설정을 URL 경로 패턴별로 관리하기 위한 인스턴스 생성
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 모든 경로에 CORS 설정 적용

        return source;
    }
}
