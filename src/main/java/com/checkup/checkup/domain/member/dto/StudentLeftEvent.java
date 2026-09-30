package com.checkup.checkup.domain.member.dto;

/**
 * DataGSM에서 졸업·자퇴가 확인된 학생을 알리는 이벤트. 얼굴 벡터·캐시처럼 학생이 떠나면 지워야 할 데이터를 정리할 때 받는다.
 *
 * <p>받는 쪽은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 받아,
 * 웹훅 처리가 DB에 확정된 뒤에만 삭제한다. 누락·일시 장애만으로는 발행하지 않는다.
 *
 * @param studentId 서비스 내부 학생 ID({@code student.id})
 * @param reason    DataGSM role({@code GRADUATE} 또는 {@code WITHDRAWN})
 */
public record StudentLeftEvent(
        Long studentId,
        String reason
) {
}
