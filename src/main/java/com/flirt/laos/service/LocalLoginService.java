package com.flirt.laos.service;

import com.flirt.laos.DTO.AuthTokensDTO;
import com.flirt.laos.DTO.LocalLoginDTO;
import com.flirt.laos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocalLoginService {

    private final UserRepository userRepository;

    private final BCryptPasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    // 로컬 로그인 사용자 인증 로직
    @Transactional
    public Optional<AuthTokensDTO> localLogin(LocalLoginDTO loginDTO) { // 로그인 성공시 인증 토큰 반환함
        if (loginDTO.getLocalId() == null || loginDTO.getPassword() == null || loginDTO.getLocalId().isBlank() || loginDTO.getPassword().isBlank()) {
            throw new IllegalArgumentException("아이디와 비밀번호를 입력하세요.");
        }

        // 로컬 로그인 성공시 아이디 비밀번호 확인 후 AT , RT 발급 후 RT 저장 로직
        return userRepository.findByLocalId(loginDTO.getLocalId())
                .filter(user -> passwordEncoder.matches(loginDTO.getPassword(), user.getPassword()))
                .map(user -> {
                    // 토큰 생성 로직 구현 (예: JWT 토큰 생성) jwtService에서 토큰 생성 메서드 호출
                    String accessToken = jwtService.createAccessTokenByUserNum(user.getUserNum());
                    String refreshToken = jwtService.createRefreshToken();
                    jwtService.updateRefreshTokenByUserNum(user.getUserNum(), refreshToken);
                    log.info("로컬 로그인 성공! 유저 아이디 = {}, accessToken = Bearer {}, refreshToken = {}",  user.getLocalId(), accessToken, refreshToken);
                    return new AuthTokensDTO(accessToken, refreshToken);
                });
    }
}
