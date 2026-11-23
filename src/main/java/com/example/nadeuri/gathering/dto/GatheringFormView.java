package com.example.nadeuri.gathering.dto;

import com.example.nadeuri.gathering.domain.Gathering;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 모임 만들기·수정 화면에 미리 채워 넣을 값.
 * - 새 모임: 빈 값 (시작 시간은 화면이 기본값을 정함, 진행 시간 2시간)
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
        Integer durationMinutes,
        Integer maxPeople
) {
    private static final DateTimeFormatter START = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final int DEFAULT_DURATION = 120;
    /** 진행 시간 선택지 (분) */
    private static final List<Integer> DURATIONS = List.of(30, 60, 90, 120, 180, 240, 360, 480, 720);

    /** 진행 시간 선택지 한 칸 */
    public record DurationOption(int minutes, String label) {}

    public static GatheringFormView empty() {
        return new GatheringFormView(null, null, null, null, null, null, null, DEFAULT_DURATION, 6);
    }

    public static GatheringFormView from(Gathering g) {
        return new GatheringFormView(g.getTitle(), g.getTag().name(), g.getDescription(), g.getPlaceName(),
                g.getLatitude(), g.getLongitude(), g.getStartAt().format(START),
                (int) g.durationMinutes(), g.getMaxPeople());
    }

    public static GatheringFormView from(GatheringCreateRequest r) {
        return new GatheringFormView(r.title(), r.tag() == null ? null : r.tag().name(), r.description(),
                r.placeName(), r.latitude(), r.longitude(),
                r.startAt() == null ? null : r.startAt().format(START),
                r.durationMinutes() == null ? DEFAULT_DURATION : r.durationMinutes(),
                r.maxPeople() == null ? 6 : r.maxPeople());
    }

    /** 선택지 목록. 저장된 값이 목록에 없으면(예: 기존 모임) 그 값도 넣어서 수정할 때 바뀌지 않게 한다 */
    public List<DurationOption> durationOptions() {
        List<Integer> minutes = new ArrayList<>(DURATIONS);
        if (durationMinutes != null && !minutes.contains(durationMinutes)
                && durationMinutes >= GatheringCreateRequest.MIN_DURATION
                && durationMinutes <= GatheringCreateRequest.MAX_DURATION) {
            minutes.add(durationMinutes);
            minutes.sort(null);
        }
        return minutes.stream().map(m -> new DurationOption(m, label(m))).toList();
    }

    /** 90 → "1시간 30분" */
    public static String label(long minutes) {
        long h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + "분";
        return m == 0 ? h + "시간" : h + "시간 " + m + "분";
    }
}
