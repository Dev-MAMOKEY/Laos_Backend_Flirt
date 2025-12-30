package com.MT.laos.DTO;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MbtiTestDTO {

    private List<MbtiAnswerDTO> answers; // 사용자가 선택한 답변 리스트
}
