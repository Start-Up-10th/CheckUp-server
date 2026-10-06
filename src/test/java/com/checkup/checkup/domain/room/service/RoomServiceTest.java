package com.checkup.checkup.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.repository.AttendanceRepository;
import com.checkup.checkup.domain.attendance.repository.RoomAttendanceCount;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.member.service.MemberService;
import com.checkup.checkup.domain.room.dto.request.RoomAttendanceRequest;
import com.checkup.checkup.domain.room.dto.response.RoomFloorResponse;
import com.checkup.checkup.domain.room.dto.response.RoomStudentResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * 층 현황은 관리자만 조회하고 그 층 호실을 오늘 운영일·용도로 세어 합산하는지도 검증한다.
 * 수동 출석 저장은 관리자만 할 수 있고 그 호실 학생의 상태만 저장하는지도 검증한다.
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

    @Mock
    private AdminVerifier adminVerifier;

    @Mock
    private AttendanceService attendanceService;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(
                memberService, studentRepository, attendanceRepository, operatingDayCalculator, adminVerifier,
                attendanceService);
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

        assertThat(response).containsExactly(new RoomStudentResponse(20L, "학생", 1, 1, 1101, false));
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
    @DisplayName("관리자가 학생이 없는 호실을 조회하면 빈 목록을 반환하고 출석을 조회하지 않는다")
    void emptyRoomReturnsEmptyListForAdmin() {
        given(memberService.getById(ADMIN_ID)).willReturn(Member.create(10L, "사감", MemberRole.ADMIN));
        given(studentRepository.findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of());

        assertThat(roomService.getStudents(ADMIN_ID, ROOM, DORMITORY)).isEmpty();
        verifyNoInteractions(attendanceRepository);
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
                new RoomStudentResponse(21L, "미출석", 1, 1, 1102, false),
                new RoomStudentResponse(20L, "출석", 1, 1, 1101, true));
    }

    @Test
    @DisplayName("층 현황은 그 층 호실 범위와 오늘 운영일로 세고 층 전체 출석·미출석을 합산한다")
    void floorSumsRoomsForTodayAndPurpose() {
        given(operatingDayCalculator.today()).willReturn(TODAY);
        given(attendanceRepository.countByRoom(300, 399, AttendancePurpose.STUDY_ROOM, TODAY))
                .willReturn(List.of(count(301, 4, 4), count(302, 3, 1)));

        RoomFloorResponse response = roomService.getFloor(ADMIN_ID, 3, AttendancePurpose.STUDY_ROOM);

        assertThat(response).isEqualTo(new RoomFloorResponse(3, AttendancePurpose.STUDY_ROOM, 5, 2, List.of(
                new RoomFloorResponse.Room(301, 4, 4),
                new RoomFloorResponse.Room(302, 1, 3))));
    }

    @Test
    @DisplayName("배정된 학생이 없는 층은 인원 0과 빈 호실 목록이다")
    void emptyFloorHasNoRooms() {
        given(operatingDayCalculator.today()).willReturn(TODAY);
        given(attendanceRepository.countByRoom(500, 599, DORMITORY, TODAY)).willReturn(List.of());

        RoomFloorResponse response = roomService.getFloor(ADMIN_ID, 5, DORMITORY);

        assertThat(response).isEqualTo(new RoomFloorResponse(5, DORMITORY, 0, 0, List.of()));
    }

    @Test
    @DisplayName("관리자가 아니면 층 현황은 ADMIN_ONLY이고 집계하지 않는다")
    void floorIsAdminOnly() {
        willThrow(new CustomException(ErrorCode.ADMIN_ONLY)).given(adminVerifier).verify(STUDENT_ID);

        assertThatThrownBy(() -> roomService.getFloor(STUDENT_ID, 3, DORMITORY))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));

        verifyNoInteractions(attendanceRepository);
    }

    @Test
    @DisplayName("수동 출석 저장은 요청의 DataGSM 학생 id를 그 호실 학생으로 바꿔 용도와 함께 저장한다")
    void savesManualAttendanceForRoomStudents() {
        given(studentRepository.findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of(
                        withId(student(20L, "학생", 1101, ROOM), 100L),
                        withId(student(21L, "룸메이트", 1102, ROOM), 101L)));

        roomService.saveAttendance(ADMIN_ID, ROOM, AttendancePurpose.STUDY_ROOM, request(item(20L, true), item(21L, false)));

        verify(attendanceService).saveManually(Map.of(100L, true, 101L, false), AttendancePurpose.STUDY_ROOM);
    }

    @Test
    @DisplayName("그 호실 학생이 아닌 학생이 섞여 있으면 STUDENT_NOT_IN_ROOM이고 아무것도 저장하지 않는다")
    void manualAttendanceForOtherRoomStudentSavesNothing() {
        given(studentRepository.findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of(withId(student(20L, "학생", 1101, ROOM), 100L)));

        assertThatThrownBy(() -> roomService.saveAttendance(
                ADMIN_ID, ROOM, DORMITORY, request(item(20L, true), item(99L, true))))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.STUDENT_NOT_IN_ROOM));

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("같은 학생이 요청에 두 번 있으면 INVALID_REQUEST이고 아무것도 저장하지 않는다")
    void manualAttendanceWithDuplicateStudentSavesNothing() {
        given(studentRepository.findAllByDormitoryRoomOrderByNameAscStudentNumberAscIdAsc(ROOM))
                .willReturn(List.of(withId(student(20L, "학생", 1101, ROOM), 100L)));

        assertThatThrownBy(() -> roomService.saveAttendance(
                ADMIN_ID, ROOM, DORMITORY, request(item(20L, true), item(20L, false))))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verifyNoInteractions(attendanceService);
    }

    @Test
    @DisplayName("관리자가 아니면 수동 출석 저장은 ADMIN_ONLY이고 학생을 조회하지도 저장하지도 않는다")
    void manualAttendanceIsAdminOnly() {
        willThrow(new CustomException(ErrorCode.ADMIN_ONLY)).given(adminVerifier).verify(STUDENT_ID);

        assertThatThrownBy(() -> roomService.saveAttendance(STUDENT_ID, ROOM, DORMITORY, request(item(20L, true))))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));

        verifyNoInteractions(studentRepository, attendanceService);
    }

    private static RoomAttendanceRequest request(RoomAttendanceRequest.Item... items) {
        return new RoomAttendanceRequest(List.of(items));
    }

    private static RoomAttendanceRequest.Item item(long studentId, boolean attended) {
        return new RoomAttendanceRequest.Item(studentId, attended);
    }

    private static RoomAttendanceCount count(int dormitoryRoom, long assigned, long attended) {
        return new RoomAttendanceCount() {
            @Override
            public Integer getDormitoryRoom() {
                return dormitoryRoom;
            }

            @Override
            public long getAssigned() {
                return assigned;
            }

            @Override
            public long getAttended() {
                return attended;
            }
        };
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
