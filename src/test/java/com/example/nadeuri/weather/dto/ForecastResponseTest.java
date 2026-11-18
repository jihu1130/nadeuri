package com.example.nadeuri.weather.dto;

import com.example.nadeuri.weather.domain.WeatherInfo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastResponseTest {

    private static final LocalDate D1 = LocalDate.of(2026, 11, 19);

    /** sky: 1 맑음, 3 구름많음, 4 흐림 / pty: 0 없음, 1 비 */
    private static WeatherInfo w(LocalDate d, int hour, double temp, int pop, int sky, int pty) {
        return new WeatherInfo(d.atTime(hour, 0), temp, pop, sky, pty);
    }

    @Test
    void 날짜별로_묶고_최저_최고_기온과_최대_강수확률을_계산한다() {
        List<WeatherInfo> list = new ArrayList<>();
        for (int h = 0; h < 24; h++) list.add(w(D1, h, 2 + h * 0.5, h == 15 ? 40 : 10, 1, 0));
        list.add(w(D1.plusDays(1), 0, 3, 20, 3, 0));
        list.add(w(D1.plusDays(1), 3, 1, 20, 3, 0));

        ForecastResponse r = ForecastResponse.from(list);

        assertThat(r.available()).isTrue();
        assertThat(r.days()).hasSize(2);
        ForecastResponse.Day first = r.days().get(0);
        assertThat(first.date()).isEqualTo(D1);
        assertThat(first.minTemperature()).isEqualTo(2.0);
        assertThat(first.maxTemperature()).isEqualTo(13.5);
        assertThat(first.maxPrecipitationProbability()).isEqualTo(40);
        assertThat(first.partial()).isFalse();
        assertThat(first.hours()).hasSize(24);
        // 다음 날은 3시간 간격 2개뿐 → 일부만 예보
        assertThat(r.days().get(1).partial()).isTrue();
        assertThat(r.forecastUntil()).isEqualTo(LocalDateTime.of(2026, 11, 20, 3, 0));
    }

    @Test
    void 낮에_비_예보가_있으면_대표_날씨는_비() {
        List<WeatherInfo> list = new ArrayList<>();
        for (int h = 0; h < 24; h++) list.add(w(D1, h, 5, 10, 1, 0));
        list.set(14, w(D1, 14, 5, 70, 4, 1));

        ForecastResponse.Day day = ForecastResponse.from(list).days().get(0);
        assertThat(day.description()).isEqualTo("비");
        assertThat(day.hours().get(14).rainy()).isTrue();
    }

    @Test
    void 비가_없으면_낮에_가장_자주_나온_하늘상태를_쓴다() {
        List<WeatherInfo> list = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            int sky = (h >= 6 && h <= 21) ? (h < 16 ? 3 : 1) : 4; // 낮 10시간 구름많음, 6시간 맑음, 밤은 흐림
            list.add(w(D1, h, 5, 10, sky, 0));
        }
        assertThat(ForecastResponse.from(list).days().get(0).description()).isEqualTo("구름많음");
    }

    @Test
    void 예보가_없으면_사용할_수_없음으로_표시한다() {
        ForecastResponse r = ForecastResponse.from(List.of(WeatherInfo.unavailable(D1.atStartOfDay())));
        assertThat(r.available()).isFalse();
        assertThat(r.days()).isEmpty();
        assertThat(r.forecastUntil()).isNull();
    }
}
