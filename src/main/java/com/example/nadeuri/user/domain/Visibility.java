package com.example.nadeuri.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 프로필 활동 기록을 누구에게 보여 줄지 (users.hosted_visibility, users.joined_visibility) */
@Getter
@RequiredArgsConstructor
public enum Visibility {
    PUBLIC("누구나"),
    MEMBERS("함께한 사람만"),
    PRIVATE("나만 보기");

    private final String label;

    /**
     * 이 공개 범위에서 보는 사람에게 보여 줄지.
     * @param self      내 프로필을 내가 보는 중
     * @param metBefore 보는 사람이 나와 같은 모임에 함께한 적 있음
     */
    public boolean allows(boolean self, boolean metBefore) {
        return self || this == PUBLIC || (this == MEMBERS && metBefore);
    }
}
