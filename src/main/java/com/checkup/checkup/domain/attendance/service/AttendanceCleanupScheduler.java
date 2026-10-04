package com.checkup.checkup.domain.attendance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceCleanupScheduler {

        private final AttendanceService attendanceService;

        @EventListener(ApplicationReadyEvent.class)
        @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
        public void deleteExpiredAttendance() {
            int deleted = attendanceService.deleteExpired();
            log.info("Deleted expired attendance records: count={}", deleted);
        }
}
