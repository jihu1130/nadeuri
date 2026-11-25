package com.example.nadeuri.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 프로필 설정: 비밀번호 바꾸기 (아이디 회원만). 새 비밀번호 규칙은 회원가입과 같다 */
public record PasswordChangeRequest(
        @NotBlank(message = "지금 비밀번호를 입력해 주세요.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 정해 주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "비밀번호에 영문과 숫자를 모두 넣어 주세요.")
        String newPassword,

        @NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
        String newPasswordConfirm
) {
    public boolean passwordsMatch() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
