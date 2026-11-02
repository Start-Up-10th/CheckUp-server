package com.checkup.checkup.domain.volunteer.service;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.notification.entity.NotificationType;
import com.checkup.checkup.domain.notification.service.NotificationService;
import com.checkup.checkup.domain.volunteer.repository.VolunteerDutyRepository;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기숙사 얼굴·QR 인증을 받은 날에만 연속 봉사를 자동 지정한다(DEC-036). */
@Service
@RequiredArgsConstructor
public class ConsecutiveVolunteerService {
    private final VolunteerDutyRepository volunteerDutyRepository;
    private final NotificationService notificationService;

    @Transactional
    public void onAttendance(Long studentId, AttendancePurpose purpose, AttendanceMethod method,
                             LocalDate day, Instant now) {
        if (purpose != AttendancePurpose.DORMITORY
                || (method != AttendanceMethod.FACE && method != AttendanceMethod.QR)) {
            return;
        }
        if (volunteerDutyRepository.assignConsecutive(studentId, day, now) == 1) {
            notificationService.create(studentId, NotificationType.VOLUNTEER, "duty:" + day,
                    "오늘 봉사 당번으로 지정됐어요. 봉사를 마치면 자치위원에게 확인받으세요.");
        }
    }
}
