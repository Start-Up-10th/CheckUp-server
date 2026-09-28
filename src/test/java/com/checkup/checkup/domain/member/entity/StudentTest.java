package com.checkup.checkup.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentTest {

    @Test
    @DisplayName("호실 번호에서 층을 정수로 계산한다")
    void 호실에서_층을_정수로_계산한다() {
        Student thirdFloor = student(301);
        Student fourthFloor = student(425);

        assertThat(thirdFloor.getDormitoryRoom()).isEqualTo(301);
        assertThat(thirdFloor.getDormitoryFloor()).isEqualTo(3);
        assertThat(fourthFloor.getDormitoryFloor()).isEqualTo(4);
    }

    @Test
    @DisplayName("호실이 미배정이면 층도 null을 반환한다")
    void 호실이_없으면_층도_없다() {
        Student unassigned = student(null);

        assertThat(unassigned.getDormitoryRoom()).isNull();
        assertThat(unassigned.getDormitoryFloor()).isNull();
    }

    private static Student student(Integer dormitoryRoom) {
        return Student.create(Member.create(1L, "학생", MemberRole.STUDENT), 1L, 1, 1, 1, 1101,
                dormitoryRoom);
    }
}
