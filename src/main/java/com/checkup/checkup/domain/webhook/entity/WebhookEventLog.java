package com.checkup.checkup.domain.webhook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 처리한 DataGSM 웹훅 이벤트 ID. 같은 이벤트의 재전송을 거른다.
 *
 * 저장은 {@link com.checkup.checkup.domain.webhook.repository.WebhookEventLogRepository#record}로만 한다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "webhook_event")
@Entity
public class WebhookEventLog {

    @Id
    private String id;

    @Column(nullable = false)
    private Instant receivedAt;
}
