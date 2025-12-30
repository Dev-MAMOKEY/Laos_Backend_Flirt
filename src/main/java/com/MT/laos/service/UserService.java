package com.MT.laos.service;

import com.MT.laos.DAO.User;
import com.MT.laos.DAO.Provider;
import com.MT.laos.DTO.RegisterDTO;
import com.MT.laos.DTO.UserUpdateDTO;
import com.MT.laos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

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
        User user = userRepository.findByUserNum(userNum)
                .orElseThrow(() -> new IllegalArgumentException("해당 사용자가 존재하지 않습니다."));
        userRepository.delete(user);
    }
}
