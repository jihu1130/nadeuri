package com.example.nadeuri.gathering.domain;

import com.example.nadeuri.global.util.Times;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_gathering_lat_lng", columnList = "latitude, longitude"))
public class Gathering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    private int maxPeople;

    /** 참여 인원은 GatheringRepository.incrementIfAvailable()로만 원자적으로 증가시킨다 */
    private int currentPeople;

    private LocalDateTime createdAt;

    public Gathering(String title, String description, ActivityTag tag, String placeName,
                     double latitude, double longitude, LocalDateTime startAt, int maxPeople) {
        this.title = title;
        this.description = description;
        this.tag = tag;
        this.placeName = placeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startAt = startAt;
        this.maxPeople = maxPeople;
        this.currentPeople = 1; // 주최자
        this.createdAt = Times.now();
    }

    public boolean isFull() {
        return currentPeople >= maxPeople;
    }
}
