package com.example.nadeuri.global.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 서비스 기준 시간대는 한국 시간(KST)으로 고정한다.
 * 서버가 UTC 등 다른 시간대에서 돌아가도 모임 시각, 날씨 예보 시각이 어긋나지 않도록
 * LocalDateTime.now() 대신 반드시 Times.now()를 사용한다.
 */
public final class Times {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private Times() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }
}
