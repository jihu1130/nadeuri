package com.example.nadeuri.weather.dto;

import com.example.nadeuri.weather.domain.CurrentWeather;

import java.time.format.DateTimeFormatter;

/**
 * /api/weather 응답 (홈 화면 "지금 날씨").
 *
 * @param observed                 true면 기온·습도가 기상청 실제 관측(초단기실황) 값, false면 예보 값
 * @param observedAt               관측 시각 "HH:mm" (관측이 아니면 null)
 * @param humidity                 습도 %, 모르면 null
 * @param precipitationProbability 강수확률 %, 모르면 null
 * @param rain1h                   최근 1시간 강수량 mm (관측이 아니면 null)
 */
public record WeatherResponse(
        boolean available,
        double temperature,
        Integer precipitationProbability,
        Integer humidity,
        Double rain1h,
        String description,
        String emoji,
        boolean observed,
        String observedAt
) {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public static WeatherResponse from(CurrentWeather w) {
        boolean observed = w.isObserved();
        return new WeatherResponse(
                w.isAvailable(),
                w.isAvailable() ? w.temperature() : 0,
                w.precipitationProbability() >= 0 ? w.precipitationProbability() : null,
                w.humidity() >= 0 ? w.humidity() : null,
                observed ? w.nowcast().rain1h() : null,
                w.description(),
                w.emoji(),
                observed,
                observed ? w.nowcast().observedAt().format(HHMM) : null);
    }
}
