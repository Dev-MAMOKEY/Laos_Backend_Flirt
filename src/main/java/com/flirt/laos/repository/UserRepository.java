package com.flirt.laos.repository;
import com.flirt.laos.DAO.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    // 중복 확인 용도
    Optional<User> findByEmail(String email); // 이메일 중복 확인 및 조회

    Optional<User> findByRefreshToken(String refreshToken); // 리프레시 토큰으로 사용자 조회

    Optional<User> findByLocalId(String localId); // 로컬ID 중복 확인 및 조회

    Optional<User> findByNickname(String nickname); // 닉네임 중복 확인 및 조회

    Optional<User> findByUserNum(Integer userNum); // 사용자 고유 번호로 사용자 조회 용도

    // provider 컬럼은 문자열(String)로 저장되므로, 쿼리 파라미터도 String으로 맞춘다.
    Optional<User> findByEmailAndProvider (String email, String provider); // 소셜 로그인 시 이메일 + 소셜 제공자 일치 사용자 조회 RT DB에 저장할 때
}
