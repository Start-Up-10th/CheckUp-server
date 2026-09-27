package com.checkup.checkup.domain.qr.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.checkup.checkup.domain.attendance.entity.AttendancePurpose;

class QrPurposeTest {

    @Test
    void QR_용도는_같은_이름의_출석_용도로_바뀐다() {
        assertThat(QrPurpose.DORMITORY.toAttendancePurpose()).isEqualTo(AttendancePurpose.DORMITORY);
        assertThat(QrPurpose.STUDY_ROOM.toAttendancePurpose()).isEqualTo(AttendancePurpose.STUDY_ROOM);
    }

    @Test
    void 모든_QR_용도가_출석_용도를_가진다() {
        for (QrPurpose purpose : QrPurpose.values()) {
            assertThat(purpose.toAttendancePurpose()).isNotNull();
        }
    }
}
