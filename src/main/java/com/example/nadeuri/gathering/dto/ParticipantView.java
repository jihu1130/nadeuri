package com.example.nadeuri.gathering.dto;

import java.time.LocalDateTime;

/** 모임 상세의 참여자 목록 한 줄 (참여 기록 + 회원 닉네임·사진) */
public record ParticipantView(
        Long userId,
        String nickname,
        String profileImageUrl,
        boolean host,
        LocalDateTime joinedAt
) {
    /** 사진이 없을 때 동그라미 안에 넣을 첫 글자 */
    public String initial() {
        return nickname == null || nickname.isBlank() ? "?" : nickname.substring(0, 1);
    }
}
