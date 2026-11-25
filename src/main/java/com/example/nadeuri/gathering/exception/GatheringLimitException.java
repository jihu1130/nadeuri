package com.example.nadeuri.gathering.exception;

import com.example.nadeuri.gathering.service.GatheringService;

/** 끝나지 않은 모임을 이미 최대 개수만큼 만든 사람이 새 모임을 만들려고 할 때 */
public class GatheringLimitException extends RuntimeException {
    public GatheringLimitException() {
        super("모임은 한 사람당 " + GatheringService.MAX_ACTIVE_HOSTED + "개까지 열 수 있어요. "
                + "진행 중이거나 예정된 모임이 끝나거나, 모임을 삭제한 뒤에 새로 만들어 주세요.");
    }
}
