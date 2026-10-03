package com.checkup.checkup.domain.auth.dto.response;

import com.checkup.checkup.domain.member.entity.Student;

/**
 * 현재 회원의 학생 정보. 학생 화면(홈·마이페이지·봉사)이 본인 학번·호실을 보여 주고 본인 데이터를 조회할 때 쓴다.
 *
 * @param studentId      DataGSM 학생 id. {@code /api/v1/users/{studentId}} 경로에 쓰는 값이다. 받은 적 없으면 null
 * @param grade          학년
 * @param classNumber    반
 * @param number         번호
 * @param studentNumber  화면 표시용 학번(예: 2405)
 * @param dormitoryRoom  기숙사 호실 번호. 배정되지 않았으면 null
 * @param dormitoryFloor 호실 번호로 계산한 층. 호실이 없으면 null
 */
public record CurrentStudentResponse(
        Long studentId,
        int grade,
        int classNumber,
        int number,
        int studentNumber,
        Integer dormitoryRoom,
        Integer dormitoryFloor
) {
    public static CurrentStudentResponse from(Student student) {
        return new CurrentStudentResponse(
                student.getDatagsmStudentId(),
                student.getGrade(),
                student.getClassNumber(),
                student.getNumber(),
                student.getStudentNumber(),
                student.getDormitoryRoom(),
                student.getDormitoryFloor());
    }
}
