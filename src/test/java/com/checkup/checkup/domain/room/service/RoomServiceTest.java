package com.checkup.checkup.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.repository.AttendanceRepository;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.domain.room.dto.response.RoomStudentResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 호실 명단을 관리자는 모든 호실, 학생은 본인 호실만 조회할 수 있는지와 요청한 용도의 오늘 출석 여부를 함께 주는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long STUDENT_ID = 2L;
    private static final int ROOM = 301;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final AttendancePurpose DORMITORY = AttendancePurpose.DORMITORY;

    @Mock
    private MemberService memberService;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private OperatingDayCalculator operatingDayCalculator;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(memberService, studentRepository, attendanceRepository, operatingDayCalculator);
    }

    @Test
    @DisplayName("학생 정보가 없는 교사 관리자도 호실 명단을 조회할 수 있다")
    void teacherAdminCanReadAnyRoom() {
        Member admin = Member.create(10L, "사감", MemberRole.ADMIN);
        Student rosterStudent = student(20L, "학생", 1101, ROOM);
        given(memberService.getById(ADMIN_ID)).willReturn(admin);
        given(studentRepository.findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of(rosterStudent));

        given(operatingDayCalculator.today()).willReturn(TODAY);

        List<RoomStudentResponse> response = roomService.getStudents(ADMIN_ID, ROOM, DORMITORY);

        assertThat(response).containsExactly(new RoomStudentResponse("학생", 1, 1101, false));
        verify(studentRepository, never()).findByMember(admin);
    }

    @Test
    @DisplayName("학생은 본인에게 배정된 호실의 명단을 조회할 수 있다")
    void studentCanReadOwnRoom() {
        Member currentMember = Member.create(20L, "학생", MemberRole.STUDENT);
        Student currentStudent = student(20L, "학생", 1101, ROOM);
        Student roommate = student(21L, "룸메이트", 1102, ROOM);
        given(memberService.getById(STUDENT_ID)).willReturn(currentMember);
        given(studentRepository.findByMember(currentMember)).willReturn(Optional.of(currentStudent));
        given(studentRepository.findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of(roommate, currentStudent));

        given(operatingDayCalculator.today()).willReturn(TODAY);

        List<RoomStudentResponse> response = roomService.getStudents(STUDENT_ID, ROOM, DORMITORY);

        assertThat(response).extracting(RoomStudentResponse::studentName).containsExactly("룸메이트", "학생");
    }

    @Test
    @DisplayName("학생의 다른 호실 접근은 명단 조회 전에 거부한다")
    void studentIsRejectedForOtherRoom() {
        Member currentMember = Member.create(20L, "학생", MemberRole.STUDENT);
        given(memberService.getById(STUDENT_ID)).willReturn(currentMember);
        given(studentRepository.findByMember(currentMember))
                .willReturn(Optional.of(student(20L, "학생", 1101, ROOM)));

        assertThatThrownBy(() -> roomService.getStudents(STUDENT_ID, 401, DORMITORY))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(studentRepository, never())
                .findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(401);
    }

    @Test
    @DisplayName("학생 정보가 없으면 403을 반환한다")
    void missingStudentReturnsForbidden() {
        Member currentMember = Member.create(20L, "학생", MemberRole.STUDENT);
        given(memberService.getById(STUDENT_ID)).willReturn(currentMember);
        given(studentRepository.findByMember(currentMember)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getStudents(STUDENT_ID, ROOM, DORMITORY))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MISSING_STUDENT_INFO));

        verify(studentRepository, never())
                .findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(ROOM);
    }

    @Test
    @DisplayName("호실이 미배정된 학생에게 403을 반환한다")
    void studentWithoutRoomReturnsForbidden() {
        Member currentMember = Member.create(20L, "학생", MemberRole.STUDENT);
        given(memberService.getById(STUDENT_ID)).willReturn(currentMember);
        given(studentRepository.findByMember(currentMember))
                .willReturn(Optional.of(student(20L, "학생", 1101, null)));

        assertThatThrownBy(() -> roomService.getStudents(STUDENT_ID, ROOM, DORMITORY))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("조회한 호실에 학생이 없으면 403을 반환한다")
    void emptyRoomReturnsForbidden() {
        given(memberService.getById(ADMIN_ID)).willReturn(Member.create(10L, "사감", MemberRole.ADMIN));
        given(studentRepository.findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of());

        assertThatThrownBy(() -> roomService.getStudents(ADMIN_ID, ROOM, DORMITORY))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MISSING_STUDENT_INFO));
    }

    @Test
    @DisplayName("삭제된 회원의 요청은 401을 반환한다")
    void deletedMemberReturnsUnauthorized() {
        given(memberService.getById(STUDENT_ID))
                .willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> roomService.getStudents(STUDENT_ID, ROOM, DORMITORY))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND));

        verify(studentRepository, never())
                .findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(ROOM);
    }

    @Test
    @DisplayName("요청한 용도의 오늘 운영일에 출석한 학생만 attended가 true다")
    void marksStudentsAttendedForPurposeToday() {
        Member admin = Member.create(10L, "사감", MemberRole.ADMIN);
        Student attendedStudent = withId(student(20L, "출석", 1101, ROOM), 100L);
        Student absentStudent = withId(student(21L, "미출석", 1102, ROOM), 101L);
        given(memberService.getById(ADMIN_ID)).willReturn(admin);
        given(studentRepository.findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of(absentStudent, attendedStudent));
        given(operatingDayCalculator.today()).willReturn(TODAY);
        given(attendanceRepository.findAttendedStudentIds(List.of(101L, 100L), AttendancePurpose.STUDY_ROOM, TODAY))
                .willReturn(List.of(100L));

        List<RoomStudentResponse> response = roomService.getStudents(ADMIN_ID, ROOM, AttendancePurpose.STUDY_ROOM);

        assertThat(response).containsExactly(
                new RoomStudentResponse("미출석", 1, 1102, false),
                new RoomStudentResponse("출석", 1, 1101, true));
    }

    private static Student withId(Student student, long id) {
        ReflectionTestUtils.setField(student, "id", id);
        return student;
    }

    private static Student student(long datagsmId, String name, int studentNumber, Integer dormitoryRoom) {
        Member member = Member.create(datagsmId, name, MemberRole.STUDENT);
        return Student.create(member, datagsmId, name, 1, 1, 1, studentNumber, dormitoryRoom);
    }
}
