package com.example.nadeuri.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/** 우리 사이트 회원가입 폼 (아이디 + 비밀번호) */
public record SignUpRequest(
        @NotBlank(message = "아이디를 입력해 주세요.")
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{3,19}$",
                message = "아이디는 영문으로 시작하고, 영문·숫자·밑줄(_)로 4~20자여야 해요.")
        String username,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 정해 주세요.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "비밀번호에 영문과 숫자를 모두 넣어 주세요.")
        String password,

        @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
        String passwordConfirm,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 20, message = "닉네임은 20자까지 쓸 수 있어요.")
        String nickname
) {
    /** 아이디는 앞뒤 공백을 없애고 소문자로 맞춰 저장·비교한다 (Walker == walker) */
    public static String normalizeUsername(String username) {
        return username == null ? null : username.strip().toLowerCase(Locale.ROOT);
    }

    public boolean passwordsMatch() {
        return password != null && password.equals(passwordConfirm);
    }
}
