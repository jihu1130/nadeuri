package com.example.nadeuri;

import com.example.nadeuri.global.util.Times;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

import java.util.TimeZone;

@EnableCaching
@SpringBootApplication
public class NadeuriApplication {
    public static void main(String[] args) {
        // 라이브러리 내부에서 쓰는 기본 시간대(@Future 검증, Jackson 등)도 KST로 맞춘다
        TimeZone.setDefault(TimeZone.getTimeZone(Times.KST));
        SpringApplication.run(NadeuriApplication.class, args);
    }
}
