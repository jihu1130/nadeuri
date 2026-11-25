package com.example.nadeuri.review.dto;

import java.time.LocalDateTime;

/** 모임 상세의 후기 한 줄 (후기 + 쓴 사람 닉네임·사진) */
public record ReviewView(
        Long userId,
        String nickname,
        String profileImageUrl,
        int rating,
        String content,
        LocalDateTime updatedAt
) {
    public String initial() {
        return nickname == null || nickname.isBlank() ? "?" : nickname.substring(0, 1);
    }

    /** ★★★★☆ */
    public String stars() {
        return Reputation.stars(rating);
    }
}
