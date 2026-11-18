package com.example.nadeuri.gathering.dto;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public record GatheringCreateRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 1000) String description,
        @NotNull ActivityTag tag,
        @NotBlank String placeName,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @Future @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
        @NotNull @Min(2) @Max(50) Integer maxPeople
) {
    public Gathering toEntity() {
        return new Gathering(title, description, tag, placeName, latitude, longitude, startAt, maxPeople);
    }
}
