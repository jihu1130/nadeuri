package com.example.nadeuri.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * AI 도우미에게 보내는 질문.
 * messages: 지금까지의 대화 (오래된 것부터, 마지막은 사용자의 새 질문). 서버에 저장하지 않고 화면이 들고 있다가 보낸다.
 * lat/lng: 사용자가 보고 있는 위치 (없으면 여의도 기준). 근처 모임·날씨를 찾는 데만 쓴다.
 */
public record AssistantRequest(
        @NotEmpty @Size(max = MAX_MESSAGES) List<@Valid @NotNull Message> messages,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng
) {
    public static final int MAX_MESSAGES = 12;
    public static final int MAX_TEXT = 500;

    public record Message(
            @NotNull @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = MAX_TEXT) String text
    ) {
    }

    public boolean endsWithUser() {
        return !messages.isEmpty() && "user".equals(messages.get(messages.size() - 1).role());
    }
}
