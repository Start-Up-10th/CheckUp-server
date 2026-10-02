package com.checkup.checkup.domain.room.service;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.repository.AttendanceRepository;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.room.dto.response.ManualRoomAttendanceResponse;
import com.checkup.checkup.domain.room.dto.response.RoomAttendanceStudentResponse;
import com.checkup.checkup.domain.room.dto.response.RoomFloorResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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
    private final OperatingDayCalculator operatingDayCalculator;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<RoomFloorResponse> getRoomsByFloor(Long memberId, Integer floor) {
        adminVerifier.verify(memberId);
        List<Student> students = studentRepository.findAllByDatagsmStudentIdIsNotNull().stream()
                .filter(student -> student.getDormitoryRoom() != null)
                .filter(student -> Objects.equals(student.getDormitoryFloor(), floor))
                .toList();
        Set<Long> attendedIds = findAttendedIds(students);

        Map<Integer, List<Student>> studentsByRoom = students.stream()
                .collect(Collectors.groupingBy(
                        Student::getDormitoryRoom,
                        TreeMap::new,
                        Collectors.toList()));

        return studentsByRoom.entrySet().stream()
                .map(entry -> new RoomFloorResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        (int) entry.getValue().stream()
                                .filter(student -> attendedIds.contains(student.getId()))
                                .count()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoomAttendanceStudentResponse> getRoomAttendance(Long memberId, Integer dormitoryRoom) {
        adminVerifier.verify(memberId);
        List<Student> students = studentRepository
                .findAllByDormitoryRoomAndDatagsmStudentIdIsNotNullOrderByMember_NameAscStudentNumberAscIdAsc(
                        dormitoryRoom);
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

        Instant changedAt = clock.instant();
        LocalDate operatingDay = operatingDayCalculator.of(changedAt);
        attendanceRepository.saveManualAttendance(
                student.getId(),
                AttendancePurpose.DORMITORY.name(),
                operatingDay,
                attended,
                changedAt);
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
