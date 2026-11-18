package com.example.nadeuri.gathering.dto;

import com.example.nadeuri.gathering.domain.Gathering;

import java.time.format.DateTimeFormatter;

/**
 * 모임 만들기·수정 화면에 미리 채워 넣을 값.
 * - 새 모임: 빈 값 (시작 시간은 화면이 기본값을 정함)
 * - 수정: 저장된 모임 값
 * - 입력 오류로 다시 보여 줄 때: 사용자가 보낸 값 (입력한 내용이 사라지지 않게)
 */
public record GatheringFormView(
        String title,
        String tag,
        String description,
        String placeName,
        Double latitude,
        Double longitude,
        String startAt,      // yyyy-MM-ddTHH:mm (날씨 달력의 처음 선택값)
        Integer maxPeople
) {
    private static final DateTimeFormatter START = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public static GatheringFormView empty() {
        return new GatheringFormView(null, null, null, null, null, null, null, 6);
    }

    public static GatheringFormView from(Gathering g) {
        return new GatheringFormView(g.getTitle(), g.getTag().name(), g.getDescription(), g.getPlaceName(),
                g.getLatitude(), g.getLongitude(), g.getStartAt().format(START), g.getMaxPeople());
    }

    public static GatheringFormView from(GatheringCreateRequest r) {
        return new GatheringFormView(r.title(), r.tag() == null ? null : r.tag().name(), r.description(),
                r.placeName(), r.latitude(), r.longitude(),
                r.startAt() == null ? null : r.startAt().format(START),
                r.maxPeople() == null ? 6 : r.maxPeople());
    }
}
