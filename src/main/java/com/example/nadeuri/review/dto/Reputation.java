package com.example.nadeuri.review.dto;

/**
 * 한 사람의 평판.
 * - meetAgain: 지금까지 받은 "다음에 또 만나요" 수
 * - hostRating / hostReviews: 이 사람이 모임장인 모임에 다른 참여자들이 준 별점 평균과 개수 (본인 후기는 빼고)
 */
public record Reputation(long meetAgain, double hostRating, long hostReviews) {

    public static final Reputation NONE = new Reputation(0, 0, 0);

    public boolean isEmpty() {
        return meetAgain == 0 && hostReviews == 0;
    }

    /** 4.25 → "4.3" */
    public String hostRatingLabel() {
        return String.format("%.1f", hostRating);
    }

    public static String stars(int rating) {
        int r = Math.max(0, Math.min(5, rating));
        return "★".repeat(r) + "☆".repeat(5 - r);
    }
}
