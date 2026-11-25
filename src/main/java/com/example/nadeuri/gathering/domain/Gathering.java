package com.example.nadeuri.gathering.domain;

import com.example.nadeuri.global.util.Times;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_gathering_lat_lng", columnList = "latitude, longitude"))
public class Gathering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 주소·API·채팅에 쓰는 공개 식별자 (무작위 UUID). 순번 id는 DB 안에서만 쓴다 */
    @Column(name = "public_id", nullable = false, unique = true, length = 36, updatable = false)
    private String publicId = UUID.randomUUID().toString();

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityTag tag;

    @Column(nullable = false)
    private String placeName;

    private double latitude;
    private double longitude;

    /** KST 기준 시작 시각 */
    @Column(nullable = false)
    private LocalDateTime startAt;

    /** KST 기준 끝나는 시각 (시작 + 진행 시간). 항상 startAt보다 뒤 */
    @Column(nullable = false)
    private LocalDateTime endAt;

    private int maxPeople;

    /** 번개 모임 (저장하는 때부터 2시간 안에 시작하는 즉석 모임). 규칙 확인은 GatheringCreateRequest */
    @Column(nullable = false)
    private boolean flash;

    /** 참여 인원은 GatheringRepository.incrementIfAvailable()로만 원자적으로 증가시킨다 */
    private int currentPeople;

    private LocalDateTime createdAt;

    public Gathering(String title, String description, ActivityTag tag, String placeName,
                     double latitude, double longitude, LocalDateTime startAt, LocalDateTime endAt,
                     int maxPeople) {
        this(title, description, tag, placeName, latitude, longitude, startAt, endAt, maxPeople, false);
    }

    public Gathering(String title, String description, ActivityTag tag, String placeName,
                     double latitude, double longitude, LocalDateTime startAt, LocalDateTime endAt,
                     int maxPeople, boolean flash) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("끝나는 시간은 시작 시간보다 뒤여야 해요.");
        }
        this.title = title;
        this.description = description;
        this.tag = tag;
        this.placeName = placeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startAt = startAt;
        this.endAt = endAt;
        this.maxPeople = maxPeople;
        this.flash = flash;
        this.currentPeople = 1; // 주최자
        this.createdAt = Times.now();
    }

    public boolean isFull() {
        return currentPeople >= maxPeople;
    }

    /** 시작 시간이 지났는가 (진행 중 또는 종료) */
    public boolean isStartedAt(LocalDateTime now) {
        return !startAt.isAfter(now);
    }

    /** 끝나는 시간이 지났는가 */
    public boolean isEndedAt(LocalDateTime now) {
        return !endAt.isAfter(now);
    }

    public GatheringStatus statusAt(LocalDateTime now) {
        if (isEndedAt(now)) return GatheringStatus.ENDED;
        if (isStartedAt(now)) return GatheringStatus.ONGOING;
        return isFull() ? GatheringStatus.FULL : GatheringStatus.RECRUITING;
    }

    /** 진행 시간(분) */
    public long durationMinutes() {
        return Duration.between(startAt, endAt).toMinutes();
    }
}
