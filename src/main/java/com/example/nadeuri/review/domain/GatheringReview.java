package com.example.nadeuri.review.domain;

import com.example.nadeuri.global.util.Times;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 끝난 모임 후기: 별점(1~5) + 한 줄 후기. (모임, 쓴 사람)마다 하나 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "gathering_review",
        uniqueConstraints = @UniqueConstraint(name = "uk_review_gathering_user", columnNames = {"gathering_id", "user_id"}),
        indexes = @Index(name = "idx_review_user", columnList = "user_id"))
public class GatheringReview {

    public static final int CONTENT_MAX = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gathering_id", nullable = false)
    private Long gatheringId;

    /** 후기를 쓴 사람 users.id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int rating;

    @Column(length = CONTENT_MAX)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public GatheringReview(Long gatheringId, Long userId, int rating, String content) {
        this.gatheringId = gatheringId;
        this.userId = userId;
        this.createdAt = Times.now();
        change(rating, content);
    }

    public void change(int rating, String content) {
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("별점은 1~5개로 골라 주세요.");
        this.rating = rating;
        this.content = content;
        this.updatedAt = Times.now();
    }
}
