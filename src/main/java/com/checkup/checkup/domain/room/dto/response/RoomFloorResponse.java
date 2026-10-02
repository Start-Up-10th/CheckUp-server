package com.checkup.checkup.domain.room.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RoomFloorResponse(
        @JsonProperty("dormitory_room") Integer dormitoryRoom,
        @JsonProperty("student_count") int studentCount,
        @JsonProperty("attended_count") int attendedCount
) {
}
