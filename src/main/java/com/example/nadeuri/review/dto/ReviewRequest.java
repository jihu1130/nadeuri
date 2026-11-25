package com.example.nadeuri.review.dto;

import com.example.nadeuri.review.domain.GatheringReview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 후기 쓰기·고치기 */
public record ReviewRequest(
        @NotNull(message = "별점을 골라 주세요.")
        @Min(value = 1, message = "별점은 1~5개로 골라 주세요.")
        @Max(value = 5, message = "별점은 1~5개로 골라 주세요.") Integer rating,
        @Size(max = GatheringReview.CONTENT_MAX, message = "후기는 200자까지 쓸 수 있어요.") String content
) {
    /** 앞뒤 공백을 지우고, 비었으면 null */
    public String cleanContent() {
        if (content == null) return null;
        String s = content.strip();
        return s.isEmpty() ? null : s;
    }
}
