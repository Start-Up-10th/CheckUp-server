package com.checkup.checkup.domain.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 학생 엔티티의 층 계산, 호실 비우기, DataGSM 이벤트 순서 판정, 동의 기록 규칙, 이름 가나다순 정렬(#150)을 검증한다.
 */
class StudentTest {

    @Test
    @DisplayName("호실 번호에서 층을 정수로 계산한다")
    void floorIsCalculatedFromRoom() {
        Student thirdFloor = student(301);
        Student fourthFloor = student(425);

        assertThat(thirdFloor.getDormitoryRoom()).isEqualTo(301);
        assertThat(thirdFloor.getDormitoryFloor()).isEqualTo(3);
        assertThat(fourthFloor.getDormitoryFloor()).isEqualTo(4);
    }

    @Test
    @DisplayName("호실이 미배정이면 층도 null을 반환한다")
    void noRoomMeansNoFloor() {
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

    @Test
    @DisplayName("동의하기 전에는 필수 동의가 없고 공지 알림도 받지 않는다")
    void noConsentByDefault() {
        Student student = student(301);

        assertThat(student.hasRequiredConsent()).isFalse();
        assertThat(student.isNoticeAlarmAgreed()).isFalse();
    }

    @Test
    @DisplayName("동의하면 필수 두 항목의 동의 시각과 공지 알림 수신 여부를 기록한다")
    void agreeRecordsRequiredConsentAndNoticeAlarm() {
        Student student = student(301);
        Instant now = Instant.parse("2026-09-30T00:00:00Z");

        student.agree(true, now, "v1");

        assertThat(student.hasRequiredConsent()).isTrue();
        assertThat(student.getPrivacyAgreedAt()).isEqualTo(now);
        assertThat(student.getFaceAgreedAt()).isEqualTo(now);
        assertThat(student.getFaceConsentVersion()).isEqualTo("v1");
        assertThat(student.isNoticeAlarmAgreed()).isTrue();
    }

    @Test
    @DisplayName("출석은 필수 동의를 마치고 호실이 있는 학생만 인정한다")
    void attendanceEligibleNeedsConsentAndRoom() {
        Instant now = Instant.parse("2026-09-30T00:00:00Z");
        Student notAgreed = student(301);
        Student agreed = student(301);
        agreed.agree(false, now, "v1");
        Student agreedWithoutRoom = student(301);
        agreedWithoutRoom.agree(false, now, "v1");
        agreedWithoutRoom.leaveDormitory();

        assertThat(notAgreed.isAttendanceEligible()).isFalse();
        assertThat(agreed.isAttendanceEligible()).isTrue();
        assertThat(agreedWithoutRoom.isAttendanceEligible()).isFalse();
    }

    @Test
    @DisplayName("다시 동의하면 처음 동의 시각은 유지하고 공지 알림 수신만 바꾼다")
    void agreeAgainKeepsFirstTimeAndUpdatesNoticeAlarm() {
        Student student = student(301);
        Instant first = Instant.parse("2026-09-30T00:00:00Z");
        student.agree(true, first, "v1");

        student.agree(false, first.plusSeconds(60), "v2");

        assertThat(student.getPrivacyAgreedAt()).isEqualTo(first);
        assertThat(student.getFaceAgreedAt()).isEqualTo(first);
        assertThat(student.getFaceConsentVersion()).isEqualTo("v1");
        assertThat(student.isNoticeAlarmAgreed()).isFalse();
    }

    private static Student student(Integer dormitoryRoom) {
        return Student.create(Member.create(1L, "학생", MemberRole.STUDENT), 1L, "학생", 1, 1, 1, 1101,
                dormitoryRoom);
    }

    @Test
    @DisplayName("이름 정렬은 가나다순이고 같은 이름은 학번순이다")
    void nameOrderIsKoreanAlphabetical() {
        List<Student> sorted = Stream.of(
                        Student.createWithoutMember(1L, "홍길동", 1, 1, 1, 1102, 301),
                        Student.createWithoutMember(2L, "계정", 1, 1, 1, 1103, 301),
                        Student.createWithoutMember(3L, "김", 1, 1, 1, 1104, 301),
                        Student.createWithoutMember(4L, "홍길동", 1, 1, 1, 1101, 301),
                        Student.createWithoutMember(5L, "강학생", 1, 1, 1, 1105, 301))
                .sorted(Student.NAME_ORDER)
                .toList();

        assertThat(sorted).extracting(Student::getName).containsExactly("강학생", "계정", "김", "홍길동", "홍길동");
        assertThat(sorted).extracting(Student::getStudentNumber).containsExactly(1105, 1103, 1104, 1101, 1102);
    }
}
