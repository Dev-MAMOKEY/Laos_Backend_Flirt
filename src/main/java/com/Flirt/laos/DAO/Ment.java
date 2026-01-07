package com.Flirt.laos.DAO;

import jakarta.persistence.*;
import lombok.*;
import java.util.Date;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Table(name = "ment")
public class Ment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ment_id") // PK, 맨트 아이디
    private Long mentId;

    @Column(name = "content_ko", nullable = false, length = 1000) //한국어 원문
    private String contentKo;

    @Column(name = "content_lo", nullable = false, length = 1000) //라오어 번역문
    private String contentLo;

    @Column(name = "tag", length = 100) // 멘트 태그
    private String tag;

    @Column(name = "is_approved", nullable = false)
    private Long isApproved; // 0:미승인, 1:승인, 2:거절

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id") // FK (USER)
    private User author;

    @Column(name = "created_at")
    private Date createdAt;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = new Date();
        if (this.isApproved == null) this.isApproved = 0L; // 기본값 미승인
        if (this.reason == null) this.reason = "none"; // Not Null 제약 조건 대비
    }
}