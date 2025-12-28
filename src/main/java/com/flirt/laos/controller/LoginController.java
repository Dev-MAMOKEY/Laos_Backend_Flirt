package com.flirt.laos.controller;

import com.flirt.laos.DTO.AuthTokensDTO;
import com.flirt.laos.DTO.LocalLoginDTO;
import com.flirt.laos.DTO.RegisterDTO;
import com.flirt.laos.service.LocalLoginService;
import com.flirt.laos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class LoginController {

    private final LocalLoginService loginService;
    private final UserService userService;

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
}
