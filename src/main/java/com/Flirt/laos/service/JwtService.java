package com.Flirt.laos.service;

import com.Flirt.laos.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
@Getter
public class JwtService { // JWT 생성 (AT, RT) , 정보 조회 판단 로직

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access.expiration}")
    private Long accessExpiration;

    @Value("${jwt.refresh.expiration}")
    private Long refreshExpiration;

    @Value("${jwt.access.header}")
    private String accessHeader;

    @Value("${jwt.refresh.header}")
    private String refreshHeader;

    private static final String ACCESS_TOKEN_Subject = "AccessToken";
    private static final String REFRESH_TOKEN_Subject = "RefreshToken";
    private static final String USER_NUM_CLAIM = "userNum"; // userNum 유지
    private static final String BEARER = "Bearer ";

    private final UserRepository userRepository;

    public JwtService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * AccessToken 생성 (userNum 기반으로 통일)
     */
    public String createAccessToken(Integer userNum) {
        Date now = new Date();
        return JWT.create()
                .withSubject(ACCESS_TOKEN_Subject)
                .withExpiresAt(new Date(now.getTime() + accessExpiration))
                .withClaim(USER_NUM_CLAIM, userNum)
                .sign(Algorithm.HMAC512(secret));
    }

    /**
     * RefreshToken 생성
     */
    public String createRefreshToken() {
        Date now = new Date();
        return JWT.create()
                .withSubject(REFRESH_TOKEN_Subject)
                .withExpiresAt(new Date(now.getTime() + refreshExpiration))
                .sign(Algorithm.HMAC512(secret));
    }

    /**
     * 토큰 유효성 검증 로직
     */
    public boolean isTokenValid(String token) {
        try {
            JWT.require(Algorithm.HMAC512(secret)).build().verify(token);
            return true;
        } catch (Exception e) {
            log.error("유효하지 않은 토큰입니다. {}", e.getMessage());
            return false;
        }
    }

    /**
     * AccessToken에서 userNum 추출
     */
    public Optional<Integer> extractUserNum(String accessToken) {
        try {
            var claim = JWT.require(Algorithm.HMAC512(secret))
                    .build()
                    .verify(accessToken.replace(BEARER, ""))
                    .getClaim(USER_NUM_CLAIM);

            if (claim.isNull()) return Optional.empty();
            return Optional.ofNullable(claim.asInt());
        } catch (Exception e) {
            log.error("유효하지 않은 액세스 토큰입니다. userNum 추출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 응답 헤더에 AccessToken 설정
     */
    public void sendAccessToken(HttpServletResponse response, String accessToken) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(accessHeader, BEARER + accessToken);
    }

    /**
     * 응답 헤더에 AccessToken + RefreshToken 설정
     */
    public void sendAccessAndRefreshToken(HttpServletResponse response, String accessToken, String refreshToken) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(accessHeader, BEARER + accessToken);
        response.setHeader(refreshHeader, BEARER + refreshToken);
    }

    /**
     * RefreshToken 업데이트
     */
    public void updateRefreshTokenByUserNum(Integer userNum, String refreshToken) {
        userRepository.findByUserNum(userNum).ifPresent(user -> {
            user.updateRefreshToken(refreshToken);
            userRepository.save(user);
        });
    }

    /**
     * RefreshToken 삭제
     */
    public void deleteRefreshTokenByUserNum(Integer userNum) {
        userRepository.findByUserNum(userNum).ifPresent(user -> {
            user.updateRefreshToken(null);
            userRepository.save(user);
            log.info("사용자 번호 {}의 RefreshToken이 삭제되었습니다.", userNum);
        });
    }

    /**
     * 헤더에서 AccessToken 추출
     */
    public Optional<String> extractAccessToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(accessHeader))
                .filter(token -> token.startsWith(BEARER))
                .map(token -> token.replace(BEARER, ""));
    }

    /**
     * 헤더에서 RefreshToken 추출
     */
    public Optional<String> extractRefreshToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(refreshHeader))
                .filter(token -> token.startsWith(BEARER))
                .map(token -> token.replace(BEARER, ""));
    }
}