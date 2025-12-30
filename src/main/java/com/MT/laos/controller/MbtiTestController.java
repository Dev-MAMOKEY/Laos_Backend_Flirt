package com.MT.laos.controller;

import com.MT.laos.DAO.User;
import com.MT.laos.DTO.MbtiTestDTO;
import com.MT.laos.repository.UserRepository;
import com.MT.laos.service.JwtService;
import com.MT.laos.service.MbtiService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * MBTI 테스트 관련 엔드포인트를 제공하는 컨트롤러
 *
 * - 로그인(로컬/소셜) 후, 사용자가 4개의 문항에 대해 0/1 값을 선택해서 전송하면
 *   서버가 MBTI 결과를 계산하고 DB(User.mbti)에 저장한 뒤 결과 문자열을 반환한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/mbti")
@Slf4j
public class MbtiTestController {

    private final MbtiService mbtiService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * MBTI 성향 테스트 화면/페이지용 기본 엔드포인트
     *
     * - 실제 화면(UI)은 프론트엔드(React, Vue 등)에서 만들고
     * - 이 API는 단순히 "/mbti/test" 주소가 살아있다는 것만 알려주는 용도로 사용해도 됩니다.
     * - 프론트에서는 이 경로를 기준으로 라우팅을 해서 테스트 화면을 띄우면 됩니다.
     */
    @GetMapping("/test")
    public ResponseEntity<String> showMbtiTestPage() {
        log.info("[MBTI] GET /mbti/test 요청 도착");
        return ResponseEntity.ok("MBTI 성향 테스트 페이지(API 연결용)");
    }

    /**
     * MBTI 테스트 결과 제출 엔드포인트
     *
     * - 요청 헤더의 AccessToken(JWT)으로 현재 로그인된 사용자를 식별
     * - 사용자가 보낸 4개의 답변(0/1)을 가지고 MBTI를 계산
     * - 계산된 MBTI를 User 엔티티에 저장한 뒤 결과 문자열을 응답으로 반환
     *
     * @param request     HTTP 요청 (헤더에서 AccessToken 추출용)
     * @param mbtiTestDTO 사용자가 선택한 MBTI 답변 리스트
     * @return ex) "INFP", "ESTJ" 와 같은 최종 MBTI 문자열
     */
    @PostMapping("/test")
    public ResponseEntity<?> submitMbtiTest(HttpServletRequest request,
                                            @RequestBody MbtiTestDTO mbtiTestDTO) {
        log.info("[MBTI] POST /mbti/test 요청 도착");

        // 1. 요청 바디 유효성 검사 (답변이 비어있으면 잘못된 요청으로 처리)
        if (mbtiTestDTO == null || mbtiTestDTO.getAnswers() == null || mbtiTestDTO.getAnswers().isEmpty()) {
            log.warn("[MBTI] answers 가 비어 있음");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("MBTI 답변이 비어 있습니다.");
        }

        // 2. 헤더에서 AccessToken 추출 (예: Authorization: Bearer xxx 형태)
        log.info("[MBTI] 요청 헤더에서 AT 추출 시도, 헤더 이름 = {}", jwtService.getAccessHeader());
        Optional<String> accessTokenOptional = jwtService.extractAccessToken(request);
        if (accessTokenOptional.isEmpty()) {
            log.warn("[MBTI] AccessToken 없음");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("AccessToken이 필요합니다.");
        }

        String accessToken = accessTokenOptional.get();
        log.info("[MBTI] 추출된 AT = {}", accessToken);

        // 3. 토큰이 유효한지 한 번 더 검사
        if (!jwtService.isTokenValid(accessToken)) {
            log.warn("[MBTI] 토큰 유효성 검사 실패");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("유효하지 않은 AccessToken 입니다.");
        }

        // 4. AccessToken에서 사용자 찾기 (로컬 → 소셜 순서로 시도)
        User user = null;

        // 4-1) 로컬 로그인: 토큰 안에 userNum(사용자 번호)이 들어있는 경우
        Optional<Integer> userNumClaim = jwtService.extractUserNum(accessToken);
        if (userNumClaim.isPresent()) {
            Integer userNum = userNumClaim.get();
            log.info("[MBTI] AT 에서 userNum 클레임 추출: {}", userNum);
            user = userRepository.findByUserNum(userNum).orElse(null);
            log.info("[MBTI] userNum 으로 조회한 User = {}", user != null ? user.getUserNum() : null);
        }

        // 4-2) 소셜 로그인: userNum이 없으면 email + provider 로 찾기
        if (user == null) {
            Optional<String> emailClaim = jwtService.extractEmail(accessToken);
            Optional<Enum<?>> providerClaim = jwtService.extractProvider(accessToken).map(p -> (Enum<?>) p);

            if (emailClaim.isPresent() && providerClaim.isPresent()) {
                String email = emailClaim.get();
                String provider = providerClaim.get().name(); // enum → 문자열 (DB provider 컬럼과 동일)
                log.info("[MBTI] email + provider 로 사용자 조회 시도: {}, {}", email, provider);
                user = userRepository.findByEmailAndProvider(email, provider).orElse(null);
                log.info("[MBTI] email + provider 로 조회한 User = {}", user != null ? user.getUserNum() : null);
            }
        }

        // 토큰은 유효하지만 해당하는 사용자를 찾지 못한 경우
        if (user == null) {
            log.warn("[MBTI] 토큰은 있지만 DB 에서 사용자를 찾지 못함");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("사용자 정보를 찾을 수 없습니다.");
        }

        // 5. 서비스 레이어에서 MBTI 계산 + User.mbti 저장
        log.info("[MBTI] MBTI 계산 시작, userNum = {}", user.getUserNum());
        String result = mbtiService.resultMbti(user.getUserNum(), mbtiTestDTO);

        // 6. 최종 MBTI 문자열을 그대로 반환 (프론트에서 이 값을 이용해 화면에 표시)
        log.info("[MBTI] MBTI 계산 완료, 결과 = {}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * MBTI 결과 조회 엔드포인트 (간단 버전)
     *
     * - 프론트에서 "/mbti/test/result/ESFJ" 같은 형태로 라우팅을 잡고,
     *   이 API를 호출해서 결과 문자열을 그대로 사용할 수 있습니다.
     * - 현재는 단순히 PathVariable 로 받은 MBTI 문자열을 그대로 반환만 합니다.
     *   추후 DB에서 MBTI별 설명/해석을 조회해서 함께 내려주고 싶다면 여기에서 확장하면 됩니다.
     */
    @GetMapping("/test/result/{mbti}")
    public ResponseEntity<String> getMbtiResult(@PathVariable("mbti") String mbti) {
        return ResponseEntity.ok(mbti);
    }
}
