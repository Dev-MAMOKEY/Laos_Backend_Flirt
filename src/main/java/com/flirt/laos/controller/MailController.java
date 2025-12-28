package com.flirt.laos.controller;

import com.flirt.laos.DTO.EmailVerificationRequestDTO;
import com.flirt.laos.service.EmailVerificationService;
import com.flirt.laos.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MailController {
    
    private final MailService mailService;
    private final EmailVerificationService emailVerificationService;
    
    @PostMapping(value = "/auth/email/send")
    public ResponseEntity<?> sendVerificationEmail(@RequestBody EmailVerificationRequestDTO requestDTO) {
        try {
            // 이메일 유효성 검사
            if (requestDTO.getEmail() == null || requestDTO.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body("이메일 주소를 입력해주세요.");
            }
            
            // 인증 코드 생성 및 이메일 발송
            String verificationCode = mailService.sendMail(requestDTO.getEmail());
            
            // 인증 코드 저장
            emailVerificationService.saveVerificationCode(requestDTO.getEmail(), verificationCode);
            
            return ResponseEntity.ok().body("이메일 인증 코드가 발송되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("이메일 발송에 실패했습니다: " + e.getMessage());
        }
    }
}
