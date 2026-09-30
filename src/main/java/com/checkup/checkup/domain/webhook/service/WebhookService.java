package com.checkup.checkup.domain.webhook.service;

import com.checkup.checkup.domain.member.dto.StudentLeftEvent;
import com.checkup.checkup.domain.member.entity.Member;
import com.checkup.checkup.domain.member.entity.MemberRole;
import com.checkup.checkup.domain.member.entity.Student;
import com.checkup.checkup.domain.member.repository.StudentRepository;
import com.checkup.checkup.domain.webhook.dto.request.Change;
import com.checkup.checkup.domain.webhook.dto.request.WebhookEvent;
import com.checkup.checkup.domain.webhook.dto.request.WebhookStudent;
import com.checkup.checkup.domain.webhook.repository.WebhookEventLogRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 서명 검증을 통과한 DataGSM 웹훅 본문을 해석해 저장된 학생의 정보와 권한을 동기화한다.
 *
 * 로그에는 이벤트 ID·종류·학생 수만 남기고 본문과 학생 정보는 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final String STUDENT_UPDATED = "student.updated";
    private static final String GRADUATE = "GRADUATE";
    private static final String WITHDRAWN = "WITHDRAWN";

    private final ObjectMapper objectMapper;
    private final WebhookEventLogRepository webhookEventLogRepository;
    private final Clock clock;
    private final StudentRepository studentRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 웹훅 본문을 처리한다. {@code student.updated}가 아닌 이벤트와 이미 처리한 이벤트 ID는 무시한다.
     * 이벤트 ID 기록과 학생 반영은 한 트랜잭션이라, 반영이 실패하면 기록도 취소돼 재전송 때 다시 처리된다.
     *
     * @param body 서명 검증을 통과한 요청 본문 원문
     * @throws CustomException 본문을 읽을 수 없거나 필수값이 없으면 {@link ErrorCode#INVALID_WEBHOOK_PAYLOAD}(400)
     */
    @Transactional
    public void handle(byte[] body) {
        WebhookEvent event = parse(body);

        if (event.id() == null
                || event.event() == null
                || event.timestamp() == null
                || event.data() == null
                || event.data().after() == null) {
            throw new CustomException(ErrorCode.INVALID_WEBHOOK_PAYLOAD);
        }

        if (!STUDENT_UPDATED.equals(event.event())) {
            log.info("Ignored webhook event: id={}, event={}", event.id(), event.event());
            return;
        }

        if (webhookEventLogRepository.record(event.id(), clock.instant()) == 0) {
            log.info("Duplicate webhook event ignored: id={}", event.id());
            return;
        }

        // 생성·삭제 쪽의 빈 객체({})는 학생 ID가 없으므로 뺀다.
        List<WebhookStudent> students = event.data().after().stream()
                .map(Change::object)
                .filter(student -> student != null && student.studentId() != null)
                .toList();
        log.info("Received student.updated: id={}, students={}", event.id(), students.size());

        Map<Long, Student> saved = studentRepository.findAllByDatagsmStudentIdIn(
                        students.stream().map(WebhookStudent::studentId).toList())
                .stream()
                .collect(Collectors.toMap(Student::getDatagsmStudentId, Function.identity()));

        for (WebhookStudent changed : students) {
            Student student = saved.get(changed.studentId());
            // 로그인한 적 없는 학생이거나 이미 더 새로운 이벤트를 반영했으면 건너뛴다.
            if (student == null || student.isStale(event.timestamp())) {
                continue;
            }
            if (sync(student, changed)) {
                student.markSynced(event.timestamp());
            }
        }
    }

    /**
     * 학생 한 명의 변경을 반영한다. 관리자 권한은 요청마다 DB 역할로 확인하므로 기존 로그인 세션에도 바로 반영된다.
     *
     * 졸업·자퇴는 학년 등이 {@code null}로 오므로 학년·반·번호는 그대로 두고, 관리자 권한을 빼고 호실을 비운 뒤
     * {@link StudentLeftEvent}를 발행한다.
     * 재학생인데 필요한 값이 없으면 부분 데이터로 보고 건너뛴다.
     *
     * @return 반영했으면 {@code true}, 건너뛰었으면 {@code false}
     */
    private boolean sync(Student student, WebhookStudent changed) {
        Member member = student.getMember();

        if (GRADUATE.equals(changed.role()) || WITHDRAWN.equals(changed.role())) {
            member.update(member.getName(), MemberRole.STUDENT);
            student.leaveDormitory();
            eventPublisher.publishEvent(new StudentLeftEvent(student.getId(), changed.role()));
            log.info("Student left: studentId={}, role={}", changed.studentId(), changed.role());
            return true;
        }

        MemberRole role = toMemberRole(changed.role());
        if (role == null
                || changed.name() == null
                || changed.grade() == null
                || changed.classNum() == null
                || changed.number() == null
                || changed.studentNumber() == null) {
            log.warn("Skipped incomplete webhook student: studentId={}", changed.studentId());
            return false;
        }

        member.update(changed.name(), role);
        student.update(
                changed.studentId(),
                changed.grade(),
                changed.classNum(),
                changed.number(),
                changed.studentNumber(),
                changed.dormitoryRoom());
        return true;
    }

    /**
     * DataGSM 학생 role을 서비스 권한으로 바꾼다. 기숙사 자치위원만 관리자다.
     *
     * @return 알 수 없는 role이면 {@code null}
     */
    private static MemberRole toMemberRole(String role) {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case "DORMITORY_MANAGER" -> MemberRole.ADMIN;
            case "GENERAL_STUDENT", "STUDENT_COUNCIL" -> MemberRole.STUDENT;
            default -> null;
        };
    }

    /**
     * 본문을 이벤트로 읽는다. 예외 메시지에 본문 일부가 섞일 수 있어 원인 예외는 남기지 않는다.
     */
    private WebhookEvent parse(byte[] body) {
        try {
            return objectMapper.readValue(body, WebhookEvent.class);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INVALID_WEBHOOK_PAYLOAD);
        }
    }
}
