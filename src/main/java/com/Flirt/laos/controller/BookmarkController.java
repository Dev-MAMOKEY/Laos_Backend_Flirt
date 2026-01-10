package com.Flirt.laos.controller;

import com.Flirt.laos.DAO.User;
import com.Flirt.laos.DTO.MentResponseDTO;
import com.Flirt.laos.repository.UserRepository;
import com.Flirt.laos.service.BookmarkService;
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
public class BookmarkController {

    private final BookmarkService bookmarkService;
    private final UserRepository userRepository;

    /**
     * 유저 - 북마크 추가
     */
    @PostMapping("/add/bookmark")
    public ResponseEntity<?> addBookmark(@RequestParam Long mentId) {
        log.info("[BOOKMARK] POST /add/bookmark 요청 도착: mentId={}", mentId);
        User user = extractUserFromToken();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        try {
            bookmarkService.addBookmark(user.getUserNum(), mentId);
            return ResponseEntity.ok("북마크에 등록되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * 유저 - 북마크 삭제
     */
    @DeleteMapping("/delete/bookmark")
    public ResponseEntity<?> deleteBookmark(@RequestParam Long mentId) {
        log.info("[BOOKMARK] DELETE /delete/bookmark 요청 도착: mentId={}", mentId);
        User user = extractUserFromToken();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        try {
            bookmarkService.deleteBookmark(user.getUserNum(), mentId);
            return ResponseEntity.ok("북마크가 삭제되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * 유저 - 내 북마크 목록 조회
     */
    @GetMapping("/my/bookmarks")
    public ResponseEntity<?> getMyBookmarks() {
        log.info("[BOOKMARK] GET /my/bookmarks 요청 도착");
        User user = extractUserFromToken();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        List<MentResponseDTO> list = bookmarkService.getMyBookmarks(user.getUserNum());
        return ResponseEntity.ok(list);
    }

    /**
     * [내부 헬퍼] SecurityContext에서 User 엔티티 추출
     * MentController와 동일한 로직 사용
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