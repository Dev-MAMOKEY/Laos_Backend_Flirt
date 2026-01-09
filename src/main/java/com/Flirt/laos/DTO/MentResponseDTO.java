package com.Flirt.laos.DTO;

import lombok.*;
import java.util.Date;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MentResponseDTO {
    private Long mentId;
    private String contentKo;
    private String contentLo;
    private String tag;
    private String authorNickname;
    private Date createdAt;

    // 상태 확인을 위한 필드 추가
    private Long isApproved; // 0:미승인, 1:승인, 2:거절
    private String reason;    // 거절 사유
}