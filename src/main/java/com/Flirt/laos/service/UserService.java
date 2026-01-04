package com.Flirt.laos.service;

import com.Flirt.laos.DAO.User;
import com.Flirt.laos.DAO.Provider;
import com.Flirt.laos.DTO.RegisterDTO;
import com.Flirt.laos.DTO.UserUpdateDTO;
import com.Flirt.laos.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Transactional
    public User registerUser(RegisterDTO registerDTO) { // 회원가입 처리 메서드 CRUD의 Create

        // 중복 로컬ID 확인 처리 로직
        if (userRepository.findByLocalId(registerDTO.getLocalId()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 ID입니다.");
        }

        // 중복 닉네임 확인 처리 로직
        if (userRepository.findByNickname(registerDTO.getNickname()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        // 중복 이메일 확인 처리 로직
        if (userRepository.findByEmail(registerDTO.getEmail()).isPresent()) { // 이메일이 이미 존재하는지 확인
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 회원 정보 저장 로직

        User user = User.builder()
                .localId(registerDTO.getLocalId())
                .password(passwordEncoder.encode(registerDTO.getPassword())) // 비밀번호 암호화, SecurityConfig에 빈으로 등록된 passwordEncoder 사용
                .nickname(registerDTO.getNickname())
                .email(registerDTO.getEmail())
                .provider(String.valueOf(Provider.LOCAL)) // 로컬 회원가입이므로 provider는 LOCAL로 설정
                .build();
        return userRepository.save(user);
    }

    // 사용자 고유 번호로 사용자 조회
    @Transactional
    public User getUser(String userNum) {
        return userRepository.findByUserNum(Integer.valueOf(userNum))
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자가 존재하지 않습니다."));
    }

    // 사용자 회원 정보 수정 로직 CRUD의 Update
    @Transactional
    public User updateUser(Integer userNum, UserUpdateDTO userUpdateDTO) { // 사용자 고유 번호로 조회 후 수정
        User user = userRepository.findByUserNum(userNum)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자가 존재하지 않습니다."));

        // 닉네임 변경시 처리 로직
        String newNickname = userUpdateDTO.getNickname();

        if (newNickname != null) {

            if (newNickname.isBlank()) {
                throw new IllegalArgumentException("닉네임은 공백일 수 없습니다.");
            }

            if (newNickname.equals(user.getNickname())) {
                throw new IllegalArgumentException("기존 닉네임과 동일합니다.");
            }

            if (userRepository.findByNickname(newNickname).isPresent()) {
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
            }
            user.setNickname(newNickname);
        }

        // 비밀번호 변경시 처리 로직
        String newPassword = userUpdateDTO.getPassword();

        if (newPassword != null) {
            if (newPassword.isBlank()) {
                throw new IllegalArgumentException("비밀번호는 공백일 수 없습니다.");
            }
            user.setPassword(passwordEncoder.encode(newPassword));
        }
        return user;
    }

    // 사용자 회원정보 탈퇴 로직 CRUD의 Delete
    @Transactional
    public void deleteUser(Integer userNum) { // 사용자 고유 번호로 조회 후 삭제
        log.info("[UserService] 회원탈퇴 시작: userNum = {}", userNum);

        User user = userRepository.findByUserNum(userNum)
                .orElseThrow(() -> {
                    log.error("[UserService] 사용자를 찾을 수 없습니다: userNum = {}", userNum);
                    return new IllegalArgumentException("해당 사용자가 존재하지 않습니다.");
                });

        log.info("[UserService] 삭제할 사용자 정보: userNum = {}, email = {}, localId = {}",
                user.getUserNum(), user.getEmail(), user.getLocalId());

        try {
            // 사용자 삭제
            userRepository.delete(user);
            // 즉시 DB에 반영
            entityManager.flush();

            // 삭제 확인
            boolean exists = userRepository.findByUserNum(userNum).isPresent();
            if (exists) {
                log.error("[UserService] 삭제 후에도 사용자가 여전히 존재합니다: userNum = {}", userNum);
                throw new IllegalStateException("사용자 삭제에 실패했습니다.");
            }

            log.info("[UserService] 회원탈퇴 완료: userNum = {}", userNum);
        } catch (DataIntegrityViolationException e) {
            log.error("[UserService] 데이터 무결성 제약조건 위반: {}", e.getMessage());
            throw new IllegalStateException("외래키 제약조건으로 인해 삭제할 수 없습니다.", e);
        } catch (Exception e) {
            log.error("[UserService] 회원탈퇴 중 예외 발생: {}", e.getMessage(), e);
            throw new IllegalStateException("회원탈퇴 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
}
