package com.MT.laos.controller;

import com.MT.laos.DAO.User;
import com.MT.laos.DTO.OpenAiResponseDTO;
import com.MT.laos.repository.UserRepository;
import com.MT.laos.service.JwtService;
import com.MT.laos.service.OpenAiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@Slf4j
public class OpenAiController {
    private final OpenAiService openAiService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    
    public OpenAiController(OpenAiService openAiService, JwtService jwtService, UserRepository userRepository){
        this.openAiService = openAiService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }
    
    @PostMapping("/question")
    public ResponseEntity<?> getChatCpmpletions(HttpServletRequest request, @RequestBody String prompt){
        log.info("[OpenAI] POST /question 요청 도착");
        log.info("[OpenAI] Authorization 헤더: {}", request.getHeader("Authorization"));
        
        // AccessToken 추출
        Optional<String> accessTokenOptional = jwtService.extractAccessToken(request);
        if (accessTokenOptional.isEmpty()) {
            log.warn("[OpenAI] AccessToken 없음 - 헤더 이름: {}", jwtService.getAccessHeader());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("AccessToken이 필요합니다.");
        }
        
        String accessToken = accessTokenOptional.get();
        log.info("[OpenAI] 추출된 토큰 길이: {}", accessToken.length());
        log.info("[OpenAI] 추출된 토큰 (앞 30자): {}", accessToken.length() > 30 ? accessToken.substring(0, 30) + "..." : accessToken);
        log.info("[OpenAI] 토큰 부분 개수: {}", accessToken.split("\\.").length);
        
        // 토큰 유효성 검사
        if (!jwtService.isTokenValid(accessToken)) {
            log.warn("[OpenAI] 토큰 유효성 검사 실패");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("유효하지 않은 AccessToken 입니다.");
        }
        log.info("[OpenAI] 토큰 유효성 검사 통과");
        
        // 사용자 찾기 (로컬 → 소셜 순서로 시도)
        User user = null;
        
        // 로컬 로그인: userNum으로 찾기
        Optional<Integer> userNumClaim = jwtService.extractUserNum(accessToken);
        if (userNumClaim.isPresent()) {
            Integer userNum = userNumClaim.get();
            log.info("[OpenAI] userNum으로 사용자 조회 시도: {}", userNum);
            user = userRepository.findByUserNum(userNum).orElse(null);
            if (user != null) {
                log.info("[OpenAI] userNum으로 사용자 찾음: {}", user.getUserNum());
            }
        } else {
            log.info("[OpenAI] userNum 클레임 없음");
        }
        
        // 소셜 로그인: email + provider로 찾기
        if (user == null) {
            Optional<String> emailClaim = jwtService.extractEmail(accessToken);
            Optional<com.MT.laos.DAO.Provider> providerClaim = jwtService.extractProvider(accessToken);
            log.info("[OpenAI] email 클레임: {}, provider 클레임: {}", 
                    emailClaim.isPresent() ? emailClaim.get() : "없음",
                    providerClaim.isPresent() ? providerClaim.get() : "없음");
            
            if (emailClaim.isPresent() && providerClaim.isPresent()) {
                String email = emailClaim.get();
                String provider = providerClaim.get().name();
                log.info("[OpenAI] email + provider로 사용자 조회 시도: {}, {}", email, provider);
                user = userRepository.findByEmailAndProvider(email, provider).orElse(null);
                if (user != null) {
                    log.info("[OpenAI] email + provider로 사용자 찾음: {}", user.getUserNum());
                }
            }
        }
        
        if (user == null) {
            log.warn("[OpenAI] 사용자를 찾을 수 없습니다.");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("사용자를 찾을 수 없습니다.");
        }
        
        try {
            log.info("[OpenAI] OpenAI API 호출 시작 - 사용자: {}", user.getUserNum());
            // 사용자의 MBTI 정보 가져오기
            String mbtiType = null;
            if (user.getMbti() != null) {
                mbtiType = user.getMbti().getMbtiType();
                log.info("[OpenAI] 사용자 MBTI: {}", mbtiType);
            } else {
                log.info("[OpenAI] 사용자 MBTI 정보 없음");
            }
            String content = openAiService.getContent(openAiService.getDefalut_prompt(), prompt, mbtiType);
            log.info("[OpenAI] OpenAI API 호출 성공");
            OpenAiResponseDTO response = new OpenAiResponseDTO(content);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[OpenAI] 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("OpenAI API 호출 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
