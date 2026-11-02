package com.checkup.checkup.domain.volunteer;

import com.checkup.checkup.domain.attendance.entity.AttendanceMethod;
import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.volunteer.service.VolunteerService;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 DB와 HTTP에서 연속 봉사의 독립 횟수·자동 지정·완료·재시도·권한을 검증한다(DEC-036). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsecutiveVolunteerIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AttendanceService attendance;
    @Autowired private VolunteerService volunteer;
    @Autowired private OperatingDayCalculator days;
    @Autowired private MockMvc mvc;
    private Long admin;
    private Long student;
    private long external;

    @BeforeEach
    void setUp() {
        long id = ThreadLocalRandom.current().nextLong(100_000_000, 900_000_000);
        admin = jdbc.queryForObject("INSERT INTO member (datagsm_id, name, role) VALUES (?, '관리자', 'ADMIN') RETURNING id", Long.class, id);
        external = id + 1;
        student = jdbc.queryForObject("""
                INSERT INTO student (datagsm_student_id, name, grade, class_number, number, student_number,
                                     dormitory_room, privacy_agreed_at, face_agreed_at, volunteer_count, consecutive_volunteer_count)
                VALUES (?, '연속학생', 1, 1, 1, 1101, 9701, now(), now(), 4, 2) RETURNING id
                """, Long.class, external);
    }

    @Test
    @DisplayName("기숙사 얼굴·QR 중복 인증은 지정·알림을 하나만 만들고 완료는 연속 횟수만 한 번 차감한다")
    void validAttendanceAssignsOnceAndCompletionOnlyConsumesConsecutiveCount() {
        attendance.markAttended(student, AttendancePurpose.DORMITORY, Instant.now(), AttendanceMethod.QR);
        attendance.markAttended(student, AttendancePurpose.DORMITORY, Instant.now(), AttendanceMethod.FACE);
        assertThat(dutyCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT consecutive FROM volunteer_duty WHERE student_id = ?", Boolean.class, student)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE student_id = ? AND type = 'VOLUNTEER'", Integer.class, student)).isEqualTo(1);
        var response = volunteer.completeDuty(admin, external);
        assertThat(response.consecutiveDuty()).isTrue();
        assertThat(response.consecutiveVolunteerCount()).isEqualTo(1);
        assertThat(response.volunteerCount()).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT kind FROM volunteer_adjustment WHERE student_id = ?", String.class, student))
                .isEqualTo("CONSECUTIVE_COMPLETION");
        assertThatThrownBy(() -> volunteer.completeDuty(admin, external)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUTY_ALREADY_COMPLETED));
        assertThat(jdbc.queryForObject("SELECT consecutive_volunteer_count FROM student WHERE id = ?", Integer.class, student)).isEqualTo(1);
        var record = volunteer.getDutyRecords(admin, days.today()).stream().filter(r -> r.studentId() == external).findFirst().orElseThrow();
        assertThat(record.consecutiveDuty()).isTrue();
        assertThat(volunteer.getVolunteers(admin, null, null, null, true).stream()
                .filter(r -> r.studentId() == external).findFirst().orElseThrow().consecutiveDuty()).isTrue();
    }

    @Test
    @DisplayName("수동 출석·자습실·과거·미래 인증은 연속 봉사를 지정하지 않는다")
    void unrelatedAndRejectedAttendanceNeverAssigns() {
        attendance.saveManually(Map.of(student, true), AttendancePurpose.DORMITORY);
        attendance.markAttended(student, AttendancePurpose.STUDY_ROOM, Instant.now(), AttendanceMethod.FACE);
        attendance.markAttended(student, AttendancePurpose.DORMITORY, Instant.now().minusSeconds(172800), AttendanceMethod.QR);
        attendance.markAttended(student, AttendancePurpose.DORMITORY, Instant.now().plusSeconds(60), AttendanceMethod.QR);
        assertThat(dutyCount()).isZero();
        // 수동 출석 뒤 실제 새 QR 인증은 유효한 자동 인증이므로 그날 지정을 만든다.
        attendance.markAttended(student, AttendancePurpose.DORMITORY, Instant.now(), AttendanceMethod.QR);
        assertThat(dutyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 일반 봉사자로 지정돼 있으면 자동 인증으로 종류를 바꾸지 않는다")
    void existingRegularDutyIsPreserved() {
        volunteer.assignDuty(admin, external);
        attendance.markAttended(student, AttendancePurpose.DORMITORY, Instant.now(), AttendanceMethod.FACE);
        var response = volunteer.completeDuty(admin, external);
        assertThat(response.consecutiveDuty()).isFalse();
        assertThat(response.volunteerCount()).isEqualTo(3);
        assertThat(response.consecutiveVolunteerCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("미출석일은 건너뛰고 잔여가 0이면 자동 지정하지 않는다")
    void skippedDaysDoNotResetAndZeroCountStopsAssignment() {
        jdbc.update("INSERT INTO volunteer_duty (student_id, operating_day, status, created_at, consecutive) VALUES (?, ?, 'COMPLETED', now(), TRUE)", student, days.today().minusDays(4));
        attendance.markAttended(student, AttendancePurpose.DORMITORY, Instant.now(), AttendanceMethod.FACE);
        assertThat(dutyCount()).isEqualTo(2);
        volunteer.cancelDuty(admin, external);
        volunteer.adjustConsecutiveCount(admin, external, "clear-" + external, -99, null);
        attendance.markAttended(student, AttendancePurpose.DORMITORY, Instant.now(), AttendanceMethod.QR);
        assertThat(dutyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("연속 횟수 조정은 재시도 시 한 번만 반영하고 일반 조정과 키를 섞지 못한다")
    void retriesAreIdempotentAndCounterKindsCannotShareKey() {
        String key = "consecutive-" + external;
        volunteer.adjustConsecutiveCount(admin, external, key, 5, "추가");
        assertThat(volunteer.adjustConsecutiveCount(admin, external, key, 5, "추가").consecutiveVolunteerCount()).isEqualTo(7);
        assertThatThrownBy(() -> volunteer.adjustCount(admin, external, key, 5, null))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThatThrownBy(() -> volunteer.adjustConsecutiveCount(admin, external, key, 6, null)).isInstanceOf(CustomException.class);
        assertThat(volunteer.adjustConsecutiveCount(admin, external, "remove-" + external, -99, null).consecutiveVolunteerCount()).isZero();
        assertThat(volunteer.adjustConsecutiveCount(admin, external, "remove-" + external, -99, null).consecutiveVolunteerCount()).isZero();
    }

    @Test
    @DisplayName("연속 조정 API는 별도 횟수 필드를 반환하고 미인증·일반 회원·잘못된 입력을 거부한다")
    void httpContractAndAuthorizationAreEnforced() throws Exception {
        String path = "/api/v1/volunteer/" + external + "/consecutive/count";
        var login = authentication(new UsernamePasswordAuthenticationToken(admin, null, List.of()));
        mvc.perform(patch(path).with(login).contentType(MediaType.APPLICATION_JSON).content("{\"delta\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consecutiveVolunteerCount").value(5))
                .andExpect(jsonPath("$.volunteerCount").value(4));
        mvc.perform(get("/api/v1/users/" + external + "/volunteer").with(login))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consecutiveVolunteerCount").value(5));
        mvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"delta\":1}"))
                .andExpect(status().isUnauthorized());
        Long regular = jdbc.queryForObject("INSERT INTO member (datagsm_id, name, role) VALUES (?, '학생', 'STUDENT') RETURNING id", Long.class, external + 2);
        mvc.perform(patch(path).with(authentication(new UsernamePasswordAuthenticationToken(regular, null, List.of())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":1}"))
                .andExpect(status().isForbidden());
        for (String body : List.of("{\"delta\":0}", "{\"delta\":100}", "{\"delta\":-100}", "{}")) {
            mvc.perform(patch(path).with(login).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    private int dutyCount() {
        return jdbc.queryForObject("SELECT count(*) FROM volunteer_duty WHERE student_id = ?", Integer.class, student);
    }

    @Test
    @DisplayName("동시 인증·완료 요청도 지정·알림·연속 차감을 각각 한 번만 반영한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAttendanceAndCompletionAreAtomic() throws Exception {
        try {
            race(() -> {
                attendance.markAttended(student, AttendancePurpose.DORMITORY, Instant.now(), AttendanceMethod.QR);
                return true;
            });
            assertThat(dutyCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE student_id = ? AND type = 'VOLUNTEER'", Integer.class, student)).isEqualTo(1);
            List<Boolean> completed = race(() -> {
                try {
                    volunteer.completeDuty(admin, external);
                    return true;
                } catch (CustomException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUTY_ALREADY_COMPLETED);
                    return false;
                }
            });
            assertThat(completed).filteredOn(Boolean::booleanValue).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT consecutive_volunteer_count FROM student WHERE id = ?", Integer.class, student)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM volunteer_adjustment WHERE student_id = ? AND kind = 'CONSECUTIVE_COMPLETION'", Integer.class, student)).isEqualTo(1);
            jdbc.update("UPDATE volunteer_duty SET status = 'ASSIGNED', completed_at = NULL WHERE student_id = ?", student);
            jdbc.update("UPDATE student SET consecutive_volunteer_count = 0 WHERE id = ?", student);
            assertThatThrownBy(() -> volunteer.completeDuty(admin, external)).isInstanceOfSatisfying(CustomException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VOLUNTEER_COUNT_ZERO));
            assertThat(jdbc.queryForObject("SELECT status FROM volunteer_duty WHERE student_id = ?", String.class, student)).isEqualTo("ASSIGNED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM volunteer_adjustment WHERE student_id = ? AND kind = 'CONSECUTIVE_COMPLETION'", Integer.class, student)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM student WHERE id = ?", student);
            jdbc.update("DELETE FROM member WHERE id = ?", admin);
        }
    }

    private List<Boolean> race(Callable<Boolean> action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();
            List<Boolean> result = new ArrayList<>();
            for (var future : futures) {
                result.add(future.get(20, TimeUnit.SECONDS));
            }
            return result;
        }
    }
}
