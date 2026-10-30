package com.checkup.checkup.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.repository.AttendanceRepository;
import com.checkup.checkup.domain.attendance.repository.FloorStudentAttendance;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.room.dto.response.RoomAttendanceStudentResponse;
import com.checkup.checkup.domain.room.dto.response.RoomFloorStudentsResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 층 명단의 관리자 권한, 단일 조회, 호실 상세와 같은 정렬, 08시 운영일 경계를 검증한다(REQ-UI-001, REQ-ATT-001). */
@ExtendWith(MockitoExtension.class)
class RoomFloorStudentsServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private AttendanceService attendanceService;
    @Mock private AdminVerifier adminVerifier;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = serviceAt("2026-10-30T00:00:00Z");
    }

    @Test
    @DisplayName("층을 한 번 조회해 호실 번호순과 학생 이름·학번·내부 id 순으로 묶는다")
    void groupsAndSortsFloorStudentsWithOneQuery() {
        given(attendanceRepository.findFloorStudents(300, 399, AttendancePurpose.STUDY_ROOM,
                LocalDate.of(2026, 10, 30))).willReturn(List.of(
                row(7, 302, "학생A", 1101, false),
                row(6, 301, "홍학생", 1101, true),
                row(5, 301, "김학생", 1102, false),
                row(4, 301, "김학생", 1101, true),
                row(3, 301, "김학생", 1101, false),
                row(2, 301, "계학생", 1101, false),
                row(1, 301, "강학생", 1101, true)));

        RoomFloorStudentsResponse result = roomService.getFloorStudents(1L, 3, AttendancePurpose.STUDY_ROOM);

        assertThat(result.floor()).isEqualTo(3);
        assertThat(result.purpose()).isEqualTo(AttendancePurpose.STUDY_ROOM);
        assertThat(result.rooms()).extracting(RoomFloorStudentsResponse.Room::dormitoryRoom)
                .containsExactly(301, 302);
        assertThat(result.rooms().getFirst().students()).extracting(RoomAttendanceStudentResponse::studentId)
                .containsExactly(101L, 102L, 103L, 104L, 105L, 106L);
        assertThat(result.rooms().getFirst().students()).extracting(RoomAttendanceStudentResponse::attended)
                .containsExactly(true, false, false, true, false, true);
        verify(adminVerifier).verify(1L);
        verify(attendanceRepository).findFloorStudents(300, 399, AttendancePurpose.STUDY_ROOM,
                LocalDate.of(2026, 10, 30));
        verifyNoMoreInteractions(attendanceRepository);
        verifyNoInteractions(studentRepository, attendanceService);
    }

    @Test
    @DisplayName("학생이 없는 층은 빈 호실 배열을 반환한다")
    void emptyFloorReturnsNoRooms() {
        given(attendanceRepository.findFloorStudents(400, 499, AttendancePurpose.DORMITORY,
                LocalDate.of(2026, 10, 30))).willReturn(List.of());

        assertThat(roomService.getFloorStudents(1L, 4, AttendancePurpose.DORMITORY).rooms()).isEmpty();
    }

    @Test
    @DisplayName("관리자가 아니면 학생 명단을 읽기 전에 거부한다")
    void nonAdminCannotReadFloorStudents() {
        willThrow(new CustomException(ErrorCode.ADMIN_ONLY)).given(adminVerifier).verify(2L);

        assertThatThrownBy(() -> roomService.getFloorStudents(2L, 3, AttendancePurpose.DORMITORY))
                .isInstanceOfSatisfying(CustomException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ADMIN_ONLY));
        verifyNoInteractions(attendanceRepository, studentRepository, attendanceService);
    }

    @ParameterizedTest
    @DisplayName("한국 시간 08시 경계 전후로 조회 운영일을 바꾼다")
    @CsvSource({"2026-10-29T22:59:59Z, 2026-10-29", "2026-10-29T23:00:00Z, 2026-10-30"})
    void usesOperatingDayAtKstBoundary(String instant, LocalDate expectedDay) {
        roomService = serviceAt(instant);
        given(attendanceRepository.findFloorStudents(300, 399, AttendancePurpose.DORMITORY, expectedDay))
                .willReturn(List.of());

        roomService.getFloorStudents(1L, 3, AttendancePurpose.DORMITORY);

        verify(attendanceRepository).findFloorStudents(300, 399, AttendancePurpose.DORMITORY, expectedDay);
    }

    private RoomService serviceAt(String instant) {
        return new RoomService(studentRepository, attendanceRepository,
                new OperatingDayCalculator(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)),
                adminVerifier, attendanceService, org.mockito.Mockito.mock(com.checkup.checkup.domain.attendance.service.AttendanceHistoryService.class));
    }

    private static FloorStudentAttendance row(long id, int room, String name, int number, boolean attended) {
        return new FloorStudentAttendance(id, room, id + 100, name, 1, number, attended);
    }
}
