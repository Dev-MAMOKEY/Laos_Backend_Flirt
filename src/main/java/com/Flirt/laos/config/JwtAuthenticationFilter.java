package com.Flirt.laos.config;

import com.Flirt.laos.DAO.User;
import com.Flirt.laos.repository.UserRepository;
import com.Flirt.laos.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Getter
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * Jwt 인증 필터
     * "/login" 이외의 URI 요청이 왔을 때 처리하는 필터
     * 기본적으로 사용자는 요청 헤더에 AccessToken만 담아서 요청
     * AccessToken 만료 시에만 RefreshToken을 요청 헤더에 AccessToken과 함께 요청
     *
     * 2. RefreshToken이 없고, AccessToken이 없거나 유효하지 않은 경우 -> 인증 실패 처리, 403 ERROR
     * 3. RefreshToken이 있는 경우 -> DB의 RefreshToken과 비교하여 일치하면 AccessToken 재발급, RefreshToken 재발급(RTR 방식)
     * 인증 성공 처리는 하지 않고 실패 처리
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String requestMethod = request.getMethod();
        log.debug("[JWT] 진입 - {} {} (remote={})", requestMethod, requestURI, request.getRemoteAddr());

        // 로그인/회원가입/소셜 인증 관련 요청은 JWT 검사 없이 통과
        if (requestURI.startsWith("/login")
                || requestURI.startsWith("/register")
                || requestURI.startsWith("/oauth2")
                || requestURI.startsWith("/login/oauth2")
                || requestURI.startsWith("/oauth/callback")) {
            log.debug("[JWT] 인증 불필요 경로, 필터 통과: {}", requestURI);
            chain.doFilter(request, response);
            return;
        }

        // 사용자 요청 헤더에 AccessToken만 담아서 요청 만료시에는 RefreshToken도 같이 요청함
        String refreshToken = jwtService.extractRefreshToken(request)
                .filter(jwtService::isTokenValid)
                .orElse(null);

        // 요청 헤더에 RefreshToken이 들어왔고 액세스 토큰이 만료된 상태이면 재발급 로직 수행
        if (refreshToken != null) {
            checkRefreshToken(response, refreshToken);
            return;
        }

        // AccessToken이 유효한 경우 인증 성공 처리
        if (refreshToken == null) {
            checkAccessToken(request, response, chain);
        }
    }

    /**
     * 리프레시 토큰 체크 메서드
     */
    public void checkRefreshToken(HttpServletResponse response, String refreshToken) throws IOException {
        log.info("[JWT Filter] RefreshToken 검증 시작");

        Optional<User> optionalUser = userRepository.findByRefreshToken(refreshToken);

        if (optionalUser.isEmpty()) {
            log.warn("[JWT Filter] DB에 일치하는 RefreshToken 이 없습니다.");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"유효하지 않은 RefreshToken 입니다. 다시 로그인 해주세요.\"}");
            return;
        }

        User user = optionalUser.get();
        String newRefreshToken = newRefreshToken(user);
        String newAccessToken = jwtService.createAccessToken(user.getUserNum()); // userNum 기반 통합

        jwtService.sendAccessAndRefreshToken(response, newAccessToken, newRefreshToken);
        log.info("[JWT Filter] AccessToken / RefreshToken 재발급 완료");
    }

    public String newRefreshToken(User user) { // 리프레시 토큰 새로 발급하는 로직
        String newRefreshToken = jwtService.createRefreshToken();
        user.updateRefreshToken(newRefreshToken);
        userRepository.save(user);
        return newRefreshToken;
    }

    /**
     * 액세스 토큰 체크 메서드
     */
    public void checkAccessToken(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        log.info("[JWT Filter] AccessToken 검증 시작");

        Optional<String> accessTokenOptional = jwtService.extractAccessToken(request);

        if (accessTokenOptional.isPresent() && jwtService.isTokenValid(accessTokenOptional.get())) {
            jwtService.extractUserNum(accessTokenOptional.get())
                    .flatMap(userRepository::findByUserNum)
                    .ifPresent(this::saveAuthentication);
        }

        chain.doFilter(request, response);
    }

    /**
     * 인증된 사용자 정보를 SecurityContext에 저장
     */
    public void saveAuthentication(User myUser) {
        String role = myUser.isRole() ? "ADMIN" : "USER";

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(myUser.getEmail() != null ? myUser.getEmail() : myUser.getLocalId())
                .password("")
                .roles(role)
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}