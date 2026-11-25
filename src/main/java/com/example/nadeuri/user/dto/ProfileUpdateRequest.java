package com.example.nadeuri.user.dto;

import com.example.nadeuri.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 프로필 설정: 닉네임, 자기소개 한 줄 */
public record ProfileUpdateRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = User.NICKNAME_MAX, message = "닉네임은 20자까지 쓸 수 있어요.")
        String nickname,

        @Size(max = User.BIO_MAX, message = "자기소개는 100자까지 쓸 수 있어요.")
        String bio
) {
}
