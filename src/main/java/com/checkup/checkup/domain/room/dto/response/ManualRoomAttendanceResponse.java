package com.checkup.checkup.domain.room.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ManualRoomAttendanceResponse(
        @JsonProperty("student_id") Long studentId,
        @JsonProperty("attended") boolean attended
) {
}
