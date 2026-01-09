package com.Flirt.laos.repository;

import com.Flirt.laos.DAO.Ment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentRepository extends JpaRepository<Ment, Long> {

    // 승인 상태에 따른 멘트 목록 조회 (0: 미승인, 1: 승인, 2: 거절)
    List<Ment> findByIsApproved(Long isApproved);

    // 태그별 승인된 멘트 목록 조회
    List<Ment> findByTagAndIsApproved(String tag, Long isApproved);

    // 특정 사용자가 작성한 멘트 목록 조회
    List<Ment> findByAuthorUserNum(Integer userNum);
}