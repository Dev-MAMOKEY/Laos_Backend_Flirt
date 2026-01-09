package com.Flirt.laos.service;

import com.Flirt.laos.DAO.Ment;
import com.Flirt.laos.DAO.User;
import com.Flirt.laos.DTO.MentRequestDTO;
import com.Flirt.laos.DTO.MentResponseDTO;
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
public class MentService {

    private final MentRepository mentRepository;
    private final UserRepository userRepository;

    /**
     * 사용자가 멘트 등록을 요청
     * 디폴트 '미승인(0)'
     */
    @Transactional
    public void requestMent(Integer userNum, MentRequestDTO dto) {
        log.info("[MentService] 멘트 등록 요청 시작: userNum = {}", userNum);

        User author = userRepository.findByUserNum(userNum)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다."));

        Ment ment = Ment.builder()
                .contentKo(dto.getContentKo())
                .contentLo(dto.getContentLo())
                .tag(dto.getTag())
                .author(author)
                .isApproved(0L) // 미승인 상태
                .build();

        mentRepository.save(ment);
        log.info("[MentService] 멘트 요청 저장 완료: mentId = {}", ment.getMentId());
    }

    /**
     * 관리자가 멘트를 승인 (isApproved -> 1)
     */
    @Transactional
    public void approveMent(Long mentId) {
        Ment ment = mentRepository.findById(mentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 멘트가 존재하지 않습니다."));

        ment.setIsApproved(1L);
        log.info("[MentService] 멘트 승인 완료: mentId = {}", mentId);
    }

    /**
     * 관리자가 멘트 승인을 거절 (isApproved -> 2)
     */
    @Transactional
    public void rejectMent(Long mentId, String reason) {
        Ment ment = mentRepository.findById(mentId)
                .orElseThrow(() -> new IllegalArgumentException("해당 멘트가 존재하지 않습니다."));

        ment.setIsApproved(2L);
        ment.setReason(reason);
        log.info("[MentService] 멘트 거절 완료: mentId = {}, 사유 = {}", mentId, reason);
    }

    /**
     * 승인된 멘트 전체 목록 조회 (공통)
     */
    @Transactional(readOnly = true)
    public List<MentResponseDTO> getApprovedMents() {
        return mentRepository.findByIsApproved(1L).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 승인 대기 중인 멘트 목록 조회 (관리자용)
     */
    @Transactional(readOnly = true)
    public List<MentResponseDTO> getPendingMents() {
        return mentRepository.findByIsApproved(0L).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 내가 작성한 멘트 목록 조회 (사용자용)
     */
    @Transactional(readOnly = true)
    public List<MentResponseDTO> getMyMents(Integer userNum) {
        return mentRepository.findByAuthorUserNum(userNum).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 태그별 승인된 멘트 조회
     */
    @Transactional(readOnly = true)
    public List<MentResponseDTO> getMentsByTag(String tag) {
        return mentRepository.findByTagAndIsApproved(tag, 1L).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 멘트 삭제
     */
    @Transactional
    public void deleteMent(Long mentId) {
        if (!mentRepository.existsById(mentId)) {
            throw new IllegalArgumentException("삭제할 멘트가 존재하지 않습니다.");
        }
        mentRepository.deleteById(mentId);
        log.info("[MentService] 멘트 삭제 완료: mentId = {}", mentId);
    }

    // Entity -> DTO 변환 로직
    private MentResponseDTO convertToDTO(Ment ment) {
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