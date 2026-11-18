package com.example.nadeuri.gathering.domain;

import com.example.nadeuri.global.util.Times;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 모임 참여 기록. (gathering_id, user_id) 유니크 제약으로
 * 같은 회원이 같은 모임에 두 번 참여하는 것을 DB 수준에서 막는다.
 * 주최자도 모임을 만들 때 host=true 로 한 줄 들어간다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_participant_gathering_user", columnNames = {"gathering_id", "user_id"}))
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gathering_id", nullable = false)
    private Long gatheringId;

    /** users.id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    private boolean host;

    private LocalDateTime joinedAt;

    public Participant(Long gatheringId, Long userId, boolean host) {
        this.gatheringId = gatheringId;
        this.userId = userId;
        this.host = host;
        this.joinedAt = Times.now();
    }
}
