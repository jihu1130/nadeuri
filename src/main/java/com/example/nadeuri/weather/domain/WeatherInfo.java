package com.example.nadeuri.weather.domain;

import java.time.LocalDateTime;

/**
 * 기상청 단기예보 한 시각의 값.
 * sky: 하늘상태 (1 맑음, 3 구름많음, 4 흐림)
 * pty: 강수형태 (0 없음, 1 비, 2 비/눈, 3 눈, 4 소나기)
 */
public record WeatherInfo(
        LocalDateTime time,
        double temperature,
        int precipitationProbability,
        int sky,
        int pty
) {
    public static WeatherInfo unavailable(LocalDateTime time) {
        return new WeatherInfo(time, Double.NaN, -1, -1, -1);
    }

    public boolean isAvailable() {
        return sky > 0 && !Double.isNaN(temperature);
    }

    /** 강수확률 60% 이상이거나 강수형태가 있으면 우천 경고 */
    public boolean isRainy() {
        return precipitationProbability >= 60 || pty > 0;
    }

    public String description() {
        if (!isAvailable()) return "예보 없음";
        switch (pty) {
            case 1: return "비";
            case 2: return "비/눈";
            case 3: return "눈";
            case 4: return "소나기";
            default: break;
        }
        return switch (sky) {
            case 1 -> "맑음";
            case 3 -> "구름많음";
            case 4 -> "흐림";
            default -> "알 수 없음";
        };
    }

    public String emoji() {
        if (!isAvailable()) return "❔";
        switch (pty) {
            case 1, 4: return "🌧️";
            case 2: return "🌨️";
            case 3: return "❄️";
            default: break;
        }
        return switch (sky) {
            case 1 -> "☀️";
            case 3 -> "⛅";
            case 4 -> "☁️";
            default -> "❔";
        };
    }
}
