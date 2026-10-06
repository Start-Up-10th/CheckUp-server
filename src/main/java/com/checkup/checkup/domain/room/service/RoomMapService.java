package com.checkup.checkup.domain.room.service;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.repository.AttendanceRepository;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.room.dto.response.ManualRoomAttendanceResponse;
import com.checkup.checkup.domain.room.dto.response.RoomAttendanceStudentResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 기숙사 출석 전개도의 호실 명단 조회와 학생 한 명 수동 출석 수정을 처리한다(REQ-ATT-006, REQ-UI-001).
 * 용도는 기숙사 입소(DORMITORY)로 고정이다.
 */
@Service
@RequiredArgsConstructor
public class RoomMapService {

    private final AdminVerifier adminVerifier;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceService attendanceService;
    private final OperatingDayCalculator operatingDayCalculator;

    /**
     * 한 호실의 학생과 오늘 운영일 기숙사 출석 여부를 이름 가나다순(학번·id 순)으로 돌려준다.
     *
     * @param memberId      세션의 회원 id
     * @param dormitoryRoom 호실 번호
     * @return 호실 출석 명단. DataGSM 학생 id가 없는 학생은 빠지고, 학생이 없으면 빈 목록이다
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403)
     */
    @Transactional(readOnly = true)
    public List<RoomAttendanceStudentResponse> getRoomAttendance(Long memberId, Integer dormitoryRoom) {
        adminVerifier.verify(memberId);
        List<Student> students = studentRepository.findAllByDormitoryRoomAndDatagsmStudentIdIsNotNull(dormitoryRoom)
                .stream()
                .sorted(Student.NAME_ORDER)
                .toList();
        Set<Long> attendedIds = findAttendedIds(students);
        return students.stream()
                .map(student -> RoomAttendanceStudentResponse.from(
                        student, attendedIds.contains(student.getId())))
                .toList();
    }

    /**
     * 학생 한 명의 오늘 운영일 기숙사 출석 상태를 관리자가 지정한다.
     * 상태가 실제로 바뀔 때만 기록하고, 출석 완료 알림도 함께 만들거나 지운다({@link AttendanceService#saveManually}).
     *
     * @param memberId         세션의 회원 id
     * @param dataGsmStudentId DataGSM 학생 id
     * @param attended         지정할 출석 상태
     * @return 지정한 출석 상태
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         학생이 없거나 호실이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404)
     */
    @Transactional
    public ManualRoomAttendanceResponse setManualAttendance(
            Long memberId, Long dataGsmStudentId, boolean attended) {
        adminVerifier.verify(memberId);
        Student student = studentRepository.findByDatagsmStudentId(dataGsmStudentId)
                .filter(candidate -> candidate.getDormitoryRoom() != null)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));

        attendanceService.saveManually(Map.of(student.getId(), attended), AttendancePurpose.DORMITORY);
        return new ManualRoomAttendanceResponse(dataGsmStudentId, attended);
    }

    private Set<Long> findAttendedIds(List<Student> students) {
        if (students.isEmpty()) {
            return Set.of();
        }
        List<Long> studentIds = students.stream().map(Student::getId).toList();
        return attendanceRepository.findAttendedStudentIds(
                        studentIds, AttendancePurpose.DORMITORY, operatingDayCalculator.today())
                .stream()
                .collect(Collectors.toSet());
    }
}
