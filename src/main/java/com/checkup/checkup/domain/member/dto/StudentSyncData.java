package com.checkup.checkup.domain.member.dto;

/**
 * DataGSM에서 받은 학생 한 명의 정보. 웹훅과 수동 동기화가 같은 반영 로직을 쓰도록 공통 형태로 바꾼 값이다.
 *
 * 졸업·자퇴하면 학년·반·번호·학번·호실이 {@code null}로 온다.
 *
 * @param datagsmStudentId DataGSM 학생 식별자({@code student.datagsm_student_id})
 * @param name             이름
 * @param grade            학년
 * @param classNum         반
 * @param number           번호
 * @param studentNumber    화면 표시용 학번
 * @param dormitoryRoom    기숙사 호실
 * @param role             DataGSM role({@code GENERAL_STUDENT}, {@code DORMITORY_MANAGER}, {@code GRADUATE} 등)
 */
public record StudentSyncData(
        Long datagsmStudentId,
        String name,
        Integer grade,
        Integer classNum,
        Integer number,
        Integer studentNumber,
        Integer dormitoryRoom,
        String role
) {
}
