package com.example.nadeuri.review.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 모임 상세의 "모임 후기" 칸에 필요한 값 (끝난 모임에서만 보여 준다).
 * @param participant   지금 보는 사람이 이 모임 참여자인지
 * @param canWrite      후기·"또 만나요"를 남길 수 있는지 (참여자 + 끝난 뒤 14일 안)
 * @param writeUntil    남길 수 있는 마지막 시각
 * @param mine          내가 쓴 후기 (없으면 null)
 * @param meetAgainSent 이 모임에서 내가 "또 만나요"를 보낸 사람들 users.id
 * @param reputations   참여자 users.id → 평판
 */
public record ReviewSection(
        boolean participant,
        boolean canWrite,
        LocalDateTime writeUntil,
        List<ReviewView> reviews,
        double average,
        ReviewView mine,
        Set<Long> meetAgainSent,
        Map<Long, Reputation> reputations
) {
    public String averageLabel() {
        return String.format("%.1f", average);
    }

    public String averageStars() {
        return Reputation.stars((int) Math.round(average));
    }

    public Reputation reputationOf(Long userId) {
        return reputations.getOrDefault(userId, Reputation.NONE);
    }

    public boolean sentTo(Long userId) {
        return meetAgainSent.contains(userId);
    }
}
