package com.example.nadeuri.review.domain;

import com.example.nadeuri.global.util.Times;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** "다음에 또 만나요": 끝난 모임에서 함께한 사람에게 보내는 좋은 표시. (모임, 보낸 사람, 받은 사람)마다 하나 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "meet_again",
        uniqueConstraints = @UniqueConstraint(name = "uk_meet_again", columnNames = {"gathering_id", "from_user_id", "to_user_id"}),
        indexes = {@Index(name = "idx_meet_again_to", columnList = "to_user_id"),
                   @Index(name = "idx_meet_again_from", columnList = "from_user_id")})
public class MeetAgain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gathering_id", nullable = false)
    private Long gatheringId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public MeetAgain(Long gatheringId, Long fromUserId, Long toUserId) {
        this.gatheringId = gatheringId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.createdAt = Times.now();
    }
}
