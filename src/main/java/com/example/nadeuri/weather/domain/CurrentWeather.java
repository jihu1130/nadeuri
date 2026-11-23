package com.example.nadeuri.weather.domain;

/**
 * 홈 화면 "지금 날씨".
 * 기온·습도·강수형태·1시간 강수량은 초단기실황(실제 관측), 하늘상태·강수확률은 단기예보에서 가져와 합친다.
 * 실황을 못 받으면 예전처럼 예보 값만 쓴다.
 *
 * @param nowcast  초단기실황 (없으면 null)
 * @param forecast 지금 시각 단기예보 (없으면 unavailable)
 */
public record CurrentWeather(Nowcast nowcast, WeatherInfo forecast) {

    public boolean isObserved() {
        return nowcast != null;
    }

    public boolean isAvailable() {
        return isObserved() || forecast.isAvailable();
    }

    public double temperature() {
        return isObserved() ? nowcast.temperature() : forecast.temperature();
    }

    /** 습도 (실황에만 있음, 없으면 -1) */
    public int humidity() {
        return isObserved() ? nowcast.humidity() : -1;
    }

    /** 강수확률 (예보에만 있음, 없으면 -1) */
    public int precipitationProbability() {
        return forecast.isAvailable() ? forecast.precipitationProbability() : -1;
    }

    /** 지금 내리는 비·눈은 실황 기준, 없으면 예보 기준 */
    private int pty() {
        if (isObserved()) return nowcast.pty();
        return forecast.isAvailable() ? forecast.pty() : 0;
    }

    private int sky() {
        return forecast.isAvailable() ? forecast.sky() : -1;
    }

    public String description() {
        if (!isAvailable()) return "날씨 정보 없음";
        String rain = WeatherInfo.ptyDescription(pty());
        if (rain != null) return rain;
        return sky() > 0 ? WeatherInfo.skyDescription(sky()) : "비 없음"; // 실황만 있고 하늘상태를 모를 때
    }

    public String emoji() {
        if (!isAvailable()) return "❔";
        String rain = WeatherInfo.ptyEmoji(pty());
        if (rain != null) return rain;
        return sky() > 0 ? WeatherInfo.skyEmoji(sky()) : "🌡️";
    }
}
