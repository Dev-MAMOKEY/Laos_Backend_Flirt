package com.Flirt.laos.controller;
import com.Flirt.laos.DAO.User;
import com.Flirt.laos.DTO.AuthTokensDTO;
import com.Flirt.laos.DTO.LocalLoginDTO;
import com.Flirt.laos.DTO.RegisterDTO;
import com.Flirt.laos.repository.UserRepository;
import com.Flirt.laos.service.JwtService;
import com.Flirt.laos.service.LocalLoginService;
import com.Flirt.laos.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final LocalLoginService loginService;
    private final UserService userService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @GetMapping("/login") // 로컬 로그인 페이지
    public String loginpage() {
        return "login page";
    }

    @PostMapping("/login") // 로컬 로그인 처리 (JSON)
    public ResponseEntity<?> login(@RequestBody LocalLoginDTO localLoginDTO) {
        Optional<AuthTokensDTO> tokens = loginService.localLogin(localLoginDTO);
        return tokens.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @GetMapping("/register") // 회원가입 페이지
    public String registerpage() {
        return "register page";
    }

    @PostMapping("/register") // 회원가입 처리
    @ResponseBody
    public ResponseEntity<?> register(@RequestBody RegisterDTO registerDTO) {
        try {
            userService.registerUser(registerDTO);
            return ResponseEntity.created(URI.create("/login")).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    /**
     * 로그아웃 엔드포인트
     * - JWT 토큰을 헤더에서 추출하여 사용자 식별
     * - DB에서 RefreshToken을 삭제하여 로그아웃 처리
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        log.info("[LOGOUT] POST /logout 요청 도착");

        // 1. 헤더에서 AccessToken 추출
        Optional<String> accessTokenOptional = jwtService.extractAccessToken(request);
        if (accessTokenOptional.isEmpty()) {
            log.warn("[LOGOUT] AccessToken 없음");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("AccessToken이 필요합니다.");
        }

        String accessToken = accessTokenOptional.get();

        // 2. 토큰 유효성 검사
        if (!jwtService.isTokenValid(accessToken)) {
            log.warn("[LOGOUT] 토큰 유효성 검사 실패");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("유효하지 않은 AccessToken 입니다.");
        }

        // 3. AccessToken에서 사용자 찾기 (로컬 → 소셜 순서로 시도)
        User user = null;

        // 3-1) 로컬 로그인: userNum으로 찾기
        Optional<Integer> userNumClaim = jwtService.extractUserNum(accessToken);
        if (userNumClaim.isPresent()) {
            Integer userNum = userNumClaim.get();
            log.info("[LOGOUT] userNum으로 사용자 조회 시도: {}", userNum);
            user = userRepository.findByUserNum(userNum).orElse(null);
            if (user != null) {
                log.info("[LOGOUT] userNum으로 사용자 찾음: {}", user.getUserNum());
                // RefreshToken 삭제
                jwtService.deleteRefreshTokenByUserNum(userNum);
                return ResponseEntity.ok("로그아웃이 완료되었습니다.");
            }
        }

        // 3-2) 소셜 로그인: email + provider로 찾기
        if (user == null) {
            Optional<String> emailClaim = jwtService.extractEmail(accessToken);
            Optional<com.Flirt.laos.DAO.Provider> providerClaim = jwtService.extractProvider(accessToken);

            if (emailClaim.isPresent() && providerClaim.isPresent()) {
                String email = emailClaim.get();
                com.Flirt.laos.DAO.Provider provider = providerClaim.get();
                log.info("[LOGOUT] email + provider로 사용자 조회 시도: {}, {}", email, provider);
                user = userRepository.findByEmailAndProvider(email, provider.name()).orElse(null);
                if (user != null) {
                    log.info("[LOGOUT] email + provider로 사용자 찾음: {}", user.getUserNum());
                    // RefreshToken 삭제
                    jwtService.deleteRefreshToken(email, provider);
                    return ResponseEntity.ok("로그아웃이 완료되었습니다.");
                }
            }
        }

        // 토큰은 유효하지만 해당하는 사용자를 찾지 못한 경우
        log.warn("[LOGOUT] 토큰은 있지만 DB에서 사용자를 찾지 못함");
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body("사용자 정보를 찾을 수 없습니다.");
    }

    /**
     * 회원탈퇴 엔드포인트
     * - JWT 토큰을 헤더에서 추출하여 사용자 식별
     * - 해당 사용자를 DB에서 삭제
     */
    @DeleteMapping("/delete/user")
    public ResponseEntity<?> deleteUser(HttpServletRequest request) {
        log.info("===========================================");
        log.info("[DELETE] DELETE /delete/user 요청 도착");
        log.info("[DELETE] Request Method: {}", request.getMethod());
        log.info("[DELETE] Request URI: {}", request.getRequestURI());
        log.info("===========================================");

        // 1. 헤더에서 AccessToken 추출
        Optional<String> accessTokenOptional = jwtService.extractAccessToken(request);
        if (accessTokenOptional.isEmpty()) {
            log.warn("[DELETE] AccessToken 없음");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("AccessToken이 필요합니다.");
        }

        String accessToken = accessTokenOptional.get();

        // 2. 토큰 유효성 검사
        if (!jwtService.isTokenValid(accessToken)) {
            log.warn("[DELETE] 토큰 유효성 검사 실패");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("유효하지 않은 AccessToken 입니다.");
        }

        // 3. AccessToken에서 사용자 찾기 (로컬 → 소셜 순서로 시도)
        User user = null;
        Integer userNum = null;

        // 3-1) 로컬 로그인: userNum으로 찾기
        Optional<Integer> userNumClaim = jwtService.extractUserNum(accessToken);
        if (userNumClaim.isPresent()) {
            userNum = userNumClaim.get();
            log.info("[DELETE] userNum으로 사용자 조회 시도: {}", userNum);
            user = userRepository.findByUserNum(userNum).orElse(null);
            if (user != null) {
                log.info("[DELETE] userNum으로 사용자 찾음: {}", user.getUserNum());
            }
        }

        // 3-2) 소셜 로그인: email + provider로 찾기
        if (user == null) {
            Optional<String> emailClaim = jwtService.extractEmail(accessToken);
            Optional<com.Flirt.laos.DAO.Provider> providerClaim = jwtService.extractProvider(accessToken);

            if (emailClaim.isPresent() && providerClaim.isPresent()) {
                String email = emailClaim.get();
                String provider = providerClaim.get().name();
                log.info("[DELETE] email + provider로 사용자 조회 시도: {}, {}", email, provider);
                user = userRepository.findByEmailAndProvider(email, provider).orElse(null);
                if (user != null) {
                    userNum = user.getUserNum();
                    log.info("[DELETE] email + provider로 사용자 찾음: {}", userNum);
                }
            }
        }

        // 토큰은 유효하지만 해당하는 사용자를 찾지 못한 경우
        if (user == null || userNum == null) {
            log.warn("[DELETE] 토큰은 있지만 DB에서 사용자를 찾지 못함");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("사용자 정보를 찾을 수 없습니다.");
        }

        // 4. 회원탈퇴 처리
        try {
            userService.deleteUser(userNum);
            log.info("[DELETE] 회원탈퇴 완료: userNum = {}", userNum);
            
            // 삭제 확인
            boolean stillExists = userRepository.findByUserNum(userNum).isPresent();
            if (stillExists) {
                log.error("[DELETE] 삭제 후에도 사용자가 여전히 존재합니다: userNum = {}", userNum);
                return ResponseEntity
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("회원탈퇴 처리 중 오류가 발생했습니다.");
            }
            
            return ResponseEntity.ok("회원탈퇴가 완료되었습니다.");
        } catch (IllegalArgumentException e) {
            log.error("[DELETE] 회원탈퇴 실패: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        } catch (IllegalStateException e) {
            log.error("[DELETE] 회원탈퇴 실패: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        } catch (Exception e) {
            log.error("[DELETE] 회원탈퇴 중 예외 발생: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("회원탈퇴 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

}
