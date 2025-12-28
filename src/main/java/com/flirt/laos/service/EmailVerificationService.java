package com.flirt.laos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EmailVerificationService {

    // 이메일을 키로, 인증 정보를 값으로 저장하는 Map
    // ConcurrentHashMap을 사용하여 스레드 안전성 보장
    private static final Map<String, VerificationInfo> verificationMap = new ConcurrentHashMap<>();
    
    // 인증 코드 만료 시간 (분 단위)
    private static final int VERIFICATION_EXPIRATION_MINUTES = 5;

    // 인증 정보를 담는 내부 클래스
    private static class VerificationInfo {
        String code;
        LocalDateTime expireTime;

        VerificationInfo(String code, LocalDateTime expireTime) {
            this.code = code;
            this.expireTime = expireTime;
        }
    }

    /**
     * 인증 코드 저장
     * @param email 이메일 주소
     * @param code 인증 코드
     */
    public void saveVerificationCode(String email, String code) {
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(VERIFICATION_EXPIRATION_MINUTES);
        verificationMap.put(email, new VerificationInfo(code, expireTime));
        log.info("[이메일 인증] 이메일: {}, 인증 코드 저장 완료. 만료 시간: {}", email, expireTime);
    }

    /**
     * 인증 코드 검증
     * @param email 이메일 주소
     * @param code 인증 코드
     * @return 검증 성공 여부
     */
    public boolean verifyCode(String email, String code) {
        VerificationInfo info = verificationMap.get(email);
        
        // 저장된 인증 정보가 없는 경우
        if (info == null) {
            log.warn("[이메일 인증] 이메일: {}, 저장된 인증 정보가 없습니다.", email);
            return false;
        }

        // 만료 시간 체크
        if (LocalDateTime.now().isAfter(info.expireTime)) {
            log.warn("[이메일 인증] 이메일: {}, 인증 코드가 만료되었습니다. 만료 시간: {}", email, info.expireTime);
            verificationMap.remove(email); // 만료된 코드 제거
            return false;
        }

        // 인증 코드 일치 여부 확인
        boolean isValid = info.code.equals(code);
        
        if (isValid) {
            log.info("[이메일 인증] 이메일: {}, 인증 코드 검증 성공", email);
            verificationMap.remove(email); // 검증 성공 시 코드 제거 (일회용)
        } else {
            log.warn("[이메일 인증] 이메일: {}, 인증 코드가 일치하지 않습니다.", email);
        }

        return isValid;
    }

    /**
     * 이메일에 대한 인증 코드가 있는지 확인 (만료 여부 포함)
     * @param email 이메일 주소
     * @return 유효한 인증 코드 존재 여부
     */
    public boolean hasValidVerificationCode(String email) {
        VerificationInfo info = verificationMap.get(email);
        if (info == null) {
            return false;
        }
        
        if (LocalDateTime.now().isAfter(info.expireTime)) {
            verificationMap.remove(email);
            return false;
        }
        
        return true;
    }

    /**
     * 만료된 인증 코드 정리 (선택적 - 주기적으로 호출 가능)
     */
    public void cleanExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        verificationMap.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expireTime));
        log.debug("[이메일 인증] 만료된 인증 코드 정리 완료");
    }
}
