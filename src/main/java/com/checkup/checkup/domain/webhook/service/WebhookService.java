package com.checkup.checkup.domain.webhook.service;

import com.checkup.checkup.domain.webhook.dto.StudentSyncData;
import com.checkup.checkup.domain.member.service.StudentSyncService;
import com.checkup.checkup.domain.webhook.dto.request.Change;
import com.checkup.checkup.domain.webhook.dto.request.WebhookEvent;
import com.checkup.checkup.domain.webhook.dto.request.WebhookStudent;
import com.checkup.checkup.domain.webhook.repository.WebhookEventLogRepository;
import com.checkup.checkup.global.exception.CustomException;
import com.checkup.checkup.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;

/**
 * 서명 검증을 통과한 DataGSM 웹훅 본문을 해석해 {@link StudentSyncService}로 학생의 정보와 권한을 동기화한다.
 *
 * 로그에는 이벤트 ID·종류·학생 수만 남기고 본문과 학생 정보는 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final String STUDENT_UPDATED = "student.updated";

    private final ObjectMapper objectMapper;
    private final WebhookEventLogRepository webhookEventLogRepository;
    private final Clock clock;
    private final StudentSyncService studentSyncService;

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
        List<StudentSyncData> students = event.data().after().stream()
                .map(Change::object)
                .filter(student -> student != null && student.studentId() != null)
                .map(WebhookStudent::toSyncData)
                .toList();
        log.info("Received student.updated: id={}, students={}", event.id(), students.size());

        studentSyncService.syncAll(students, event.timestamp());
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
