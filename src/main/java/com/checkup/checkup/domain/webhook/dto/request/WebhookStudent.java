package com.checkup.checkup.domain.webhook.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * {@code student.updated} 이벤트의 학생 정보. 동기화에 쓰는 필드만 받고 이메일·성별 등은 받지 않는다.
 *
 * <p>졸업·자퇴하면 학년·반·번호·학번·호실이 {@code null}로 온다.
 *
 * @param studentId     DataGSM 학생 식별자({@code student.datagsm_student_id})
 * @param name          이름
 * @param grade         학년
 * @param classNum      반
 * @param number        번호
 * @param studentNumber 화면 표시용 학번
 * @param dormitoryRoom 기숙사 호실
 * @param role          소속·상태({@code GENERAL_STUDENT}, {@code DORMITORY_MANAGER}, {@code GRADUATE} 등)
 */
public record WebhookStudent(
        @JsonProperty("student_id") Long studentId,
        String name,
        Integer grade,
        @JsonProperty("class_num") Integer classNum,
        Integer number,
        @JsonProperty("student_number") Integer studentNumber,
        @JsonProperty("dormitory_room") Integer dormitoryRoom,
        String role
) {
}
