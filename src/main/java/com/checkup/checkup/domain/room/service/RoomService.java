package com.checkup.checkup.domain.room.service;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.repository.AttendanceRepository;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.room.dto.request.RoomAttendanceRequest;
import com.checkup.checkup.domain.room.dto.response.RoomFloorResponse;
import com.checkup.checkup.domain.room.dto.response.RoomStudentResponse;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 호실 명단, 층 단위 출석 현황, 호실 수동 출석 저장의 접근 범위와 응답을 처리한다. */
@Service
@RequiredArgsConstructor
public class RoomService {

    private static final int ROOMS_PER_FLOOR = 100;

    private final StudentRepository studentRepository;
    private final AttendanceRepository attendanceRepository;
    private final OperatingDayCalculator operatingDayCalculator;
    private final AdminVerifier adminVerifier;
    private final AttendanceService attendanceService;

    /**
     * 현재 회원의 권한을 확인한 뒤 해당 호실의 학생 명단과 오늘 운영일의 출석 여부를 조회한다.
     *
     * @param memberId 세션의 현재 회원 id
     * @param dormitoryRoom 조회할 호실 번호
     * @param purpose 출석 여부를 볼 용도(기숙사 입소·자습실). 용도가 다르면 출석을 따로 본다(REQ-ATT-001)
     * @return 이름(가나다)·학번 순으로 정렬된 호실 학생 명단. 관리자가 빈 호실을 조회하면 빈 목록이다(#151)
     */
    @Transactional(readOnly = true)
    public List<RoomStudentResponse> getStudents(Long memberId, Integer dormitoryRoom, AttendancePurpose purpose) {
        if (!adminVerifier.isAdmin(memberId)) {
            Student currentStudent = studentRepository.findByMemberId(memberId)
                    .orElseThrow(() -> new CustomException(ErrorCode.MISSING_STUDENT_INFO));
            Integer currentRoom = currentStudent.getDormitoryRoom();
            if (currentRoom == null || !currentRoom.equals(dormitoryRoom)) {
                throw new CustomException(ErrorCode.FORBIDDEN);
            }
        }

        List<Student> students = studentRepository.findAllByDormitoryRoom(dormitoryRoom).stream()
                .sorted(Student.NAME_ORDER)
                .toList();
        if (students.isEmpty()) {
            // 학생 본인 호실은 본인이 있어 비지 않는다. 빈 호실은 권한 문제가 아니므로 관리자에게 빈 목록으로 응답한다.
            return List.of();
        }

        Set<Long> attended = new HashSet<>(attendanceRepository.findAttendedStudentIds(
                students.stream().map(Student::getId).toList(), purpose, operatingDayCalculator.today()));
        return students.stream()
                .map(student -> RoomStudentResponse.from(student, attended.contains(student.getId())))
                .toList();
    }

    /**
     * 관리자 전개도용으로 한 층의 호실별 배정 인원과 오늘 운영일의 출석 인원을 조회한다(REQ-UI-001).
     * 층은 호실 번호를 100으로 나눈 값이다. 그 층에 배정된 학생이 없으면 호실 목록이 비고 인원은 0이다.
     *
     * @param memberId 세션의 현재 회원 id
     * @param floor 조회할 층
     * @param purpose 출석 용도(기숙사 입소·자습실). 용도가 다르면 출석을 따로 센다(REQ-ATT-001)
     * @return 층 전체 출석·미출석 인원과 호실 번호 오름차순의 호실별 현황
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403)
     */
    @Transactional(readOnly = true)
    public RoomFloorResponse getFloor(Long memberId, int floor, AttendancePurpose purpose) {
        adminVerifier.verify(memberId);
        return RoomFloorResponse.of(floor, purpose, attendanceRepository.countByRoom(
                floor * ROOMS_PER_FLOOR, floor * ROOMS_PER_FLOOR + ROOMS_PER_FLOOR - 1,
                purpose, operatingDayCalculator.today()));
    }

    /**
     * 관리자가 호실 상세에서 고른 학생별 출석 상태를 오늘 운영일에 저장한다(REQ-ATT-006).
     * 요청에 그 호실 학생이 아닌 학생이 하나라도 있으면 아무것도 저장하지 않는다.
     *
     * @param memberId 세션의 현재 회원 id
     * @param dormitoryRoom 저장할 호실 번호
     * @param purpose 출석 용도(기숙사 입소·자습실)
     * @param request 학생별 출석 상태
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         같은 학생이 두 번 있으면 {@link ErrorCode#INVALID_REQUEST}(400),
     *                         그 호실 학생이 아니면 {@link ErrorCode#STUDENT_NOT_IN_ROOM}(400)
     */
    @Transactional
    public void saveAttendance(
            Long memberId, Integer dormitoryRoom, AttendancePurpose purpose, RoomAttendanceRequest request) {
        adminVerifier.verify(memberId);

        Map<Long, Long> roomStudentIds = new HashMap<>();
        for (Student student : studentRepository.findAllByDormitoryRoom(dormitoryRoom)) {
            if (student.getDatagsmStudentId() != null) {
                roomStudentIds.put(student.getDatagsmStudentId(), student.getId());
            }
        }

        Map<Long, Boolean> attendedByStudentId = new HashMap<>();
        for (RoomAttendanceRequest.Item item : request.students()) {
            Long studentId = roomStudentIds.get(item.studentId());
            if (studentId == null) {
                throw new CustomException(ErrorCode.STUDENT_NOT_IN_ROOM);
            }
            if (attendedByStudentId.put(studentId, item.attended()) != null) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
        }
        attendanceService.saveManually(attendedByStudentId, purpose);
    }
}
