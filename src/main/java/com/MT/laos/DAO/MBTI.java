package com.MT.laos.DAO;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "mbti")
@NoArgsConstructor
public class MBTI {
    @Id
    @Column(name = "mbti_num") // MBTI 번호 1 ~ 16
    private int mbtiNum;

    @Column(name = "mbti_type") // MBTI 유형 ex) INFP, ESTJ
    private String mbtiType;

    // 초기 데이터 생성을 위한 편의 생성자
    public MBTI(int mbtiNum, String mbtiType) {
        this.mbtiNum = mbtiNum;
        this.mbtiType = mbtiType;
    }
}
