package com.checkup.checkup.domain.webhook.repository;

import com.checkup.checkup.domain.webhook.entity.WebhookEventLog;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

/**
 * 처리한 웹훅 이벤트 ID를 기록한다.
 */
public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, String> {

    /**
     * 이벤트 ID를 기록한다. 확인과 저장을 한 문장으로 해서 동시에 온 재전송도 한 번만 기록된다.
     *
     * @param id         DataGSM 이벤트 ID
     * @param receivedAt 수신 시각
     * @return 새로 기록하면 1, 이미 처리한 이벤트면 0
     */
    @Modifying
    @Query(value = """
            INSERT INTO webhook_event (id, received_at)
            VALUES (:id, :receivedAt)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int record(@Param("id") String id, @Param("receivedAt") Instant receivedAt);

    /**
     * 기준 시각보다 먼저 받은 이벤트 기록을 지운다. 기준 시각과 같은 기록은 남긴다.
     *
     * @param before 기준 시각
     * @return 지운 기록 수
     */
    @Modifying
    @Query("DELETE FROM WebhookEventLog w WHERE w.receivedAt < :before")
    int deleteReceivedBefore(@Param("before") Instant before);
}
