package com.Flirt.laos.controller;

import com.Flirt.laos.DTO.AuthTokensDTO;
import com.Flirt.laos.DTO.GoogleAuthCodeDTO;
import com.Flirt.laos.service.GoogleOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프론트엔드에서 받은 Google OAuth2 인가 코드를
 * 백엔드에서 액세스 토큰으로 교환하고, User 정보를 조회한 뒤
 * 자체 JWT(AccessToken, RefreshToken)를 발급해 주는 엔드포인트.
 *
 * Flow:
 *  1) FE: Google 로그인 → authorization_code 발급
 *  2) FE → BE: /oauth/callback/google 로 code 전달
 *  3) BE: code 를 이용해 Google Token Endpoint 호출
 *  4) BE: Google access_token 으로 userinfo 조회
 *  5) BE: User DB 저장/조회 후 자체 JWT AT/RT 발급
 */
@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
@Slf4j
public class SocialAuthController {

    private final GoogleOAuthService googleOAuthService;

    @PostMapping("/callback/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleAuthCodeDTO dto) {
        try {
            log.info("[Google OAuth] /oauth/callback/google 요청 수신, code 수신 여부 = {}", dto.getCode() != null);
            AuthTokensDTO tokens = googleOAuthService.loginWithCode(dto.getCode());
            return ResponseEntity.ok(tokens);
        } catch (IllegalArgumentException e) {
            // 클라이언트가 잘못된 요청을 보낸 경우 (code 없음/형식 오류 등)
            log.warn("[Google OAuth] 잘못된 요청: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            // 구글 응답 오류, 토큰 교환 실패 등 비즈니스 예외
            log.warn("[Google OAuth] 토큰 교환/사용자 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            // 여기까지 오는 것은 예기치 못한 서버 내부 오류
            log.error("[Google OAuth] 소셜 로그인 처리 중 예기치 못한 오류", e);
            // 상태 코드를 400으로 내려서 항상 클라이언트가 상세 메시지를 볼 수 있게 함
            return ResponseEntity.badRequest()
                    .body("UNEXPECTED_SERVER_ERROR: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}
