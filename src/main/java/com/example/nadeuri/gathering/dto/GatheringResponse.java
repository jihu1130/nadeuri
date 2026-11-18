package com.example.nadeuri.gathering.dto;

import com.example.nadeuri.gathering.domain.Gathering;

import java.time.LocalDateTime;

public record GatheringResponse(
        Long id, String title, String tag, String tagLabel, String emoji,
        String placeName, double latitude, double longitude,
        LocalDateTime startAt, int currentPeople, int maxPeople
) {
    public static GatheringResponse from(Gathering g) {
        return new GatheringResponse(g.getId(), g.getTitle(), g.getTag().name(),
                g.getTag().getLabel(), g.getTag().getEmoji(), g.getPlaceName(),
                g.getLatitude(), g.getLongitude(), g.getStartAt(),
                g.getCurrentPeople(), g.getMaxPeople());
    }
}
