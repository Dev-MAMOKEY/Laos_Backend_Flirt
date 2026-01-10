package com.Flirt.laos.controller;

import com.Flirt.laos.DAO.User;
import com.Flirt.laos.DTO.MentAdminRequestDTO;
import com.Flirt.laos.DTO.MentRequestDTO;
import com.Flirt.laos.DTO.MentResponseDTO;
import com.Flirt.laos.repository.UserRepository;
import com.Flirt.laos.service.JwtService;
import com.Flirt.laos.service.MentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
public class MentController {

    private final MentService mentService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * 유저 - 플러팅 멘트 등록 요청
     */
    @PostMapping("/request/comment")
    public ResponseEntity<?> requestMent(HttpServletRequest request, @RequestBody MentRequestDTO dto) {
        log.info("[MENT] POST /request/comment 요청 도착");

        User user = extractUserFromToken();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        try {
            mentService.requestMent(user.getUserNum(), dto);
            return ResponseEntity.ok("멘트 등록 요청이 완료되었습니다. 관리자 승인 후 게시됩니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * 공통 - 승인된 멘트 목록 조회 (태그 필터 가능)
     * 예: /ment/list 또는 /ment/list?tag=공대
     */
    @GetMapping("/ment/list")
    public ResponseEntity<List<MentResponseDTO>> getMentList(@RequestParam(required = false) String tag) {
        if (tag != null && !tag.isBlank()) {
            return ResponseEntity.ok(mentService.getMentsByTag(tag));
        }
        return ResponseEntity.ok(mentService.getApprovedMents());
    }

    /**
     * 유저 - 내가 신청한 멘트 목록 조회 (상태 포함)
     */
    @GetMapping("/my/ment/list")
    public ResponseEntity<?> getMyMentList() {
        log.info("[MENT] GET /my/ment/list 요청 도착");
        User user = extractUserFromToken();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증된 사용자 정보를 찾을 수 없습니다.");
        }
        return ResponseEntity.ok(mentService.getMyMents(user.getUserNum()));
    }

    /**
     * 관리자 - 승인 대기 중인 멘트 목록 조회
     */
    @GetMapping("/admin/ment/pending")
    public ResponseEntity<List<MentResponseDTO>> getPendingMentList() {
        log.info("[ADMIN] 승인 대기 목록 조회");
        return ResponseEntity.ok(mentService.getPendingMents());
    }

    /**
     * 관리자 - 멘트 승인 처리
     */
    @PostMapping("/add/comment")
    public ResponseEntity<?> approveMent(@RequestParam Long mentId) {
        log.info("[ADMIN] 멘트 승인 시도: mentId = {}", mentId);
        try {
            mentService.approveMent(mentId);
            return ResponseEntity.ok("멘트 승인이 완료되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * 관리자 - 멘트 거절 처리
     */
    @PostMapping("/request/negative")
    public ResponseEntity<?> rejectMent(@RequestParam Long mentId, @RequestBody MentAdminRequestDTO dto) {
        log.info("[ADMIN] 멘트 거절 시도: mentId = {}, 사유 = {}", mentId, dto.getReason());
        try {
            mentService.rejectMent(mentId, dto.getReason());
            return ResponseEntity.ok("멘트 거절 처리가 완료되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * 멘트 삭제 (관리자 또는 작성자 권한 체크 로직 추가 권장)
     */
    @DeleteMapping("/delete/ment/{mentId}")
    public ResponseEntity<?> deleteMent(@PathVariable Long mentId) {
        try {
            mentService.deleteMent(mentId);
            return ResponseEntity.ok("멘트가 성공적으로 삭제되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * [내부 헬퍼 수정] 이미 Filter에서 검증된 정보를 SecurityContext에서 가져오도록 변경
     *
     */
    private User extractUserFromToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getPrincipal().equals("anonymousUser")) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            // JwtAuthenticationFilter에서 username에 email이나 localId를 넣어두었으므로 이를 활용해 조회
            return userRepository.findByEmail(username)
                    .or(() -> userRepository.findByLocalId(username))
                    .orElse(null);
        }
        return null;
    }
}