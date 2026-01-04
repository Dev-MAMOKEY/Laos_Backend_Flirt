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
     * <p>
     * 기본적으로 사용자는 요청 헤더에 AccessToken만 담아서 요청
     * AccessToken 만료 시에만 RefreshToken을 요청 헤더에 AccessToken과 함께 요청
     * <p>m
     * 2. RefreshToken이 없고, AccessToken이 없거나 유효하지 않은 경우 -> 인증 실패 처리, 403 ERROR
     * 3. RefreshToken이 있는 경우 -> DB의 RefreshToken과 비교하여 일치하면 AccessToken 재발급, RefreshToken 재발급(RTR 방식)
     * 인증 성공 처리는 하지 않고 실패 처리
     */

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        String requestMethod = request.getMethod();
        log.info("===========================================");
        log.info("[JWT Filter] 필터 진입 - Method: {}, URI: {}", requestMethod, requestURI);
        log.info("[JWT Filter] Remote Address: {}", request.getRemoteAddr());
        log.info("===========================================");

        // 로그인/회원가입/소셜 인증 관련 요청은 JWT 검사 없이 통과
        if (requestURI.startsWith("/login")
                || requestURI.startsWith("/register")
                || requestURI.startsWith("/oauth2")
                || requestURI.startsWith("/login/oauth2")
                || requestURI.startsWith("/oauth/callback")) {
            log.debug("[JWT Filter] 인증 불필요 경로 통과: {}", requestURI);
            chain.doFilter(request, response); // 요청 응답을 chain.doFilter() 통해서 넘겨야 다음 작업이 진행됨 / 호출하지 않으면 다음 필터로 이동 x
            return; // 리턴 통해 다음 코드를 실행시키지 않고 종료
        }

        // 사용자 요청 헤더에 AccessToken만 담아서 요청 만료시에는 RefreshToken도 같이 요청함
        // 따라서 그 외 RefreshToken이 요청되는 경우는 null 반환
        String refreshToken = jwtService.extractRefreshToken(request) // RefreshToken은 JwtService 클래스 파일에서 extractRefreshToken 메서드 참조
                .filter(jwtService::isTokenValid) // 필터를 걸쳐서 jwtService 인스턴스가 isTokenVaild() 메서드를 참조해서 필터 걸치고 다음 필터 넘김
                .orElse(null); // 일치하지 않으면 null 값으로 반환

        // 요청 헤더에 RefreshToken이 null이 아니라는 의미는 즉 요청 헤더에 리프레시 토큰이 들어왔고 액세스 토큰이 만료된 상태이며,
        // 요청 온 리프레시 토큰과 DB에 리프레시 토큰을 비교해서 일치하면 액세스 토큰을 다시 재발급 해주는 로직
        // 리프레시 토큰이 요청 헤더에 들어오면 액세스 토큰을 다시 발급 해주므로 중복 인증 방지 위해 다음 필터로 넘어가는 것을 막기 위해 return을 붙임
        if (refreshToken != null) {
            checkRefreshToken(response, refreshToken);
            return;
        }

        // 요청 헤더에 RefreshToken이 null 값이 들어온 경우는 AccessToken이 만료되지 않은 경우에는 다음 필터 걸쳐서 인증 성공 그 외는 에러 처리
        // 액세스 토큰을 비교해서 일치하면 인증성공하고 일치하지 않거나 없을 경우 에러 반환이므로 자연스럽게 다음 필터로 이동할 수 있게 return 생략
        if (refreshToken == null) {
            checkAccessToken(request, response, chain);
        }
    }

    /**
     * 리프레시 토큰 체크 메서드
     * - 만료된 AccessToken + 유효한 RefreshToken 이 함께 올 때 사용
     * - DB에 저장된 RefreshToken 과 요청값이 일치하면 AT/RT 둘 다 재발급
     * - 일치하지 않으면 403 으로 인증 실패 처리
     */
    public void checkRefreshToken(HttpServletResponse response, String refreshToken) throws IOException {
        log.info("[JWT Filter] RefreshToken 검증 시작");
        
        Optional<User> optionalUser = userRepository.findByRefreshToken(refreshToken);
        
        if (optionalUser.isEmpty()) {
            log.warn("[JWT Filter] DB에 일치하는 RefreshToken 이 없습니다. 로그아웃되었거나 탈퇴한 사용자일 수 있습니다.");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"유효하지 않은 RefreshToken 입니다. 다시 로그인 해주세요.\"}");
            return;
        }

        User user = optionalUser.get();
        log.info("[JWT Filter] RefreshToken 일치 사용자 확인: userNum = {}, provider = {}",
                user.getUserNum(), user.getProvider());

        // 일치하면 리프레시 토큰 값도 새로 발급 받음
        String newRefreshToken = newRefreshToken(user);

        // 액세스 토큰 새로 발급: 로컬(LOCAL)은 userNum 기반, 소셜은 email + provider 기반
        String provider = user.getProvider();
        String newAccessToken;
        if (provider == null || "LOCAL".equalsIgnoreCase(provider)) {
            newAccessToken = jwtService.createAccessTokenByUserNum(user.getUserNum());
            log.info("[JWT Filter] LOCAL 사용자에 대한 AccessToken 재발급 완료: userNum = {}", user.getUserNum());
        } else {
            newAccessToken = jwtService.createAccessToken(user.getEmail(), provider);
            log.info("[JWT Filter] 소셜 사용자에 대한 AccessToken 재발급 완료: email = {}, provider = {}", user.getEmail(), provider);
        }

        // AT, RT를 헤더에 담아서 응답 (메서드 시그니처: (response, accessToken, refreshToken))
        jwtService.sendRefreshToken(response, newAccessToken, newRefreshToken);
        log.info("[JWT Filter] AccessToken / RefreshToken 재발급 완료, 응답 전송");
    }

    public String newRefreshToken(User user) { // 리프레시 토큰 새로 발급하는 로직
        String newRefreshToken = jwtService.createRefreshToken(); // jwtService 인스턴스에서 createRefreshToken() 메서드 활용
        user.updateRefreshToken(newRefreshToken); // 사용자 리프레시 토큰을 새로운 리프레시 토큰으로 업데이트
        userRepository.save(user); // 업데이트한 리프레시 토큰 저장
        return newRefreshToken; // 저장된 새로운 리프레시 토큰 반환
    }

    public void checkAccessToken(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String requestURI = request.getRequestURI();
        String requestMethod = request.getMethod();
        log.info("[JWT Filter] AccessToken 검증 시작: {} {}", requestMethod, requestURI);
        
        Optional<String> accessTokenOptional = jwtService.extractAccessToken(request);
        
        if (accessTokenOptional.isEmpty()) {
            log.warn("[JWT Filter] AccessToken이 없습니다. {} {}", requestMethod, requestURI);
            // 토큰이 없어도 chain.doFilter를 호출하여 Spring Security의 exceptionHandling이 처리하도록 함
            log.info("[JWT Filter] chain.doFilter 호출 전: {} {}", requestMethod, requestURI);
            chain.doFilter(request, response);
            log.info("[JWT Filter] chain.doFilter 호출 후: {} {}", requestMethod, requestURI);
            return;
        }
        
        String accessToken = accessTokenOptional.get();
        
        if (!jwtService.isTokenValid(accessToken)) {
            log.warn("[JWT Filter] 유효하지 않은 AccessToken. {} {}", requestMethod, requestURI);
            // 토큰이 유효하지 않아도 chain.doFilter를 호출하여 Spring Security의 exceptionHandling이 처리하도록 함
            log.info("[JWT Filter] chain.doFilter 호출 전: {} {}", requestMethod, requestURI);
            chain.doFilter(request, response);
            log.info("[JWT Filter] chain.doFilter 호출 후: {} {}", requestMethod, requestURI);
            return;
        }
        
        log.info("[JWT Filter] 유효한 AT 감지, 사용자 인증 진행 중. {} {}", requestMethod, requestURI);

        // 1) userNum 클레임 우선 시도 (로컬 로그인 토큰)
        boolean authenticated = jwtService.extractUserNum(accessToken)
                .flatMap(userRepository::findByUserNum)
                .map(user -> { 
                    log.info("[JWT Filter] userNum으로 사용자 인증 성공: {}", user.getUserNum());
                    saveAuthentication(user); 
                    return true; 
                })
                .orElse(false);

        // 2) email 클레임으로 시도 (소셜 로그인 토큰)
        if (!authenticated) {
            authenticated = jwtService.extractEmail(accessToken)
                    .flatMap(email -> jwtService.extractProvider(accessToken)
                            .map(Enum::name)
                            .flatMap(provider -> userRepository.findByEmailAndProvider(email, provider)))
                    .map(user -> {
                        log.info("[JWT Filter] email + provider로 사용자 인증 성공: {}", user.getUserNum());
                        saveAuthentication(user);
                        return true;
                    })
                    .orElse(false);
        }
        
        if (!authenticated && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.warn("[JWT Filter] 토큰은 유효하지만 사용자를 찾을 수 없습니다. {} {}", request.getMethod(), requestURI);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("[JWT Filter] 인증 완료, SecurityContext 인증 상태: {} (URI: {})", 
                auth != null ? "인증됨" : "인증 안됨", requestURI);
        
        log.info("[JWT Filter] chain.doFilter 호출 전: {} {}", request.getMethod(), requestURI);
        chain.doFilter(request, response); // 다음 필터로 요청 전달
        log.info("[JWT Filter] chain.doFilter 호출 후: {} {}", request.getMethod(), requestURI);
    }

    public void saveAuthentication(User myUser) { // 소셜/로컬 공통: 인증된 사용자 정보를 SecurityContext에 저장
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(myUser.getEmail() != null ? myUser.getEmail() : myUser.getLocalId()) // 이메일 없으면 localId 사용
                .password("") // JWT 기반이라 비밀번호는 여기서 사용하지 않음
                .roles("USER") // 간단히 USER 권한 부여
                .build();

        // Authentication: 사용자 인증 상태와 정보를 나타내는 인터페이스
        /**
         * UsernamePasswordAuthenticationToken
         * 첫 번째 파라미터에는 인증된 사용자 정보(보통 UserDetails) 들어감
         * 두 번째 자격 증명 : 소셜 로그인으로 구현해서 비밀번호가 없으므로 null 값 사용함
         * 세 번째 해당 사용자 권한 목록
         * 결론 : 현재 로그인한 사용자 정보를 담아서 인증 토큰 객체를 생성하는 과정
         */

        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        /** SecurityContextHolder : 스프링 시큐리티에 인증 정보 저장하고 조회하는 저장소 역할을함
         *  1. getContext() : 현재 실행중인 스레드의 SecurityContext 가져옴
         *  2. setAuthentication(객체) : 만든 객체를 저장하면, 해당 요청 동안 스프링 시큐리티가 사용자를 인증된 사용자로 인식하는 로직
         */

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
