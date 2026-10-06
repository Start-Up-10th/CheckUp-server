package com.checkup.checkup.domain.face.entity;

/**
 * 최근 인식 기록 한 줄의 결과(#143).
 *
 * - {@code SUCCESS}: 알아봤고 출석 처리됐다.
 * - {@code FAILED}: 알아봤지만 출석 처리되지 않았거나(늦게 도착·수동 처리 이후 등), 못 알아봐 QR을 안내했다.
 */
public enum FaceRecognitionResult {
    SUCCESS,
    FAILED
}
