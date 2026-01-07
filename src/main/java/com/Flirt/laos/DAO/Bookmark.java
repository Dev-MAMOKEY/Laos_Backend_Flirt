package com.Flirt.laos.DAO;

import jakarta.persistence.*;
import lombok.*;
import java.util.Date;

@Entity
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Table(name = "bookmark")
public class Bookmark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bookmark_id") // PK,북마크 아이디
    private Long bookmarkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // FK (USER)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ment_id") // FK (MENT)
    private Ment ment;

    @Column(name = "created_at") // 지정일자
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = new Date();
    }
}