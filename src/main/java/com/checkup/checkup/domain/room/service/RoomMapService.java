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

@Service
@RequiredArgsConstructor
public class RoomMapService {

    private final AdminVerifier adminVerifier;
    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceService attendanceService;
    private final OperatingDayCalculator operatingDayCalculator;

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
