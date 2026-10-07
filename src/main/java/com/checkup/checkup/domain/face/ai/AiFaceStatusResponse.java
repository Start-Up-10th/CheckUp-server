package com.checkup.checkup.domain.face.ai;

/** AI 서버의 상태 응답. 준비 확인·세션 생성·세션 삭제 응답에 쓴다. */
public record AiFaceStatusResponse(String status) {
}
