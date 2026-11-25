package com.example.nadeuri.user.dto;

import com.example.nadeuri.gathering.domain.Gathering;
import com.example.nadeuri.review.dto.Reputation;
import com.example.nadeuri.user.domain.User;

import java.util.List;

/**
 * 회원 프로필 화면(/users/{publicId})에 보여 줄 값. 공개 범위에 맞지 않는 목록은 비워서 넘긴다.
 * @param self          내 프로필을 내가 보는 중 (모든 항목이 보이고, 공개 범위 표시가 붙는다)
 * @param hostedVisible "연 모임"을 보여 줄지
 * @param joinedVisible "참여한 모임"을 보여 줄지
 * @param hostedCount   연 모임 전체 수 (보여 줄 때만 의미 있음)
 * @param joinedCount   참여한 끝난 모임 전체 수 (보여 줄 때만 의미 있음)
 */
public record PublicProfile(
        User user,
        boolean self,
        Reputation reputation,
        boolean hostedVisible,
        List<Gathering> hostedUpcoming,
        List<Gathering> hostedPast,
        int hostedCount,
        boolean joinedVisible,
        List<Gathering> joinedPast,
        int joinedCount
) {
    /** 목록마다 최근 것부터 이만큼만 */
    public static final int LIST_MAX = 20;

    public String initial() {
        String n = user.getNickname();
        return n == null || n.isBlank() ? "?" : n.substring(0, 1);
    }
}
