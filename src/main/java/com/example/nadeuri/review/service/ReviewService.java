package com.example.nadeuri.review.service;

import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.dto.ParticipantView;
import com.example.nadeuri.gathering.repository.ParticipantRepository;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.review.domain.GatheringReview;
import com.example.nadeuri.review.domain.MeetAgain;
import com.example.nadeuri.review.dto.Reputation;
import com.example.nadeuri.review.dto.ReviewRequest;
import com.example.nadeuri.review.dto.ReviewSection;
import com.example.nadeuri.review.dto.ReviewView;
import com.example.nadeuri.review.repository.GatheringReviewRepository;
import com.example.nadeuri.review.repository.MeetAgainRepository;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 끝난 모임 후기와 평판.
 * - 후기(별점 1~5 + 한 줄)와 "다음에 또 만나요"는 그 모임 참여자만, 모임이 끝난 뒤 WINDOW_DAYS일 안에 남길 수 있다.
 *   지난 뒤에도 남긴 후기는 계속 보인다.
 * - 평판 = 받은 "또 만나요" 수 + 모임장으로서 받은 별점 평균 (모임장 본인 별점 제외)
 * GatheringService를 쓰지 않고 저장소만 쓴다 (GatheringService가 모임을 지울 때 이 서비스를 부르므로 서로 물리지 않게).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    public static final int WINDOW_DAYS = 14;

    private final GatheringReviewRepository reviews;
    private final MeetAgainRepository meetAgain;
    private final ParticipantRepository participants;
    private final UserRepository users;

    /** 후기를 남길 수 있는 마지막 시각 */
    public static LocalDateTime writeUntil(Gathering g) {
        return g.getEndAt().plusDays(WINDOW_DAYS);
    }

    /** 모임 상세의 후기 칸 (끝난 모임에서만 부른다) */
    public ReviewSection section(Gathering g, Long viewerId, List<ParticipantView> members) {
        LocalDateTime now = Times.now();
        boolean participant = viewerId != null && participants.existsByGatheringIdAndUserId(g.getId(), viewerId);
        boolean canWrite = participant && g.isEndedAt(now) && !now.isAfter(writeUntil(g));
        List<ReviewView> list = reviews.findViews(g.getId());
        double average = list.stream().mapToInt(ReviewView::rating).average().orElse(0);
        ReviewView mine = viewerId == null ? null
                : list.stream().filter(r -> r.userId().equals(viewerId)).findFirst().orElse(null);
        Set<Long> sent = viewerId == null ? Set.of() : new HashSet<>(meetAgain.sentTo(g.getId(), viewerId));
        Map<Long, Reputation> reps = reputations(members.stream().map(ParticipantView::userId).toList());
        return new ReviewSection(participant, canWrite, writeUntil(g), list, average, mine, sent, reps);
    }

    /** 여러 사람의 평판을 한 번에 (참여자 목록용) */
    public Map<Long, Reputation> reputations(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, Long> received = new HashMap<>();
        for (Object[] row : meetAgain.countReceived(userIds)) {
            received.put((Long) row[0], ((Number) row[1]).longValue());
        }
        Map<Long, double[]> host = new HashMap<>();
        for (Object[] row : reviews.hostRatings(userIds)) {
            host.put((Long) row[0], new double[]{((Number) row[1]).doubleValue(), ((Number) row[2]).doubleValue()});
        }
        Map<Long, Reputation> result = new HashMap<>();
        for (Long id : userIds) {
            double[] h = host.getOrDefault(id, new double[]{0, 0});
            Reputation r = new Reputation(received.getOrDefault(id, 0L), h[0], (long) h[1]);
            if (!r.isEmpty()) result.put(id, r);
        }
        return result;
    }

    public Reputation reputation(Long userId) {
        return reputations(List.of(userId)).getOrDefault(userId, Reputation.NONE);
    }

    /** 내 모임 화면: 이 끝난 모임들 가운데 내가 후기를 쓴 것 */
    public Set<Long> reviewedAmong(Long userId, Collection<Long> gatheringIds) {
        if (gatheringIds.isEmpty()) return Set.of();
        return new HashSet<>(reviews.reviewedGatheringIds(userId, gatheringIds));
    }

    /**
     * 후기 쓰기 (이미 썼으면 고친다).
     * 같은 사람이 동시에 두 번 보내도 한 줄만 남도록 회원 행을 잠그고 확인한다.
     * @return 새로 썼으면 true, 고쳤으면 false
     * @throws IllegalStateException 참여자가 아니거나, 아직 안 끝났거나, 기간이 지남
     */
    @Transactional
    public boolean write(Gathering g, Long userId, ReviewRequest req) {
        requireWritable(g, userId);
        users.lockById(userId);
        Optional<GatheringReview> existing = reviews.findByGatheringIdAndUserId(g.getId(), userId);
        if (existing.isPresent()) {
            existing.get().change(req.rating(), req.cleanContent());
            return false;
        }
        reviews.save(new GatheringReview(g.getId(), userId, req.rating(), req.cleanContent()));
        return true;
    }

    /** 내 후기 지우기 (기간과 상관없이 언제든) */
    @Transactional
    public boolean deleteMine(Gathering g, Long userId) {
        return reviews.deleteMine(g.getId(), userId) > 0;
    }

    /**
     * "다음에 또 만나요" 보내기 / 거두기 (누를 때마다 바뀜).
     * @param toPublicId 받을 사람의 공개 UUID (같은 모임 참여자, 나 말고)
     * @return 보낸 상태가 됐으면 true, 거뒀으면 false
     */
    @Transactional
    public boolean toggleMeetAgain(Gathering g, Long fromUserId, String toPublicId) {
        requireWritable(g, fromUserId);
        User to = users.findByPublicId(toPublicId == null ? "" : toPublicId)
                .filter(u -> !u.getId().equals(fromUserId))
                .filter(u -> participants.existsByGatheringIdAndUserId(g.getId(), u.getId()))
                .orElseThrow(() -> new IllegalStateException("이 모임에서 함께한 사람에게만 보낼 수 있어요."));
        users.lockById(fromUserId);
        if (meetAgain.deleteOne(g.getId(), fromUserId, to.getId()) > 0) return false;
        meetAgain.save(new MeetAgain(g.getId(), fromUserId, to.getId()));
        return true;
    }

    private void requireWritable(Gathering g, Long userId) {
        LocalDateTime now = Times.now();
        if (!participants.existsByGatheringIdAndUserId(g.getId(), userId)) {
            throw new IllegalStateException("이 모임에 참여한 사람만 후기를 남길 수 있어요.");
        }
        if (!g.isEndedAt(now)) {
            throw new IllegalStateException("모임이 끝난 뒤에 후기를 남길 수 있어요.");
        }
        if (now.isAfter(writeUntil(g))) {
            throw new IllegalStateException("후기는 모임이 끝나고 " + WINDOW_DAYS + "일 안에만 남길 수 있어요.");
        }
    }

    /** 모임을 지울 때 (GatheringService) */
    @Transactional
    public void removeForGathering(Long gatheringId) {
        reviews.deleteAllByGatheringId(gatheringId);
        meetAgain.deleteAllByGatheringId(gatheringId);
    }

    /** 회원 탈퇴: 그 사람이 쓴 후기, 보내고 받은 "또 만나요" */
    @Transactional
    public void removeAllFor(Long userId) {
        reviews.deleteAllByUserId(userId);
        meetAgain.deleteAllInvolving(userId);
    }
}
