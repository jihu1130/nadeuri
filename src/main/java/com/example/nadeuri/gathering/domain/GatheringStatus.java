package com.example.nadeuri.gathering.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 모임 상태. DB에 저장하지 않고 시작·끝 시간과 인원으로 그때그때 계산한다
 * (시간이 지나면 저절로 바뀌므로 저장해 두면 금방 틀린 값이 된다).
 */
@Getter
@RequiredArgsConstructor
public enum GatheringStatus {
    RECRUITING("모집 중", "recruiting"),
    FULL("모집 마감", "full"),
    ONGOING("진행 중", "ongoing"),
    ENDED("종료", "ended");

    private final String label;
    /** 화면 배지 CSS 클래스 (status-badge.recruiting 등) */
    private final String cssClass;
}
