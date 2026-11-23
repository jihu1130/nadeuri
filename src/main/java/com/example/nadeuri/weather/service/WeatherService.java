package com.example.nadeuri.weather.service;

import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.weather.client.KmaForecastClient;
import com.example.nadeuri.weather.client.KmaNowcastClient;
import com.example.nadeuri.weather.domain.CurrentWeather;
import com.example.nadeuri.weather.domain.KmaGrid;
import com.example.nadeuri.weather.domain.Nowcast;
import com.example.nadeuri.weather.domain.WeatherInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/** 위치·시각에 맞는 예보·실황을 골라 주는 서비스. API 호출은 KmaForecastClient·KmaNowcastClient가 담당한다. */
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final KmaForecastClient client;
    private final KmaNowcastClient nowcastClient;

    /** 특정 시각(KST)의 예보. 정확한 정시 값이 없으면 앞뒤 2시간 내 가장 가까운 값 */
    public WeatherInfo forecastAt(double lat, double lng, LocalDateTime time) {
        KmaGrid grid = KmaGrid.from(lat, lng);
        if (!client.isConfigured() || !grid.isInKorea()) {
            return WeatherInfo.unavailable(time);
        }
        LocalDateTime target = time.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime base = KmaForecastClient.latestBaseTime(Times.now());
        return client.fetch(grid.nx(), grid.ny(), base).stream()
                .filter(w -> Math.abs(Duration.between(target, w.time()).toHours()) <= 2)
                .min(Comparator.comparingLong(w -> Math.abs(Duration.between(target, w.time()).toMinutes())))
                .orElse(WeatherInfo.unavailable(time));
    }

    public WeatherInfo now(double lat, double lng) {
        return forecastAt(lat, lng, Times.now());
    }

    /**
     * 홈 화면 "지금 날씨": 초단기실황(관측) + 지금 시각 단기예보(하늘상태·강수확률).
     * 가장 최근 정시 관측이 아직 안 나왔으면 한 시간 전 관측을 쓴다.
     */
    public CurrentWeather current(double lat, double lng) {
        WeatherInfo forecast = now(lat, lng);
        KmaGrid grid = KmaGrid.from(lat, lng);
        if (!nowcastClient.isConfigured() || !grid.isInKorea()) {
            return new CurrentWeather(null, forecast);
        }
        LocalDateTime base = KmaNowcastClient.latestBaseTime(Times.now());
        Nowcast nowcast = nowcastClient.fetch(grid.nx(), grid.ny(), base);
        if (nowcast == null) {
            nowcast = nowcastClient.fetch(grid.nx(), grid.ny(), base.minusHours(1));
        }
        return new CurrentWeather(nowcast, forecast);
    }

    /**
     * 지금 이후로 받을 수 있는 시간별 예보 전체 (이번 정시부터).
     * 기상청 단기예보라서 대략 3~4일 뒤까지만 있다. 키가 없거나 국내가 아니면 빈 목록.
     */
    public List<WeatherInfo> upcoming(double lat, double lng) {
        KmaGrid grid = KmaGrid.from(lat, lng);
        if (!client.isConfigured() || !grid.isInKorea()) {
            return List.of();
        }
        LocalDateTime now = Times.now();
        LocalDateTime from = now.truncatedTo(ChronoUnit.HOURS);
        return client.fetch(grid.nx(), grid.ny(), KmaForecastClient.latestBaseTime(now)).stream()
                .filter(w -> !w.time().isBefore(from))
                .toList();
    }
}
