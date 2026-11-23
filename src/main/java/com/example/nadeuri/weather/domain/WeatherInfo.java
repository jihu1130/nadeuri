package com.example.nadeuri.weather.domain;

import java.time.LocalDateTime;

/**
 * 기상청 단기예보 한 시각의 값.
 * sky: 하늘상태 (1 맑음, 3 구름많음, 4 흐림)
 * pty: 강수형태 (0 없음, 1 비, 2 비/눈, 3 눈, 4 소나기 / 초단기실황은 5 빗방울, 6 빗방울눈날림, 7 눈날림도 씀)
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
        String rain = ptyDescription(pty);
        return rain != null ? rain : skyDescription(sky);
    }

    public String emoji() {
        if (!isAvailable()) return "❔";
        String rain = ptyEmoji(pty);
        return rain != null ? rain : skyEmoji(sky);
    }

    /**
     * 강수형태 글자. 비·눈이 없으면 null.
     * 단기예보는 1~4, 초단기실황은 1~3, 5~7을 쓴다.
     */
    public static String ptyDescription(int pty) {
        return switch (pty) {
            case 1 -> "비";
            case 2 -> "비/눈";
            case 3 -> "눈";
            case 4 -> "소나기";
            case 5 -> "빗방울";
            case 6 -> "빗방울·눈날림";
            case 7 -> "눈날림";
            default -> null;
        };
    }

    public static String ptyEmoji(int pty) {
        return switch (pty) {
            case 1, 4 -> "🌧️";
            case 5 -> "🌦️";
            case 2, 6, 7 -> "🌨️";
            case 3 -> "❄️";
            default -> null;
        };
    }

    public static String skyDescription(int sky) {
        return switch (sky) {
            case 1 -> "맑음";
            case 3 -> "구름많음";
            case 4 -> "흐림";
            default -> "알 수 없음";
        };
    }

    public static String skyEmoji(int sky) {
        return switch (sky) {
            case 1 -> "☀️";
            case 3 -> "⛅";
            case 4 -> "☁️";
            default -> "❔";
        };
    }
}
