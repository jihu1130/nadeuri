package com.example.nadeuri.weather.service;

import com.example.nadeuri.weather.client.KmaForecastClient;
import com.example.nadeuri.weather.client.KmaNowcastClient;
import com.example.nadeuri.weather.domain.CurrentWeather;
import com.example.nadeuri.weather.domain.Nowcast;
import com.example.nadeuri.weather.domain.WeatherInfo;
import com.example.nadeuri.weather.dto.WeatherResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 홈 화면 "지금 날씨": 초단기실황 + 단기예보 합치기 */
class CurrentWeatherTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 23, 19, 0);
    private static final WeatherInfo CLEAR_FORECAST = new WeatherInfo(T, 8.0, 20, 1, 0);
    private static final Nowcast DRY = new Nowcast(T, 9.1, 87, 0, 0);

    @Test
    void 기온_습도는_관측값_하늘상태_강수확률은_예보값() {
        WeatherResponse r = WeatherResponse.from(new CurrentWeather(DRY, CLEAR_FORECAST));
        assertThat(r.available()).isTrue();
        assertThat(r.observed()).isTrue();
        assertThat(r.observedAt()).isEqualTo("19:00");
        assertThat(r.temperature()).isEqualTo(9.1);          // 예보 8.0이 아니라 관측 9.1
        assertThat(r.humidity()).isEqualTo(87);
        assertThat(r.precipitationProbability()).isEqualTo(20);
        assertThat(r.description()).isEqualTo("맑음");
        assertThat(r.emoji()).isEqualTo("☀️");
        assertThat(r.rain1h()).isZero();
    }

    @Test
    void 지금_비가_오면_예보가_맑아도_관측한_비를_보여준다() {
        CurrentWeather w = new CurrentWeather(new Nowcast(T, 5.0, 95, 5, 0.5), CLEAR_FORECAST);
        assertThat(w.description()).isEqualTo("빗방울");
        assertThat(w.emoji()).isEqualTo("🌦️");
    }

    @Test
    void 관측이_없으면_예보만_쓴다() {
        WeatherResponse r = WeatherResponse.from(new CurrentWeather(null, CLEAR_FORECAST));
        assertThat(r.available()).isTrue();
        assertThat(r.observed()).isFalse();
        assertThat(r.observedAt()).isNull();
        assertThat(r.temperature()).isEqualTo(8.0);
        assertThat(r.humidity()).isNull();
        assertThat(r.rain1h()).isNull();
    }

    @Test
    void 예보가_없으면_관측만으로_보여준다() {
        WeatherResponse r = WeatherResponse.from(new CurrentWeather(DRY, WeatherInfo.unavailable(T)));
        assertThat(r.available()).isTrue();
        assertThat(r.description()).isEqualTo("비 없음");
        assertThat(r.precipitationProbability()).isNull();
    }

    @Test
    void 둘_다_없으면_표시할_수_없다() {
        assertThat(new CurrentWeather(null, WeatherInfo.unavailable(T)).isAvailable()).isFalse();
    }

    @Test
    void 최근_정시_관측이_아직_없으면_한_시간_전_관측을_쓴다() {
        KmaForecastClient forecastClient = mock(KmaForecastClient.class);
        KmaNowcastClient nowcastClient = mock(KmaNowcastClient.class);
        when(forecastClient.isConfigured()).thenReturn(true);
        when(forecastClient.fetch(anyInt(), anyInt(), any())).thenReturn(List.of());
        when(nowcastClient.isConfigured()).thenReturn(true);
        when(nowcastClient.fetch(eq(60), eq(127), any())).thenReturn(null, DRY);

        CurrentWeather w = new WeatherService(forecastClient, nowcastClient).current(37.5714, 126.9658);

        assertThat(w.nowcast()).isEqualTo(DRY);
        verify(nowcastClient, times(2)).fetch(eq(60), eq(127), any());
    }

    @Test
    void 국외_좌표는_실황을_부르지_않는다() {
        KmaForecastClient forecastClient = mock(KmaForecastClient.class);
        KmaNowcastClient nowcastClient = mock(KmaNowcastClient.class);
        when(nowcastClient.isConfigured()).thenReturn(true);

        CurrentWeather w = new WeatherService(forecastClient, nowcastClient).current(48.85, 2.35);

        assertThat(w.isAvailable()).isFalse();
        verify(nowcastClient, never()).fetch(anyInt(), anyInt(), any());
    }
}
