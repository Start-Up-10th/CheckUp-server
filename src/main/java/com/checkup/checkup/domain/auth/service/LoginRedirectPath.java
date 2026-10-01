package com.checkup.checkup.domain.auth.service;

/**
 * 로그인을 마치고 웹으로 돌아갈 경로를 정한다.
 *
 * 외부 사이트로 보내는 공개 리다이렉트(open redirect)를 막기 위해 같은 웹 안의 상대 경로만 받는다.
 * {@code /}로 시작해야 하고, 다른 호스트로 해석될 수 있는 {@code //}·역슬래시, 제어 문자·공백은 받지 않는다.
 * 조건에 맞지 않거나 값이 없으면 웹의 로그인 완료 화면으로 보낸다.
 */
public final class LoginRedirectPath {

    /** 웹 로그인 완료 화면. 회원 역할과 저장된 QR 복귀 주소를 보고 다음 화면을 정한다(CheckUp-Client#75). */
    public static final String DEFAULT = "/login/complete";

    private static final int MAX_LENGTH = 512;

    private LoginRedirectPath() {
    }

    /**
     * 요청한 경로가 안전하면 그대로, 아니면 {@link #DEFAULT}를 반환한다.
     */
    public static String sanitize(String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_LENGTH) {
            return DEFAULT;
        }
        if (!path.startsWith("/") || path.startsWith("//")) {
            return DEFAULT;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                return DEFAULT;
            }
        }
        return path;
    }
}
