package com.example.nadeuri.global.init;

import com.example.nadeuri.gathering.domain.ActivityTag;
import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.repository.GatheringRepository;
import com.example.nadeuri.global.util.Times;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** DB가 비어 있을 때만 샘플 모임을 넣는다 (서울 여의도~마포 일대) */
/**@Component
@RequiredArgsConstructor
public class SampleDataLoader implements CommandLineRunner {

    private final GatheringRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) return;

        LocalDateTime base = Times.now().plusHours(3).withMinute(0).withSecond(0).withNano(0);
        repository.saveAll(List.of(
                new Gathering("한강 노을 피크닉", "돗자리 있어요. 간식 각자 조금씩!", ActivityTag.PICNIC,
                        "여의도 한강공원", 37.5285, 126.9327, base, base.plusHours(2), 6),
                new Gathering("저녁 5km 러닝", "초보 환영, 6분 페이스", ActivityTag.RUNNING,
                        "여의나루역 2번 출구", 37.5271, 126.9326, base.plusHours(2), base.plusHours(2).plusHours(2), 8),
                new Gathering("연남동 골목 사진 산책", "필름카메라도 환영", ActivityTag.PHOTO,
                        "경의선숲길 연남동", 37.5603, 126.9235, base.plusDays(1), base.plusDays(1).plusHours(2), 5),
                new Gathering("주말 강아지 산책 모임", "소형견 위주", ActivityTag.PET,
                        "망원한강공원", 37.5551, 126.8953, base.plusDays(2), base.plusDays(2).plusHours(2), 10),
                new Gathering("비 오면 보드게임", "실내라 날씨 무관!", ActivityTag.BOARD_GAME,
                        "홍대입구역 근처 보드게임카페", 37.5572, 126.9245, base.plusDays(1).plusHours(1), base.plusDays(1).plusHours(1).plusHours(2), 6)
        ));
    }

} */

