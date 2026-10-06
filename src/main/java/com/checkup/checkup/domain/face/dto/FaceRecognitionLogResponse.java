package com.checkup.checkup.domain.face.dto;

import com.checkup.checkup.domain.face.entity.FaceRecognitionLog;
import com.checkup.checkup.domain.face.entity.FaceRecognitionResult;
import com.checkup.checkup.domain.member.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 관리자 얼굴 출석 화면의 최근 인식 한 줄(#143). 못 알아본 얼굴은 이름·학번이 {@code null}이다.
 */
public record FaceRecognitionLogResponse(
        @Schema(description = "인식한 시각") Instant recognizedAt,
        @Schema(description = "SUCCESS(출석 처리됨) 또는 FAILED(출석 처리 안 됨·못 알아봐 QR 안내)", example = "SUCCESS") FaceRecognitionResult result,
        @Schema(description = "학생 이름. 못 알아본 얼굴이면 null", example = "홍길동") String studentName,
        @Schema(description = "학번. 못 알아본 얼굴이면 null", example = "2105") Integer studentNumber
) {

    public static FaceRecognitionLogResponse from(FaceRecognitionLog log) {
        Student student = log.getStudent();
        return new FaceRecognitionLogResponse(
                log.getRecognizedAt(),
                log.getResult(),
                student == null ? null : student.getName(),
                student == null ? null : student.getStudentNumber());
    }
}
