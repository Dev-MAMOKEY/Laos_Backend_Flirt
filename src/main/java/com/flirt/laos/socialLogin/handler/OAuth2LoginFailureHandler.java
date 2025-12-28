package com.flirt.laos.socialLogin.handler;

import com.flirt.laos.repository.UserRepository;
import com.flirt.laos.service.JwtService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 에러
        response.getWriter().write("소셜 로그인 실패"); // 클라이언트에게 실패 메시지를 응답 본문에 작성
        log.info("소셜 로그인에 실패했습니다. 에러 메시지: {}", exception.getMessage()); // 에러가 나온 이유 로그에 찍어서 확인
    }
}
