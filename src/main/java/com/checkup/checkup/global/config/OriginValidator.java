package com.checkup.checkup.global.config;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 웹 주소 설정({@code PUBLIC_ORIGIN})이 브라우저를 보낼 수 있는 http(s) origin인지 확인한다.
 *
 * 값이 깨져 있으면 로그인 콜백·QR 링크를 만들 때 500이 나므로, 설정을 읽는 시점에 막아 서버가 시작하지 않게 한다.
 * 예외 메시지에는 설정값을 넣지 않는다.
 */
public final class OriginValidator {

    private OriginValidator() {
    }

    /**
     * {@code http://host[:port]} 또는 {@code https://host[:port]} 형태인지 확인한다. 끝의 {@code /} 하나는 허용한다.
     * 빈 값은 각 설정의 {@code @NotBlank}가 막으므로 {@code null}은 그냥 통과시킨다.
     *
     * @param value 확인할 주소
     * @throws IllegalArgumentException 공백·주석·따옴표 등 주소에 쓸 수 없는 문자가 있거나, http(s)가 아니거나,
     *                                  호스트가 없거나, 경로·쿼리·fragment·사용자 정보가 붙어 있으면
     */
    public static void requireOrigin(String value) {
        if (value == null) {
            return;
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw invalidOrigin();
        }

        String scheme = uri.getScheme();
        String path = uri.getPath();
        boolean invalid = scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || !(path == null || path.isEmpty() || path.equals("/"))
                || uri.getQuery() != null
                || uri.getFragment() != null;
        if (invalid) {
            throw invalidOrigin();
        }
    }

    private static IllegalArgumentException invalidOrigin() {
        return new IllegalArgumentException(
                "PUBLIC_ORIGIN must be an absolute http(s) origin without path, query or fragment");
    }
}
