package com.MT.laos.service;

import com.MT.laos.DAO.MBTI;
import com.MT.laos.DAO.User;
import com.MT.laos.DTO.MbtiAnswerDTO;
import com.MT.laos.DTO.MbtiTestDTO;
import com.MT.laos.repository.MbtiRepository;
import com.MT.laos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MbtiService {

    private final MbtiRepository mbtiRepository;
    private final UserRepository userRepository;

    /**
     * 사용자가 제출한 MBTI 테스트 결과를 기반으로
     * - 4개의 축(E/I, S/N, T/F, J/P)을 계산하여 최종 MBTI 문자열을 만들고
     * - 해당 MBTI 엔티티를 조회한 뒤
     * - User 엔티티에 MBTI를 설정하고 저장한다.
     *
     * @param userNum     MBTI 결과를 저장할 대상 사용자 고유 번호
     * @param mbtiTestDTO 사용자가 선택한 답변 정보(4문항, 각 0 또는 1)
     * @return 계산된 MBTI 문자열 (ex. "INFP", "ESTJ")
     */
    @Transactional
    public String resultMbti(Integer userNum, MbtiTestDTO mbtiTestDTO) {

        // 방어 로직: answers 리스트가 비어 있거나 null 인 경우 예외 처리
        if (mbtiTestDTO == null || mbtiTestDTO.getAnswers() == null || mbtiTestDTO.getAnswers().isEmpty()) {
            throw new IllegalArgumentException("MBTI 답변 리스트가 비어 있습니다.");
        }

        int e = 0, i = 0, s = 0, n = 0, t = 0, f = 0, j = 0, p = 0;

        for (MbtiAnswerDTO answer : mbtiTestDTO.getAnswers()) {

            String type = answer.getQuestionType();

            switch (type) {
                case "EI": if (answer.getAnswer() == 0) e++; else i++; break;
                case "SN": if (answer.getAnswer() == 0) s++; else n++; break;
                case "TF": if (answer.getAnswer() == 0) t++; else f++; break;
                case "JP": if (answer.getAnswer() == 0) j++; else p++; break;
            }
        }

        String result =
                (e >= i ? "E" : "I") +
                        (s >= n ? "S" : "N") +
                        (t >= f ? "T" : "F") +
                        (j >= p ? "J" : "P");

        // DB 저장 처리
        MBTI mbti = mbtiRepository.findByMbtiType(result)
                .orElseThrow(() -> new RuntimeException("MBTI not found"));

        User user = userRepository.findByUserNum(userNum)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setMbti(mbti);
        userRepository.save(user);

        // 로깅: 어떤 사용자에게 어떤 MBTI가 계산되었는지 확인용
        log.info("사용자 번호 {} 의 MBTI 테스트 결과 = {}", userNum, result);

        return result;
    }
}
