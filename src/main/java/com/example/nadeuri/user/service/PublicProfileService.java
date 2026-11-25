package com.example.nadeuri.user.service;

import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.gathering.repository.ParticipantRepository;
import com.example.nadeuri.gathering.service.GatheringService;
import com.example.nadeuri.global.util.Times;
import com.example.nadeuri.review.service.ReviewService;
import com.example.nadeuri.user.domain.User;
import com.example.nadeuri.user.dto.PublicProfile;
import com.example.nadeuri.user.exception.UserNotFoundException;
import com.example.nadeuri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 회원 프로필 화면: 자기소개·평판(항상 공개)과 활동 기록(공개 범위에 따라).
 * - 연 모임: 끝나지 않은 것 + 끝난 것 (모임은 원래 지도에 공개돼 있다)
 * - 참여한 모임: 끝난 것만. 앞으로 갈 곳(예정된 참여)은 공개 범위와 상관없이 남에게 보여 주지 않는다
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicProfileService {

    private final UserRepository users;
    private final GatheringService gatheringService;
    private final ParticipantRepository participants;
    private final ReviewService reviewService;

    /** @param viewerId 보는 사람 (로그인 안 했으면 null) */
    public PublicProfile view(UUID publicId, Long viewerId) {
        User user = users.findByPublicId(publicId.toString()).orElseThrow(() -> new UserNotFoundException(publicId));
        boolean self = user.getId().equals(viewerId);
        boolean met = !self && viewerId != null && participants.metBefore(user.getId(), viewerId);
        LocalDateTime now = Times.now();

        boolean hostedVisible = user.getHostedVisibility().allows(self, met);
        List<Gathering> hostedUpcoming = List.of(), hostedPast = List.of();
        int hostedCount = 0;
        if (hostedVisible) {
            List<Gathering> hosted = gatheringService.hostedBy(user.getId());
            hostedCount = hosted.size();
            hostedUpcoming = hosted.stream().filter(g -> !g.isEndedAt(now)).limit(PublicProfile.LIST_MAX).toList();
            hostedPast = recentFirst(hosted.stream().filter(g -> g.isEndedAt(now)).toList());
        }

        boolean joinedVisible = user.getJoinedVisibility().allows(self, met);
        List<Gathering> joinedPast = List.of();
        int joinedCount = 0;
        if (joinedVisible) {
            List<Gathering> ended = gatheringService.joinedBy(user.getId()).stream().filter(g -> g.isEndedAt(now)).toList();
            joinedCount = ended.size();
            joinedPast = recentFirst(ended);
        }

        return new PublicProfile(user, self, reviewService.reputation(user.getId()),
                hostedVisible, hostedUpcoming, hostedPast, hostedCount, joinedVisible, joinedPast, joinedCount);
    }

    /** 끝난 모임: 최근 것부터 LIST_MAX개 */
    private static List<Gathering> recentFirst(List<Gathering> list) {
        List<Gathering> copy = new ArrayList<>(list);
        Collections.reverse(copy);
        return copy.stream().limit(PublicProfile.LIST_MAX).toList();
    }
}
