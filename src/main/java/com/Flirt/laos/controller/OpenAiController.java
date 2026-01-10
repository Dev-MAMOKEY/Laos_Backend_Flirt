package com.Flirt.laos.controller;

import com.Flirt.laos.DAO.User;
import com.Flirt.laos.DTO.OpenAiResponseDTO;
import com.Flirt.laos.repository.UserRepository;
import com.Flirt.laos.service.JwtService;
import com.Flirt.laos.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/translate")
    public ResponseEntity<?> getChatCpmpletions(@RequestBody String prompt){
        log.info("[OpenAI] POST /translate 요청 도착");

        // [수정] SecurityContext에서 인증된 사용자 추출
        User user = extractUserFromToken();

        if (user == null) {
            log.warn("[OpenAI] 사용자를 찾을 수 없거나 인증되지 않았습니다.");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        try {
            log.info("[OpenAI] OpenAI API 호출 시작 - 사용자: {}", user.getUserNum());
            String content = openAiService.getContent(openAiService.getDefalut_prompt(), prompt);
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

    /**
     * [내부 헬퍼] SecurityContext에서 User 엔티티 추출
     */
    private User extractUserFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            return userRepository.findByEmail(username)
                    .or(() -> userRepository.findByLocalId(username))
                    .orElse(null);
        }
        return null;
    }
}