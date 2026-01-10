package com.Flirt.laos.service;

import com.Flirt.laos.DAO.Bookmark;
import com.Flirt.laos.DAO.Ment;
import com.Flirt.laos.DAO.User;
import com.Flirt.laos.DTO.MentResponseDTO;
import com.Flirt.laos.repository.BookmarkRepository;
import com.Flirt.laos.repository.MentRepository;
import com.Flirt.laos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final MentRepository mentRepository;
    private final UserRepository userRepository;

    /**
     * 북마크 추가
     */
    @Transactional
    public void addBookmark(Integer userNum, Long mentId) {
        User user = userRepository.findByUserNum(userNum)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다."));
        Ment ment = mentRepository.findById(mentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 멘트를 찾을 수 없습니다."));

        // 이미 북마크가 존재하는지 확인
        if (bookmarkRepository.findByUserAndMent(user, ment).isPresent()) {
            throw new IllegalArgumentException("이미 북마크에 등록된 멘트입니다.");
        }

        Bookmark bookmark = Bookmark.builder()
                .user(user)
                .ment(ment)
                .build();

        bookmarkRepository.save(bookmark);
        log.info("[BookmarkService] 북마크 등록 완료: userNum={}, mentId={}", userNum, mentId);
    }

    /**
     * 북마크 해제 (삭제)
     */
    @Transactional
    public void deleteBookmark(Integer userNum, Long mentId) {
        User user = userRepository.findByUserNum(userNum)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다."));
        Ment ment = mentRepository.findById(mentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 멘트를 찾을 수 없습니다."));

        bookmarkRepository.deleteByUserAndMent(user, ment);
        log.info("[BookmarkService] 북마크 삭제 완료: userNum={}, mentId={}", userNum, mentId);
    }

    /**
     * 내가 북마크한 멘트 목록 조회
     */
    @Transactional(readOnly = true)
    public List<MentResponseDTO> getMyBookmarks(Integer userNum) {
        return bookmarkRepository.findByUser_UserNum(userNum).stream()
                .map(bookmark -> convertToMentDTO(bookmark.getMent()))
                .collect(Collectors.toList());
    }

    // Ment 엔티티를 MentResponseDTO로 변환 (기존 MentService 로직 활용)
    private MentResponseDTO convertToMentDTO(Ment ment) {
        return MentResponseDTO.builder()
                .mentId(ment.getMentId())
                .contentKo(ment.getContentKo())
                .contentLo(ment.getContentLo())
                .tag(ment.getTag())
                .authorNickname(ment.getAuthor() != null ? ment.getAuthor().getNickname() : "알 수 없음")
                .createdAt(ment.getCreatedAt())
                .isApproved(ment.getIsApproved())
                .reason(ment.getReason())
                .build();
    }
}