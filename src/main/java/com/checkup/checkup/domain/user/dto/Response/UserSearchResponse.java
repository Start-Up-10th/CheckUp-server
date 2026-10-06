package com.checkup.checkup.domain.user.dto.Response;

import io.swagger.v3.oas.annotations.media.Schema;
import team.themoment.datagsm.sdk.openapi.model.Student;

/** DataGSM에서 조회한 학생 정보. 이메일 등은 서버에 저장하지 않고 조회할 때만 받는다. */
public record UserSearchResponse(
        @Schema(description = "DataGSM 학생 id", example = "1") Long id,
        @Schema(description = "이름", example = "홍길동") String name,
        @Schema(description = "DataGSM에 등록된 이메일. 서버에 저장하지 않고 조회할 때만 받는다") String email,
        @Schema(description = "기숙사 호실 번호. 배정되지 않았으면 null", example = "301") Integer dormitoryRoom,
        @Schema(description = "성별. MAN 또는 WOMAN", example = "MAN") Sex sex,
        @Schema(description = "학년", example = "2") Integer grade,
        @Schema(description = "반", example = "4") Integer classNumber,
        @Schema(description = "번호", example = "5") Integer number,
        @Schema(description = "DataGSM 학생 역할. GENERAL_STUDENT, STUDENT_COUNCIL, DORMITORY_MANAGER, GRADUATE, WITHDRAWN", example = "GENERAL_STUDENT") StudentRole studentRole
) {

    public static UserSearchResponse from(Student student) {
        return new UserSearchResponse(
                student.getId(),
                student.getName(),
                student.getEmail(),
                student.getDormitoryRoom().orElse(null),
                toSex(student.getSex()),
                student.getGrade().orElse(null),
                student.getClassNum().orElse(null),
                student.getNumber().orElse(null),
                toRole(student.getRole())
        );
    }

    private static Sex toSex(team.themoment.datagsm.sdk.openapi.model.Sex sdk) {
        if (sdk == null) {
            return null;
        }
        return switch (sdk) {
            case MAN -> Sex.MAN;
            case WOMAN -> Sex.WOMAN;
        };
    }

    private static StudentRole toRole(team.themoment.datagsm.sdk.openapi.model.StudentRole sdk) {
        if (sdk == null) {
            return null;
        }
        return switch (sdk) {
            case GENERAL_STUDENT -> StudentRole.GENERAL_STUDENT;
            case STUDENT_COUNCIL -> StudentRole.STUDENT_COUNCIL;
            case DORMITORY_MANAGER -> StudentRole.DORMITORY_MANAGER;
            case GRADUATE -> StudentRole.GRADUATE;
            case WITHDRAWN -> StudentRole.WITHDRAWN;
        };
    }
}
