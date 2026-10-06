package com.checkup.checkup.domain.room;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkup.checkup.global.time.OperatingDayCalculator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * 호실 전개도 세 API가 실제 PostgreSQL 출석 상태를 공유하고 관리자 권한을 검사하는지 검증한다(REQ-ATT-006).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomMapApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OperatingDayCalculator operatingDayCalculator;

    private Long adminId;
    private Long studentMemberId;
    private Long unlinkedDataGsmId;

    @BeforeEach
    void setUp() {
        adminId = insertMember("관리자", "ADMIN");
        studentMemberId = insertMember("이전이름", "STUDENT");
        Long linkedStudentId = insertStudent(studentMemberId, nextDataGsmId(), "가학생", 1101, 301);
        unlinkedDataGsmId = nextDataGsmId();
        insertStudent(null, unlinkedDataGsmId, "나학생", 1102, 301);
        insertStudent(null, nextDataGsmId(), "다학생", 1103, 302);
        jdbcTemplate.update("""
                INSERT INTO attendance (student_id, purpose, operating_day, attended, method, manual_updated_at)
                VALUES (?, 'DORMITORY', ?, TRUE, 'MANUAL', now())
                """, linkedStudentId, operatingDayCalculator.today());
    }

    @Test
    @DisplayName("계정 없는 학생도 명단에 나오고 수동 저장은 명단과 층별 출석 수에 반영된다")
    void manualAttendanceUpdatesRosterAndFloorTotals() throws Exception {
        mockMvc.perform(get("/api/v1/room/attendance")
                        .param("dormitoryRoom", "301")
                        .with(loginAs(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].student_name").value("가학생"))
                .andExpect(jsonPath("$[0].attended").value(true))
                .andExpect(jsonPath("$[1].student_id").value(unlinkedDataGsmId))
                .andExpect(jsonPath("$[1].student_name").value("나학생"))
                .andExpect(jsonPath("$[1].attended").value(false));

        mockMvc.perform(get("/api/v1/room/floor")
                        .param("floor", "3")
                        .with(loginAs(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].dormitoryRoom").value(301))
                .andExpect(jsonPath("$.rooms[0].assigned").value(2))
                .andExpect(jsonPath("$.rooms[0].attended").value(1))
                .andExpect(jsonPath("$.rooms[1].dormitoryRoom").value(302))
                .andExpect(jsonPath("$.rooms[1].assigned").value(1))
                .andExpect(jsonPath("$.rooms[1].attended").value(0));

        mockMvc.perform(put("/api/v1/room/student/{studentId}/attendance", unlinkedDataGsmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attended\":true}")
                        .with(loginAs(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student_id").value(unlinkedDataGsmId))
                .andExpect(jsonPath("$.attended").value(true));

        mockMvc.perform(get("/api/v1/room/attendance")
                        .param("dormitoryRoom", "301")
                        .with(loginAs(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].attended").value(true));
        mockMvc.perform(get("/api/v1/room/floor")
                        .param("floor", "3")
                        .with(loginAs(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms[0].attended").value(2));

        mockMvc.perform(put("/api/v1/room/student/{studentId}/attendance", unlinkedDataGsmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attended\":false}")
                        .with(loginAs(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attended").value(false));
        mockMvc.perform(get("/api/v1/room/attendance")
                        .param("dormitoryRoom", "301")
                        .with(loginAs(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].attended").value(false));
    }

    @Test
    @DisplayName("관리자 권한을 가진 학생은 호실 전개도 조회와 수동 출석 저장을 사용할 수 있다")
    void dormitoryManagerStudentCanUseRoomMapApis() throws Exception {
        Long managerStudentMemberId = insertMember("자치위원", "ADMIN");
        insertStudent(managerStudentMemberId, nextDataGsmId(), "자치위원", 1104, 303);

        mockMvc.perform(get("/api/v1/room/attendance")
                        .param("dormitoryRoom", "301")
                        .with(loginAs(managerStudentMemberId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/room/floor")
                        .param("floor", "3")
                        .with(loginAs(managerStudentMemberId)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/room/student/{studentId}/attendance", unlinkedDataGsmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attended\":true}")
                        .with(loginAs(managerStudentMemberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attended").value(true));
    }

    @Test
    @DisplayName("일반 학생은 호실 전개도 조회와 수동 출석 저장을 사용할 수 없다")
    void regularStudentCannotUseRoomMapApis() throws Exception {
        mockMvc.perform(get("/api/v1/room/attendance")
                        .param("dormitoryRoom", "301")
                        .with(loginAs(studentMemberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));
        mockMvc.perform(get("/api/v1/room/floor")
                        .param("floor", "3")
                        .with(loginAs(studentMemberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));
        mockMvc.perform(put("/api/v1/room/student/{studentId}/attendance", unlinkedDataGsmId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attended\":true}")
                        .with(loginAs(studentMemberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));
    }

    private Long insertMember(String name, String role) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO member (datagsm_id, name, role) VALUES (?, ?, ?) RETURNING id",
                Long.class, nextDataGsmId(), name, role);
    }

    private Long insertStudent(Long memberId, Long dataGsmId, String name, int studentNumber, int room) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO student
                    (member_id, datagsm_student_id, name, number, grade, class_number, student_number, dormitory_room)
                VALUES (?, ?, ?, 1, 1, 1, ?, ?) RETURNING id
                """, Long.class, memberId, dataGsmId, name, studentNumber, room);
    }

    private static Long nextDataGsmId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }

    private static RequestPostProcessor loginAs(Long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of()));
    }
}
