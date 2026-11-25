package com.example.nadeuri.gathering.dto;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 모임 만들기·수정 요청.
 * 끝나는 시간은 따로 받지 않고 "진행 시간(분)"을 받아 시작 시간에 더한다
 * (끝이 시작보다 앞서는 잘못된 입력이 생기지 않게).
 */
public record GatheringCreateRequest(
        @NotBlank(message = "모임 이름을 적어 주세요.")
        @Size(max = 100, message = "모임 이름은 100자까지 쓸 수 있어요.") String title,
        @Size(max = 1000, message = "소개는 1000자까지 쓸 수 있어요.") String description,
        @NotNull(message = "활동 종류를 골라 주세요.") ActivityTag tag,
        @NotBlank(message = "모임 장소 이름을 적어 주세요.")
        @Size(max = 255, message = "장소 이름은 255자까지 쓸 수 있어요.") String placeName,
        // 위도·경도는 지도에서 장소를 고르면 채워지는 숨은 값이라, 문제가 있으면 "장소를 선택해 주세요"로 안내한다
        @NotNull(message = PLACE_REQUIRED) @DecimalMin(value = "-90", message = PLACE_REQUIRED)
        @DecimalMax(value = "90", message = PLACE_REQUIRED) Double latitude,
        @NotNull(message = PLACE_REQUIRED) @DecimalMin(value = "-180", message = PLACE_REQUIRED)
        @DecimalMax(value = "180", message = PLACE_REQUIRED) Double longitude,
        @NotNull(message = "시작 시간을 골라 주세요.") @Future(message = "시작 시간은 지금보다 뒤여야 해요.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
        @NotNull(message = "진행 시간을 골라 주세요.")
        @Min(value = MIN_DURATION, message = "진행 시간은 30분 이상이어야 해요.")
        @Max(value = MAX_DURATION, message = "진행 시간은 24시간까지 정할 수 있어요.")
        Integer durationMinutes,
        @NotNull(message = "정원을 적어 주세요.") @Min(value = 2, message = "정원은 2명 이상이어야 해요.")
        @Max(value = 50, message = "정원은 50명까지 정할 수 있어요.") Integer maxPeople
) {
    public static final String PLACE_REQUIRED = "모임 장소를 선택해 주세요. 장소를 검색해 고르거나 지도에서 위치를 눌러 주세요.";
    public static final int MIN_DURATION = 30;
    public static final int MAX_DURATION = 24 * 60;

    public LocalDateTime endAt() {
        return startAt.plusMinutes(durationMinutes);
    }

    public Gathering toEntity() {
        return new Gathering(title, description, tag, placeName, latitude, longitude, startAt, endAt(), maxPeople);
    }
}
