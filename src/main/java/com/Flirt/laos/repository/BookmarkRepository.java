package com.Flirt.laos.repository;

import com.Flirt.laos.DAO.Bookmark;
import com.Flirt.laos.DAO.Ment;
import com.Flirt.laos.DAO.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    // 특정 사용자와 특정 멘트의 북마크 존재 여부 확인 및 조회
    Optional<Bookmark> findByUserAndMent(User user, Ment ment);

    // 특정 사용자가 등록한 모든 북마크 목록 조회 (마이페이지용)
    List<Bookmark> findByUser_UserNum(Integer userNum);

    // 북마크 삭제 (중복 방지를 위해 유저와 멘트 조합으로 삭제)
    void deleteByUserAndMent(User user, Ment ment);
}