package com.checkup.checkup.domain.room.dto.request;

import jakarta.validation.constraints.NotNull;

public record ManualRoomAttendanceRequest(@NotNull Boolean attended) {
}
