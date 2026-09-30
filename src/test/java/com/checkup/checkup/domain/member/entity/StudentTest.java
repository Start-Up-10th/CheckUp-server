package com.checkup.checkup.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

    @Test
    @DisplayName("웹훅을 반영한 적 없으면 어떤 이벤트도 오래되지 않았다")
    void neverSyncedIsNotStale() {
        assertThat(student(301).isStale(Instant.parse("2026-06-23T05:00:00Z"))).isFalse();
    }

    @Test
    @DisplayName("마지막 반영 시각과 같거나 이전인 이벤트는 오래됐고 이후 이벤트는 새롭다")
    void staleComparedToLastSyncedTime() {
        Instant synced = Instant.parse("2026-06-23T05:00:00Z");
        Student student = student(301);
        student.markSynced(synced);

        assertThat(student.isStale(synced.minusSeconds(1))).isTrue();
        assertThat(student.isStale(synced)).isTrue();
        assertThat(student.isStale(synced.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("호실을 비우면 호실과 층은 null이 되고 학년·학번은 그대로다")
    void leaveDormitoryClearsRoomOnly() {
        Student student = student(301);

        student.leaveDormitory();

        assertThat(student.getDormitoryRoom()).isNull();
        assertThat(student.getDormitoryFloor()).isNull();
        assertThat(student.getStudentNumber()).isEqualTo(1101);
    }

    private static Student student(Integer dormitoryRoom) {
        return Student.create(Member.create(1L, "학생", MemberRole.STUDENT), 1L, 1, 1, 1, 1101,
                dormitoryRoom);
    }
}
