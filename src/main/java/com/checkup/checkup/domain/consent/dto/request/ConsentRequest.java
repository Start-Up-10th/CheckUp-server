package com.checkup.checkup.domain.consent.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 서비스 이용 동의 요청(REQ-AUTH-004). 필수 두 항목은 true여야 한다.
 *
 * @param privacy     개인정보 수집 및 이용 동의(필수)
 * @param face        얼굴 정보 처리 동의(필수)
 * @param noticeAlarm 기숙사 공지 알림 수신(선택)
 */
public record ConsentRequest(
        @NotNull @AssertTrue(message = "개인정보 수집 및 이용에 동의해야 합니다.") Boolean privacy,
        @NotNull @AssertTrue(message = "얼굴 정보 처리에 동의해야 합니다.") Boolean face,
        @NotNull Boolean noticeAlarm
) {
}
