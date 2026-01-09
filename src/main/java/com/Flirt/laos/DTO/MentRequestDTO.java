package com.Flirt.laos.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
public class MentRequestDTO {
    private String contentKo; // 한국어 원문
    private String contentLo; // 라오어 번역문
    private String tag; // 태그
}