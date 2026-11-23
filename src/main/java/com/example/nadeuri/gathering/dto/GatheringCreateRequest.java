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
        @NotBlank @Size(max = 100) String title,
        @Size(max = 1000) String description,
        @NotNull ActivityTag tag,
        @NotBlank @Size(max = 255, message = "장소 이름은 255자까지 쓸 수 있어요.") String placeName,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @Future @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
        @NotNull(message = "진행 시간을 골라 주세요.")
        @Min(value = MIN_DURATION, message = "진행 시간은 30분 이상이어야 해요.")
        @Max(value = MAX_DURATION, message = "진행 시간은 24시간까지 정할 수 있어요.")
        Integer durationMinutes,
        @NotNull @Min(2) @Max(50) Integer maxPeople
) {
    public static final int MIN_DURATION = 30;
    public static final int MAX_DURATION = 24 * 60;

    public LocalDateTime endAt() {
        return startAt.plusMinutes(durationMinutes);
    }

    public Gathering toEntity() {
        return new Gathering(title, description, tag, placeName, latitude, longitude, startAt, endAt(), maxPeople);
    }
}
