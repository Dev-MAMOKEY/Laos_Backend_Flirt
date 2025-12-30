package com.MT.laos.service;

import com.MT.laos.DAO.MBTI;
import com.MT.laos.repository.MbtiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 애플리케이션이 시작될 때 MBTI 테이블에
 * 1 ~ 16번, 각 번호에 해당하는 MBTI 타입을 자동으로 넣어주는 초기 데이터 설정 클래스.
 *
 * - 이미 데이터가 있으면 아무 작업도 하지 않음.
 * - 데이터가 비어 있으면 16개의 MBTI 레코드를 생성.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MbtiDataInitializer implements CommandLineRunner {

    private final MbtiRepository mbtiRepository;

    @Override
    public void run(String... args) {
        // 이미 데이터가 있으면 스킵
        if (mbtiRepository.count() > 0) {
            return;
        }

        // 0000 ~ 1111 조합 순서대로 1 ~ 16번에 매핑되는 MBTI 리스트
        List<MBTI> mbtiList = Arrays.asList(
                new MBTI(1,  "ESTJ"), // 0000
                new MBTI(2,  "ESTP"), // 0001
                new MBTI(3,  "ESFJ"), // 0010
                new MBTI(4,  "ESFP"), // 0011
                new MBTI(5,  "ENTJ"), // 0100
                new MBTI(6,  "ENTP"), // 0101
                new MBTI(7,  "ENFJ"), // 0110
                new MBTI(8,  "ENFP"), // 0111
                new MBTI(9,  "ISTJ"), // 1000
                new MBTI(10, "ISTP"), // 1001
                new MBTI(11, "ISFJ"), // 1010
                new MBTI(12, "ISFP"), // 1011
                new MBTI(13, "INTJ"), // 1100
                new MBTI(14, "INTP"), // 1101
                new MBTI(15, "INFJ"), // 1110
                new MBTI(16, "INFP")  // 1111
        );

        mbtiRepository.saveAll(mbtiList);
        log.info("MBTI 초기 데이터 16개를 자동으로 생성했습니다.");
    }

}


