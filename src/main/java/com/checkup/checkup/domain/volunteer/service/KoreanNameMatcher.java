package com.checkup.checkup.domain.volunteer.service;

/**
 * 한글 이름 검색에 쓰는 도우미. 외부 검색 엔진 없이 학생 수백 명 규모에서 쓴다.
 */
public final class KoreanNameMatcher {

    private static final char HANGUL_BEGIN = '가';
    private static final char HANGUL_END = '힣';
    private static final char[] CHOSEONG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'};
    private static final char[] JUNGSEONG = {
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ',
            'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'};
    private static final char[] JONGSEONG = {
            0, 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ',
            'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'};

    private KoreanNameMatcher() {
    }

    /** 한글 음절을 초성으로 바꾼다. 한글 음절이 아닌 글자는 그대로 둔다. */
    static String initials(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            result.append(isSyllable(c) ? CHOSEONG[(c - HANGUL_BEGIN) / 588] : c);
        }
        return result.toString();
    }

    /** 한글 음절을 초성·중성·종성으로 쪼갠다. 한글 음절이 아닌 글자는 그대로 둔다. */
    static String jamo(String text) {
        StringBuilder result = new StringBuilder(text.length() * 3);
        for (char c : text.toCharArray()) {
            if (!isSyllable(c)) {
                result.append(c);
                continue;
            }
            int index = c - HANGUL_BEGIN;
            result.append(CHOSEONG[index / 588]).append(JUNGSEONG[index % 588 / 28]);
            char jong = JONGSEONG[index % 28];
            if (jong != 0) {
                result.append(jong);
            }
        }
        return result.toString();
    }

    private static boolean isSyllable(char c) {
        return c >= HANGUL_BEGIN && c <= HANGUL_END;
    }
}
