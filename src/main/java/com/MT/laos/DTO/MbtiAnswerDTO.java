package com.MT.laos.DTO;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MbtiAnswerDTO {

    private Long questionId; // 질문 번호 ( 1 ~ 4)

    private Integer answer; // 0 or 1

    public String getQuestionType() {
        switch (questionId.intValue()) {
            case 1:
                return "EI"; // 외향(E) 내향(I)
            case 2:
                return "SN"; // 감각(S) 직관(N)
            case 3:
                return "TF"; // 사고(T) 감정(F)
            case 4:
                return "JP"; // 판단(J) 인식(P)
            default:
                throw new IllegalArgumentException("Invalid questionId: " + questionId);
        }
    }
}
