package com.checkup.checkup.domain.volunteer.service;

import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.volunteer.dto.response.VolunteerAdjustmentResponse;
import com.checkup.checkup.domain.volunteer.dto.response.VolunteerDutyRecordResponse;
import com.checkup.checkup.domain.volunteer.dto.response.VolunteerResponse;
import com.checkup.checkup.domain.notification.service.NotificationService;
import com.checkup.checkup.domain.notification.entity.NotificationType;
import com.checkup.checkup.domain.volunteer.entity.DutyStatus;
import com.checkup.checkup.domain.volunteer.entity.VolunteerAdjustment;
import com.checkup.checkup.domain.volunteer.entity.VolunteerAdjustmentKind;
import com.checkup.checkup.domain.volunteer.entity.VolunteerDuty;
import com.checkup.checkup.domain.volunteer.repository.VolunteerAdjustmentRepository;
import com.checkup.checkup.domain.volunteer.repository.VolunteerDutyRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import com.checkup.checkup.global.security.AdminVerifier;
import com.checkup.checkup.global.time.OperatingDayCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * 봉사 관리(DEC-020). 사감·기숙사 자치위원(관리자)만 사용할 수 있다.
 *
 * 명단은 DataGSM 학생 id가 있는 저장된 전체 학생이다. 관리자는 이 명단에서 학생의 봉사 횟수를 조정한다.
 * 봉사 횟수는 앞으로 해야 할 봉사 횟수이고, 조정은 알림을 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class VolunteerService {

    private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");
    private static final int MAX_REQUEST_KEY_LENGTH = 100;
    private static final int MAX_REASON_LENGTH = 100;
    private static final int MAX_ADJUST_AMOUNT = 99;
    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT = 100;
    private static final String DUTY_MESSAGE = "오늘 봉사 당번으로 지정됐어요. 봉사를 마치면 자치위원에게 확인받으세요.";

    /** 호실 → 이름 → 학번순. 호실이 없는 학생은 맨 뒤에 둔다. */
    private static final Comparator<Student> ROOM_ORDER = Comparator
            .comparing(Student::getDormitoryRoom, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Student::getName)
            .thenComparing(Student::getStudentNumber);

    private final AdminVerifier adminVerifier;
    private final StudentRepository studentRepository;
    private final VolunteerAdjustmentRepository volunteerAdjustmentRepository;
    private final VolunteerDutyRepository volunteerDutyRepository;
    private final NotificationService notificationService;
    private final OperatingDayCalculator operatingDayCalculator;
    private final Clock clock;

    /**
     * 전체 학생의 봉사 횟수 명단을 호실·이름·학번순으로 조회한다.
     *
     * @param memberId 세션의 회원 id
     * @param floor    층(호실의 맨 앞자리). {@code null}이면 전체 층이고, 값이 있으면 호실 미배정 학생은 빠진다.
     * @param query    검색어. 숫자면 호실 번호나 학번이 정확히 같은 학생이다. 그 밖에는 이름으로 찾고,
     *                 이름에 포함 → 초성 일치 → 오타 1개 순으로 앞에 둔다({@link KoreanNameMatcher}). 비어 있으면 전체다.
     * @param minCount 최소 봉사 횟수. 당일 봉사자 지정 후보는 1을 넣어 봉사가 남은 학생만 찾는다. {@code null}이면 제한 없다.
     * @param onDuty   {@code true}면 오늘 당일 봉사자로 지정된(완료 포함) 학생만 본다. {@code null}·{@code false}면 제한 없다.
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403)
     */
    @Transactional(readOnly = true)
    public List<VolunteerResponse> getVolunteers(
            Long memberId, Integer floor, String query, Integer minCount, Boolean onDuty) {
        adminVerifier.verify(memberId);
        ToIntFunction<Student> rank = ranker(query);
        Map<Long, Instant> lastActivities = volunteerAdjustmentRepository.findLastActivities().stream()
                .collect(Collectors.toMap(
                        VolunteerAdjustmentRepository.LastActivity::getStudentId,
                        VolunteerAdjustmentRepository.LastActivity::getLastActivityAt));
        Map<Long, VolunteerDuty> todayDuties = volunteerDutyRepository
                .findAllByOperatingDay(operatingDayCalculator.today()).stream()
                .collect(Collectors.toMap(duty -> duty.getStudent().getId(), Function.identity()));
        return studentRepository.findAllByDatagsmStudentIdIsNotNull()
                .stream()
                .filter(student -> floor == null || Objects.equals(student.getDormitoryFloor(), floor))
                .filter(student -> minCount == null || student.getVolunteerCount() >= minCount)
                .filter(student -> !Boolean.TRUE.equals(onDuty) || todayDuties.containsKey(student.getId()))
                .filter(student -> rank.applyAsInt(student) != KoreanNameMatcher.NO_MATCH)
                .sorted(Comparator.comparingInt(rank).thenComparing(ROOM_ORDER))
                .map(student -> VolunteerResponse.of(
                        student, lastActivities.get(student.getId()),
                        todayDuties.containsKey(student.getId()) ? todayDuties.get(student.getId()).getStatus() : null,
                        todayDuties.containsKey(student.getId()) && todayDuties.get(student.getId()).isConsecutive()))
                .toList();
    }

    /**
     * 봉사 횟수를 1 늘린다. 기숙사에서 잘못해 봉사가 생겼을 때 쓴다.
     *
     * @param memberId   세션의 회원 id
     * @param studentId  DataGSM 학생 id
     * @param requestKey 재시도 방지 키(선택). 같은 키로 다시 오면 반영하지 않고 현재 상태만 돌려준다.
     * @return 조정 뒤 학생의 명단 항목
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         키가 100자를 넘으면 {@link ErrorCode#INVALID_REQUEST}(400)
     */
    @Transactional
    public VolunteerResponse increase(Long memberId, Long studentId, String requestKey) {
        return adjust(memberId, studentId, requestKey, 1, null);
    }

    /**
     * 봉사 횟수를 1 줄인다. 봉사를 마쳤거나 사감이 특별한 사정으로 감면할 때 쓴다. 0 미만으로는 줄이지 않는다.
     *
     * @param memberId   세션의 회원 id
     * @param studentId  DataGSM 학생 id
     * @param requestKey 재시도 방지 키(선택). 같은 키로 다시 오면 반영하지 않고 현재 상태만 돌려준다.
     * @return 조정 뒤 학생의 명단 항목
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         횟수가 0이면 {@link ErrorCode#VOLUNTEER_COUNT_ZERO}(409),
     *                         키가 100자를 넘으면 {@link ErrorCode#INVALID_REQUEST}(400)
     */
    @Transactional
    public VolunteerResponse decrease(Long memberId, Long studentId, String requestKey) {
        return adjust(memberId, studentId, requestKey, -1, null);
    }

    /**
     * 봉사 횟수를 여러 회 늘리거나 줄이고 사유를 남긴다(#139). 차감은 남은 횟수까지만 한다.
     *
     * @param memberId   세션의 회원 id
     * @param studentId  DataGSM 학생 id
     * @param requestKey 재시도 방지 키(선택). 같은 키로 다시 오면 반영하지 않고 현재 상태만 돌려준다.
     * @param delta      바꿀 횟수. 양수는 추가, 음수는 차감이며 -99~99, 0 제외
     * @param reason     사유(선택, 100자 이하). 비어 있으면 남기지 않는다.
     * @return 조정 뒤 학생의 명단 항목
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         차감인데 횟수가 0이면 {@link ErrorCode#VOLUNTEER_COUNT_ZERO}(409),
     *                         횟수·사유·키가 범위를 벗어나면 {@link ErrorCode#INVALID_REQUEST}(400)
     */
    @Transactional
    public VolunteerResponse adjustCount(Long memberId, Long studentId, String requestKey, int delta, String reason) {
        if (delta == 0 || Math.abs(delta) > MAX_ADJUST_AMOUNT) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return adjust(memberId, studentId, requestKey, delta, reason);
    }

    /**
     * 학생 한 명의 봉사 횟수 조정 이력을 최신순으로 돌려준다(#140). 당일 봉사 완료의 차감도 포함한다.
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @param limit     최대 개수(선택, 1~100). 없으면 50
     * @return 조정 이력. 없으면 빈 목록
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         개수가 범위를 벗어나면 {@link ErrorCode#INVALID_REQUEST}(400)
     */
    @Transactional(readOnly = true)
    public List<VolunteerAdjustmentResponse> getAdjustments(Long memberId, Long studentId, Integer limit) {
        adminVerifier.verify(memberId);
        int size = limit == null ? DEFAULT_HISTORY_LIMIT : limit;
        if (size < 1 || size > MAX_HISTORY_LIMIT) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        Long id = findStudent(studentId).getId();
        return volunteerAdjustmentRepository.findByStudent_IdOrderByCreatedAtDescIdDesc(id, Limit.of(size)).stream()
                .map(VolunteerAdjustmentResponse::from)
                .toList();
    }

    /**
     * 학생을 오늘(운영일) 당일 봉사자로 지정하고 봉사 알림을 보낸다. 봉사 횟수는 바꾸지 않는다.
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @return 지정 뒤 학생의 명단 항목
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         봉사 횟수가 0이면 {@link ErrorCode#NO_VOLUNTEER_LEFT}(409),
     *                         오늘 이미 지정됐으면 {@link ErrorCode#ALREADY_ON_DUTY}(409)
     */
    @Transactional
    public VolunteerResponse assignDuty(Long memberId, Long studentId) {
        adminVerifier.verify(memberId);
        Student student = findStudent(studentId);
        if (student.getVolunteerCount() <= 0) {
            throw new CustomException(ErrorCode.NO_VOLUNTEER_LEFT);
        }
        LocalDate today = operatingDayCalculator.today();
        if (volunteerDutyRepository.assign(student.getId(), today, clock.instant()) == 0) {
            throw new CustomException(ErrorCode.ALREADY_ON_DUTY);
        }
        notificationService.create(student.getId(), NotificationType.VOLUNTEER, dutyKey(today), DUTY_MESSAGE);
        return toResponse(student);
    }

    /**
     * 오늘 당일 봉사자 지정을 취소하고 그 지정 알림을 지운다. 이미 완료한 지정은 취소할 수 없다.
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @return 취소 뒤 학생의 명단 항목
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         오늘 지정되지 않았으면 {@link ErrorCode#NOT_ON_DUTY}(404),
     *                         이미 완료했으면 {@link ErrorCode#DUTY_ALREADY_COMPLETED}(409),
     *                         미이행으로 처리했으면 {@link ErrorCode#DUTY_MISSED}(409)
     */
    @Transactional
    public VolunteerResponse cancelDuty(Long memberId, Long studentId) {
        adminVerifier.verify(memberId);
        Student student = findStudent(studentId);
        LocalDate today = operatingDayCalculator.today();
        VolunteerDuty duty = findTodayDuty(student.getId(), today);
        rejectMissed(duty);
        if (volunteerDutyRepository.cancel(duty.getId()) == 0) {
            throw new CustomException(ErrorCode.DUTY_ALREADY_COMPLETED);
        }
        notificationService.delete(student.getId(), NotificationType.VOLUNTEER, dutyKey(today));
        return reload(student.getId());
    }

    /**
     * 오늘 당일 봉사를 완료로 표시하고 봉사 횟수를 1 줄인다. 자치위원이 봉사를 확인했을 때 쓴다.
     * 완료 표시와 차감은 한 트랜잭션이라, 차감할 수 없으면 완료 표시도 취소된다.
     *
     * @param memberId  세션의 회원 id
     * @param studentId DataGSM 학생 id
     * @return 완료 뒤 학생의 명단 항목
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         오늘 지정되지 않았으면 {@link ErrorCode#NOT_ON_DUTY}(404),
     *                         이미 완료했으면 {@link ErrorCode#DUTY_ALREADY_COMPLETED}(409),
     *                         미이행으로 처리했으면 {@link ErrorCode#DUTY_MISSED}(409),
     *                         그 사이 횟수가 0이 됐으면 {@link ErrorCode#VOLUNTEER_COUNT_ZERO}(409)
     */
    @Transactional
    public VolunteerResponse completeDuty(Long memberId, Long studentId) {
        adminVerifier.verify(memberId);
        Long id = findStudent(studentId).getId();
        Instant now = clock.instant();
        VolunteerDuty duty = findTodayDuty(id, operatingDayCalculator.today());
        rejectMissed(duty);
        if (volunteerDutyRepository.complete(duty.getId(), now) == 0) {
            throw new CustomException(ErrorCode.DUTY_ALREADY_COMPLETED);
        }
        volunteerAdjustmentRepository.insertIfAbsent(
                id, -1, -1, null, (duty.isConsecutive() ? VolunteerAdjustmentKind.CONSECUTIVE_COMPLETION
                        : VolunteerAdjustmentKind.DUTY_COMPLETION).name(), null, now);
        if ((duty.isConsecutive() ? studentRepository.decreaseConsecutiveVolunteerCount(id)
                : studentRepository.decreaseVolunteerCount(id)) == 0) {
            throw new CustomException(ErrorCode.VOLUNTEER_COUNT_ZERO);
        }
        return reload(id);
    }

    /**
     * 한 운영일의 당일 봉사자 기록을 호실·이름·학번순으로 돌려준다(#250). 오늘 운영일도 같은 형태다.
     * 운영일이 지났는데 아직 지정 상태인 지정은 미이행(횟수 미처리)으로 본다.
     *
     * @param memberId     세션의 회원 id
     * @param operatingDay 조회할 운영일. {@code null}이면 오늘 운영일
     * @return 그날 지정된 학생들. 지정이 없으면 빈 목록
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403)
     */
    @Transactional(readOnly = true)
    public List<VolunteerDutyRecordResponse> getDutyRecords(Long memberId, LocalDate operatingDay) {
        adminVerifier.verify(memberId);
        LocalDate today = operatingDayCalculator.today();
        List<VolunteerDuty> duties = volunteerDutyRepository
                .findAllWithStudentByOperatingDay(operatingDay == null ? today : operatingDay);
        if (duties.isEmpty()) {
            return List.of();
        }
        Map<Long, VolunteerAdjustment> adjustments = missedAdjustments(duties.stream().map(VolunteerDuty::getId).toList());
        return duties.stream()
                .sorted(Comparator.comparing(VolunteerDuty::getStudent, ROOM_ORDER))
                .map(duty -> VolunteerDutyRecordResponse.of(
                        duty.getStudent(), recordStatus(duty, today), adjustments.get(duty.getId()), duty.isConsecutive()))
                .toList();
    }

    /**
     * 한 달에서 미이행이 있는 운영일과 그날의 미이행 인원을 날짜순으로 돌려준다(#250). 달력의 표시에 쓴다.
     * 횟수를 처리한 미이행과 처리하지 않은 미이행을 함께 센다.
     *
     * @param memberId 세션의 회원 id
     * @param month    조회할 달
     * @return 운영일 → 미이행 인원. 미이행이 없는 날은 없다
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403)
     */
    @Transactional(readOnly = true)
    public Map<LocalDate, Long> getMissedSummary(Long memberId, YearMonth month) {
        adminVerifier.verify(memberId);
        return volunteerDutyRepository
                .countMissedByOperatingDay(month.atDay(1), month.atEndOfMonth(), operatingDayCalculator.today())
                .stream()
                .collect(Collectors.toMap(
                        VolunteerDutyRepository.MissedDay::getOperatingDay,
                        VolunteerDutyRepository.MissedDay::getMissedCount,
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    /**
     * 오늘 당일 봉사를 미이행으로 처리하고 봉사 횟수를 바꾼다(#250). 지정은 지우지 않는다.
     *
     * @param memberId   세션의 회원 id
     * @param studentId  DataGSM 학생 id
     * @param requestKey 재시도 방지 키(선택). 같은 키로 다시 오면 반영하지 않고 현재 상태만 돌려준다.
     * @param delta      바꿀 횟수(-99~99). 0이면 횟수는 그대로 두고 미이행만 기록한다
     * @param reason     사유(선택, 100자 이하)
     * @return 처리 뒤 학생의 명단 항목
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         오늘 지정되지 않았으면 {@link ErrorCode#NOT_ON_DUTY}(404),
     *                         그 밖에는 {@link #markMissed}와 같다
     */
    @Transactional
    public VolunteerResponse markTodayMissed(Long memberId, Long studentId, String requestKey, int delta, String reason) {
        Long id = recordMissed(memberId, studentId, operatingDayCalculator.today(), requestKey, delta, reason,
                ErrorCode.NOT_ON_DUTY);
        return reload(id);
    }

    /**
     * 한 운영일의 당일 봉사를 미이행으로 처리하고 봉사 횟수를 바꾼다(#250). 달력에서 횟수 미처리 미이행을 처리할 때 쓴다.
     * 횟수를 이미 처리한 미이행은 다시 처리하지 않는다. 그 뒤의 조정은 일반 횟수 조정을 쓴다.
     *
     * @param memberId     세션의 회원 id
     * @param studentId    DataGSM 학생 id
     * @param operatingDay 지정의 운영일
     * @param requestKey   재시도 방지 키(선택). 같은 키로 다시 오면 반영하지 않고 현재 상태만 돌려준다.
     * @param delta        바꿀 횟수(-99~99). 0이면 횟수는 그대로 두고 미이행만 기록한다. 차감은 남은 횟수까지만 한다
     * @param reason       사유(선택, 100자 이하)
     * @return 처리 뒤 그날의 기록
     * @throws CustomException 관리자가 아니면 {@link ErrorCode#ADMIN_ONLY}(403),
     *                         저장된 학생이 없으면 {@link ErrorCode#STUDENT_NOT_FOUND}(404),
     *                         그날 지정되지 않았으면 {@link ErrorCode#DUTY_NOT_FOUND}(404),
     *                         완료한 지정이면 {@link ErrorCode#DUTY_ALREADY_COMPLETED}(409),
     *                         횟수를 이미 처리했으면 {@link ErrorCode#DUTY_ALREADY_ADJUSTED}(409),
     *                         차감인데 횟수가 0이면 {@link ErrorCode#VOLUNTEER_COUNT_ZERO}(409),
     *                         키를 다른 요청에 썼으면 {@link ErrorCode#IDEMPOTENCY_KEY_REUSED}(409),
     *                         횟수·사유·키가 범위를 벗어나면 {@link ErrorCode#INVALID_REQUEST}(400)
     */
    @Transactional
    public VolunteerDutyRecordResponse markMissed(
            Long memberId, Long studentId, LocalDate operatingDay, String requestKey, int delta, String reason) {
        Long id = recordMissed(memberId, studentId, operatingDay, requestKey, delta, reason, ErrorCode.DUTY_NOT_FOUND);
        VolunteerDuty duty = volunteerDutyRepository.findByStudentIdAndOperatingDay(id, operatingDay)
                .orElseThrow(() -> new CustomException(ErrorCode.DUTY_NOT_FOUND));
        return VolunteerDutyRecordResponse.of(duty.getStudent(), recordStatus(duty, operatingDayCalculator.today()),
                missedAdjustments(List.of(duty.getId())).get(duty.getId()), duty.isConsecutive());
    }

    /**
     * 지정을 잠가 미이행으로 바꾸고, 횟수를 바꿀 때는 그 지정에 연결한 조정 기록을 먼저 남긴다.
     * 같은 지정의 요청은 지정 잠금으로 차례로 처리해 같은 키의 재시도가 한 번만 반영된다.
     *
     * @return 학생 DB id
     */
    private Long recordMissed(Long memberId, Long studentId, LocalDate operatingDay, String requestKey,
                              int requestedDelta, String reason, ErrorCode notFound) {
        adminVerifier.verify(memberId);
        if (Math.abs(requestedDelta) > MAX_ADJUST_AMOUNT) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        String key = normalizeKey(requestKey);
        String savedReason = normalizeReason(reason);
        Long id = findStudent(studentId).getId();

        VolunteerDuty duty = volunteerDutyRepository.lockByStudentIdAndOperatingDay(id, operatingDay)
                .orElseThrow(() -> new CustomException(notFound));
        if (key != null && isMissedRetry(key, duty.getId(), requestedDelta)) {
            return id;
        }
        if (duty.getStatus() == DutyStatus.COMPLETED) {
            throw new CustomException(ErrorCode.DUTY_ALREADY_COMPLETED);
        }
        if (volunteerAdjustmentRepository.existsByDuty_IdAndKind(duty.getId(), VolunteerAdjustmentKind.DUTY_MISSED)) {
            throw new CustomException(ErrorCode.DUTY_ALREADY_ADJUSTED);
        }
        if (requestedDelta != 0) {
            int count = studentRepository.lockVolunteerCount(id)
                    .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
            int delta = requestedDelta > 0 ? requestedDelta : -Math.min(-requestedDelta, count);
            if (delta == 0) {
                throw new CustomException(ErrorCode.VOLUNTEER_COUNT_ZERO);
            }
            if (volunteerAdjustmentRepository.insertMissed(
                    id, duty.getId(), delta, requestedDelta, savedReason, key, clock.instant()) == 0) {
                throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            studentRepository.changeVolunteerCount(id, delta);
        }
        if (volunteerDutyRepository.markMissed(duty.getId()) == 0) {
            throw new CustomException(ErrorCode.DUTY_ALREADY_COMPLETED);
        }
        return id;
    }

    /**
     * 같은 키의 기록이 이미 있으면 같은 지정·같은 요청 횟수의 미이행 재시도인지 확인한다.
     * 다른 요청에 쓴 키면 반영되지 않았는데 성공으로 보이지 않도록 {@link ErrorCode#IDEMPOTENCY_KEY_REUSED}(409)다.
     *
     * @return 재시도면 {@code true}, 그 키의 기록이 없으면 {@code false}
     */
    private boolean isMissedRetry(String key, Long dutyId, int requestedDelta) {
        return volunteerAdjustmentRepository.findByRequestKey(key)
                .map(existing -> {
                    boolean same = existing.getKind() == VolunteerAdjustmentKind.DUTY_MISSED
                            && existing.getDuty() != null
                            && existing.getDuty().getId().equals(dutyId)
                            && existing.getRequestedDelta() == requestedDelta;
                    if (!same) {
                        throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
                    }
                    return true;
                })
                .orElse(false);
    }

    /** 지정 id → 그 지정의 미이행 조정 기록. 횟수를 처리하지 않은 지정은 없다. */
    private Map<Long, VolunteerAdjustment> missedAdjustments(List<Long> dutyIds) {
        return volunteerAdjustmentRepository
                .findAllByDuty_IdInAndKind(dutyIds, VolunteerAdjustmentKind.DUTY_MISSED).stream()
                .collect(Collectors.toMap(adjustment -> adjustment.getDuty().getId(), Function.identity()));
    }

    /** 조회할 때 보는 지정 상태. 운영일이 지났는데 아직 지정 상태면 미이행이다. */
    private static DutyStatus recordStatus(VolunteerDuty duty, LocalDate today) {
        if (duty.getStatus() == DutyStatus.ASSIGNED && duty.getOperatingDay().isBefore(today)) {
            return DutyStatus.MISSED;
        }
        return duty.getStatus();
    }

    /** 미이행으로 처리한 지정은 완료·취소할 수 없다. 미이행 기록이 남아야 한다. */
    private static void rejectMissed(VolunteerDuty duty) {
        if (duty.getStatus() == DutyStatus.MISSED) {
            throw new CustomException(ErrorCode.DUTY_MISSED);
        }
    }

    private VolunteerDuty findTodayDuty(Long studentId, LocalDate today) {
        return volunteerDutyRepository.findByStudentIdAndOperatingDay(studentId, today)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_ON_DUTY));
    }

    /** 당일 봉사자 알림의 원본 키. 학생·운영일마다 알림이 하나다. */
    private static String dutyKey(LocalDate operatingDay) {
        return "duty:" + operatingDay;
    }

    /**
     * 학생 행을 잠가 남은 횟수를 보고 실제로 바꿀 횟수를 정한 뒤, 조정 기록을 먼저 남기고 횟수를 바꾼다.
     * 차감은 남은 횟수까지만 하고, 남은 횟수가 0이면 {@link ErrorCode#VOLUNTEER_COUNT_ZERO}(409)다.
     * 같은 키의 기록이 이미 있으면 재시도로 보고 횟수를 바꾸지 않는다. 잠금 덕분에 같은 키가 동시에 와도 한 번만 반영된다.
     * 그 키가 다른 학생이나 다른 요청 횟수에 쓰였으면 {@link ErrorCode#IDEMPOTENCY_KEY_REUSED}(409)다.
     */
    private VolunteerResponse adjust(Long memberId, Long studentId, String requestKey, int requestedDelta,
                                     String reason) {
        return adjust(memberId, studentId, requestKey, requestedDelta, reason, false);
    }

    private VolunteerResponse adjust(Long memberId, Long studentId, String requestKey, int requestedDelta,
                                     String reason, boolean consecutive) {
        adminVerifier.verify(memberId);
        Long id = findStudent(studentId).getId();
        String key = normalizeKey(requestKey);
        String savedReason = normalizeReason(reason);

        int count = (consecutive ? studentRepository.lockConsecutiveVolunteerCount(id)
                : studentRepository.lockVolunteerCount(id))
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        int delta = requestedDelta > 0 ? requestedDelta : -Math.min(-requestedDelta, count);

        if (delta == 0) {
            if (key == null || volunteerAdjustmentRepository.findByRequestKey(key).isEmpty()) {
                throw new CustomException(ErrorCode.VOLUNTEER_COUNT_ZERO);
            }
            verifySameRequest(key, id, requestedDelta, consecutive);
        } else if (volunteerAdjustmentRepository.insertIfAbsent(
                id, delta, requestedDelta, savedReason,
                (consecutive ? VolunteerAdjustmentKind.CONSECUTIVE_ADMIN : VolunteerAdjustmentKind.ADMIN).name(), key,
                clock.instant()) == 1) {
            if (consecutive) {
                studentRepository.changeConsecutiveVolunteerCount(id, delta);
            } else {
                studentRepository.changeVolunteerCount(id, delta);
            }
        } else {
            verifySameRequest(key, id, requestedDelta, consecutive);
        }

        return reload(id);
    }

    /**
     * 키가 겹쳐 기록되지 않았을 때, 기존 기록이 같은 학생·같은 요청 횟수의 재시도인지 확인한다.
     * 웹이 키를 다른 요청에 다시 쓰면 반영되지 않았는데 성공으로 보이지 않도록 409로 알린다.
     */
    private void verifySameRequest(String key, Long studentId, int requestedDelta, boolean consecutive) {
        boolean same = volunteerAdjustmentRepository.findByRequestKey(key)
                .map(existing -> existing.getStudent().getId().equals(studentId)
                        && existing.getRequestedDelta() == requestedDelta
                        && existing.getKind() == (consecutive ? VolunteerAdjustmentKind.CONSECUTIVE_ADMIN : VolunteerAdjustmentKind.ADMIN))
                .orElse(false);
        if (!same) {
            throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private Student findStudent(Long studentId) {
        return studentRepository.findByDatagsmStudentId(studentId)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
    }

    /** 일괄 수정 뒤 학생을 다시 읽어 명단 항목으로 만든다. */
    private VolunteerResponse reload(Long id) {
        Student updated = studentRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.STUDENT_NOT_FOUND));
        return toResponse(updated);
    }

    /** 학생 한 명의 명단 항목을 최근 활동·오늘 지정 상태와 함께 만든다. */
    private VolunteerResponse toResponse(Student student) {
        VolunteerDuty todayDuty = volunteerDutyRepository
                .findByStudentIdAndOperatingDay(student.getId(), operatingDayCalculator.today())
                .orElse(null);
        return VolunteerResponse.of(
                student, volunteerAdjustmentRepository.findLastActivityAt(student.getId()),
                todayDuty == null ? null : todayDuty.getStatus(), todayDuty != null && todayDuty.isConsecutive());
    }

    /** 사유의 앞뒤 공백을 지운다. 비어 있으면 {@code null}, 100자를 넘으면 {@link ErrorCode#INVALID_REQUEST}(400)다. */
    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String stripped = reason.strip();
        if (stripped.length() > MAX_REASON_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return stripped;
    }

    private static String normalizeKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            return null;
        }
        String key = requestKey.strip();
        if (key.length() > MAX_REQUEST_KEY_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return key;
    }

    /**
     * 검색어에 맞는 정도를 순위로 돌려주는 함수를 만든다. 순위가 낮을수록 앞에 오고, 맞지 않으면 {@link KoreanNameMatcher#NO_MATCH}다.
     */
    private static ToIntFunction<Student> ranker(String query) {
        if (query == null || query.isBlank()) {
            return student -> KoreanNameMatcher.CONTAINS;
        }
        String keyword = query.strip();
        if (DIGITS.matcher(keyword).matches()) {
            int number = Integer.parseInt(keyword);
            return student -> Objects.equals(student.getDormitoryRoom(), number) || student.getStudentNumber() == number
                    ? KoreanNameMatcher.CONTAINS
                    : KoreanNameMatcher.NO_MATCH;
        }
        return student -> KoreanNameMatcher.rank(student.getName(), keyword);
    }

    /** 연속 봉사 잔여 횟수를 일반 횟수와 독립적으로 조정한다. 출석 전에는 당일 지정을 만들지 않는다. */
    @Transactional
    public VolunteerResponse adjustConsecutiveCount(Long memberId, Long studentId, String key, int delta, String reason) {
        if (delta == 0 || delta < -MAX_ADJUST_AMOUNT || delta > MAX_ADJUST_AMOUNT) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        return adjust(memberId, studentId, key, delta, reason, true);
    }
}
