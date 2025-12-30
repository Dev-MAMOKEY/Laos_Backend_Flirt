package com.MT.laos.repository;

import com.MT.laos.DAO.MBTI;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MbtiRepository extends JpaRepository<MBTI, Integer> {

    Optional<MBTI> findByMbtiType(String mbtiType); // MBTI 타입으로 MBTI 엔티티 조회
}
