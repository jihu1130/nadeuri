package com.example.nadeuri.gathering.dto;

import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.domain.GatheringStatus;

import java.time.LocalDateTime;

/** 지도 API 응답. id는 모임의 공개 UUID (주소에 쓰는 값). status: RECRUITING | FULL | ONGOING | ENDED */
public record GatheringResponse(
        String id, String title, String tag, String tagLabel, String emoji,
        String placeName, double latitude, double longitude,
        LocalDateTime startAt, LocalDateTime endAt, int currentPeople, int maxPeople,
        String status, String statusLabel
) {
    public static GatheringResponse from(Gathering g, LocalDateTime now) {
        GatheringStatus s = g.statusAt(now);
        return new GatheringResponse(g.getPublicId(), g.getTitle(), g.getTag().name(),
                g.getTag().getLabel(), g.getTag().getEmoji(), g.getPlaceName(),
                g.getLatitude(), g.getLongitude(), g.getStartAt(), g.getEndAt(),
                g.getCurrentPeople(), g.getMaxPeople(), s.name(), s.getLabel());
    }
}
