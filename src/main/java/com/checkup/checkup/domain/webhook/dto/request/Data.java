package com.checkup.checkup.domain.webhook.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 웹훅 이벤트의 변경 목록. 내부 DB와 비교하므로 변경 후({@code new}) 목록만 받는다.
 *
 * @param after 변경 후 목록. JSON 키 {@code new}는 Java 예약어라 이름을 바꿔 받는다.
 */
public record Data(
        @JsonProperty("new") List<Change> after
) {
}
