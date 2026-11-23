package com.example.nadeuri.weather.domain;

import java.time.LocalDateTime;

/**
 * 기상청 초단기실황(getUltraSrtNcst) 한 번의 관측값.
 * 매시 정시에 관측한 값이 조금 뒤에 나온다. 하늘상태(SKY)와 강수확률은 실황에 없다.
 *
 * @param observedAt  관측(발표) 시각 (정시, KST)
 * @param temperature 기온 T1H (℃)
 * @param humidity    습도 REH (%)
 * @param pty         강수형태 PTY (0 없음, 1 비, 2 비/눈, 3 눈, 5 빗방울, 6 빗방울눈날림, 7 눈날림)
 * @param rain1h      1시간 강수량 RN1 (mm)
 */
public record Nowcast(
        LocalDateTime observedAt,
        double temperature,
        int humidity,
        int pty,
        double rain1h
) {
}
