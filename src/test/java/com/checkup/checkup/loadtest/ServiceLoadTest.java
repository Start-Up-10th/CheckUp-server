package com.checkup.checkup.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;
import com.checkup.checkup.domain.attendance.service.AttendanceService;
import com.checkup.checkup.domain.notification.service.NotificationService;
import com.checkup.checkup.domain.qr.dto.QrSessionIssue;
import com.checkup.checkup.domain.qr.entity.QrScanResult;
import com.checkup.checkup.domain.qr.service.QrScanService;
import com.checkup.checkup.domain.qr.service.QrSessionService;
import com.checkup.checkup.domain.room.service.RoomService;
import com.checkup.checkup.global.querylog.QueryCounter;

/**
 * 출석 시간대에 몰리는 요청을 서비스 계층에서 동시에 호출해 응답 시간과 요청당 DB 쿼리 수를 잰다(#218).
 *
 * 평소 빌드에서는 돌지 않는다. 환경변수 {@code LOADTEST=true}일 때만 실행하며, 실제 Postgres·Redis(로컬 compose)가 필요하다.
 * 학생 {@value #STUDENTS}명을 임시로 만들고 끝나면 지운다. 결과는 {@code build/loadtest/report.md}에 쓴다.
 *
 * 한계: HTTP·로그인·보안 필터·JSON 직렬화와 DataGSM·얼굴 AI 호출은 포함하지 않는다. 같은 PC의 DB·Redis라 네트워크 지연이 없다.
 * 절대값이 아니라 변경 전후 비교와 요청당 쿼리 수를 보는 용도다. 동시 호출 수는 {@code LOADTEST_CONCURRENCY}(기본 50)로 바꾼다.
 */
@SpringBootTest(properties = "checkup.query-log.enabled=true")
@EnabledIfEnvironmentVariable(named = "LOADTEST", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceLoadTest {

    static final int STUDENTS = 200;
    private static final int WARMUP_STUDENTS = 20;
    private static final int FIRST_ROOM = 301;
    private static final int STUDENTS_PER_ROOM = 4;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueryCounter queryCounter;

    @Autowired
    private QrSessionService qrSessionService;

    @Autowired
    private QrScanService qrScanService;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RoomService roomService;

    private final int concurrency = Integer.parseInt(System.getenv().getOrDefault("LOADTEST_CONCURRENCY", "50"));
    private final List<Long> memberIds = new ArrayList<>();
    private final List<Long> studentIds = new ArrayList<>();
    private final List<String> report = new ArrayList<>();
    private Long adminMemberId;

    @BeforeAll
    void seed() {
        long base = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);
        adminMemberId = jdbcTemplate.queryForObject(
                "INSERT INTO member (datagsm_id, name, role) VALUES (?, '부하관리자', 'ADMIN') RETURNING id",
                Long.class, base);
        for (int i = 0; i < STUDENTS + WARMUP_STUDENTS; i++) {
            Long memberId = jdbcTemplate.queryForObject(
                    "INSERT INTO member (datagsm_id, name, role) VALUES (?, ?, 'STUDENT') RETURNING id",
                    Long.class, base + 1 + i, "부하학생" + i);
            Long studentId = jdbcTemplate.queryForObject(
                    "INSERT INTO student (member_id, name, number, grade, class_number, student_number, "
                            + "dormitory_room, privacy_agreed_at, face_agreed_at, datagsm_student_id) "
                            + "VALUES (?, ?, 1, 1, 1, ?, ?, now(), now(), ?) RETURNING id",
                    Long.class, memberId, "부하학생" + i, 1100 + i, FIRST_ROOM + i / STUDENTS_PER_ROOM, base + 1 + i);
            memberIds.add(memberId);
            studentIds.add(studentId);
        }
    }

    @AfterAll
    void cleanUp() throws IOException {
        for (Long studentId : studentIds) {
            jdbcTemplate.update("DELETE FROM notification WHERE student_id = ?", studentId);
            jdbcTemplate.update("DELETE FROM attendance WHERE student_id = ?", studentId);
            jdbcTemplate.update("DELETE FROM student WHERE id = ?", studentId);
        }
        for (Long memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE id = ?", memberId);
        }
        jdbcTemplate.update("DELETE FROM member WHERE id = ?", adminMemberId);

        Path out = Path.of("build", "loadtest", "report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, String.join("\n", report) + "\n");
        System.out.println(String.join("\n", report));
    }

    @Test
    @DisplayName("출석 시간대 시나리오: QR 스캔 폭주 → 학생 홈 조회 → 관리자 호실·층 조회")
    void attendanceRush() throws Exception {
        report.add("# 서비스 계층 부하 기준값 (#218)");
        report.add("");
        report.add("학생 " + STUDENTS + "명, 동시 호출 " + concurrency + "개, 실제 Postgres·Redis(로컬). HTTP·로그인·직렬화 제외.");
        report.add("");
        report.add("| 시나리오 | 호출 수 | 총 시간(ms) | 처리량(건/초) | p50(ms) | p95(ms) | 최대(ms) | 호출당 쿼리(평균/최대) |");
        report.add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");

        // 첫 호출의 JIT·커넥션 준비 비용이 섞이지 않도록, 측정 대상이 아닌 학생으로 먼저 한 번 돌린다.
        QrSessionIssue warmupSession = qrSessionService.create(adminMemberId, AttendancePurpose.DORMITORY);
        run("warmup", slice(STUDENTS, STUDENTS + WARMUP_STUDENTS),
                memberId -> qrScanService.scan(memberId, warmupSession.token()));
        qrSessionService.close(adminMemberId, warmupSession.sessionId());

        QrSessionIssue session = qrSessionService.create(adminMemberId, AttendancePurpose.STUDY_ROOM);
        Result scan = run("QR 스캔(첫 출석)", slice(0, STUDENTS),
                memberId -> qrScanService.scan(memberId, session.token()));
        assertThat(scan.results()).containsOnly(QrScanResult.APPROVED);

        Result duplicate = run("QR 스캔(중복)", slice(0, STUDENTS),
                memberId -> qrScanService.scan(memberId, session.token()));
        assertThat(duplicate.results()).containsOnly(QrScanResult.DUPLICATE);

        run("학생 홈: 본인 출석 조회", slice(0, STUDENTS), attendanceService::getMyToday);
        run("학생 홈: 미확인 알림 조회", slice(0, STUDENTS), notificationService::hasUnread);

        List<Long> adminCalls = new ArrayList<>();
        for (int i = 0; i < STUDENTS; i++) {
            adminCalls.add(adminMemberId);
        }
        run("관리자: 호실 명단 조회", adminCalls, memberId -> roomService.getStudents(
                memberId, FIRST_ROOM + ThreadLocalRandom.current().nextInt(STUDENTS / STUDENTS_PER_ROOM),
                AttendancePurpose.STUDY_ROOM));
        run("관리자: 층 출석 현황", adminCalls.subList(0, 50),
                memberId -> roomService.getFloor(memberId, 3, AttendancePurpose.STUDY_ROOM));

        qrSessionService.close(adminMemberId, session.sessionId());
        report.add("");
        report.add("얼굴 인식 프레임은 AI 서버 호출이 있어 이 측정에 포함하지 않았다.");
    }

    private List<Long> slice(int from, int to) {
        return memberIds.subList(from, to);
    }

    /** 호출 목록을 동시 호출 수만큼의 스레드로 한꺼번에 실행하고, 호출마다 걸린 시간과 쿼리 수를 모아 보고서에 한 줄 더한다. */
    private <T> Result run(String name, List<Long> callers, CallerAction<T> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<Sample<T>>> futures = new ArrayList<>();
            long startedAt = System.nanoTime();
            for (Long caller : callers) {
                Callable<Sample<T>> task = () -> {
                    queryCounter.start();
                    long begin = System.nanoTime();
                    T result = action.apply(caller);
                    long elapsed = System.nanoTime() - begin;
                    return new Sample<>(elapsed, queryCounter.stop(), result);
                };
                futures.add(executor.submit(task));
            }
            long[] latencies = new long[callers.size()];
            int[] queries = new int[callers.size()];
            List<T> results = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                Sample<T> sample = futures.get(i).get(2, TimeUnit.MINUTES);
                latencies[i] = sample.nanos;
                queries[i] = sample.queries;
                results.add(sample.result);
            }
            long wallNanos = System.nanoTime() - startedAt;
            if (!name.equals("warmup")) {
                report.add(row(name, latencies, queries, wallNanos));
            }
            return new Result(new ArrayList<Object>(results));
        } finally {
            executor.shutdownNow();
        }
    }

    private static String row(String name, long[] latencies, int[] queries, long wallNanos) {
        long[] sorted = latencies.clone();
        Arrays.sort(sorted);
        double wallSeconds = wallNanos / 1e9;
        return String.format("| %s | %d | %.0f | %.0f | %.1f | %.1f | %.1f | %.1f / %d |",
                name, latencies.length, wallNanos / 1e6, latencies.length / wallSeconds,
                percentile(sorted, 50) / 1e6, percentile(sorted, 95) / 1e6, sorted[sorted.length - 1] / 1e6,
                Arrays.stream(queries).average().orElse(0), Arrays.stream(queries).max().orElse(0));
    }

    private static long percentile(long[] sorted, int percent) {
        int index = (int) Math.ceil(percent / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    @FunctionalInterface
    private interface CallerAction<T> {
        T apply(Long caller) throws Exception;
    }

    private record Sample<T>(long nanos, int queries, T result) {
    }

    private record Result(List<Object> results) {
    }
}
