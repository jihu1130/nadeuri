package com.example.nadeuri.gathering.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ActivityTag {
    WALK("산책", "🚶", true),
    RUNNING("러닝", "🏃", true),
    PICNIC("피크닉", "🧺", true),
    PHOTO("사진", "📷", true),
    PET("반려동물", "🐕", true),
    CAFE("카페", "☕", false),
    BOARD_GAME("보드게임", "🎲", false),
    FESTIVAL("행사/축제", "🎪", true),
    /** 위 종류에 없는 모임. 실내·야외를 알 수 없어 비 예보 경고는 띄우지 않는다 */
    OTHER("기타", "📌", false);

    private final String label;
    private final String emoji;
    /** 야외 활동이면 비 예보 시 경고를 띄운다 */
    private final boolean outdoor;
}
