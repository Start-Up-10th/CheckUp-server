package com.checkup.checkup.domain.face.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 카메라 프레임 한 장의 얼굴 인식 결과. 얼굴 이미지와 벡터는 담지 않는다. */
public record FaceFrameResponse(
        @Schema(description = "프레임 id. 요청의 X-Frame-Id, 없으면 서버가 만든 값", example = "frame-1")
        String frameId,
        @Schema(description = "프레임에서 찾은 얼굴 목록")
        List<Face> faces
) {
    public record Face(
            @Schema(description = "익명 얼굴 트랙 id. 같은 얼굴이 화면에 머무는 동안 같은 값이다")
            String trackId,
            @Schema(description = "정규화한 [x, y, width, height]. 각 값은 0..1 범위")
            List<Double> bbox,
            @Schema(description = "원본 프레임 픽셀 좌표의 랜드마크 [x, y] 목록")
            List<List<Double>> landmarks,
            Quality quality,
            Recognition recognition,
            @Schema(description = "이 얼굴 트랙의 연속 인식 시도 횟수(DEC-004)", example = "1")
            int attempts,
            @Schema(description = "true면 QR 출석 안내를 띄운다(3회 초과 실패, DEC-004)")
            boolean qrRecommended
    ) {
    }

    public record Quality(
            @Schema(description = "밝기 점수") double brightness,
            @Schema(description = "선명도 점수") double sharpness,
            @Schema(description = "품질 문제 목록. 문제가 없으면 빈 배열") List<String> issues
    ) {
    }

    public record Recognition(
            @Schema(description = "KNOWN, UNKNOWN, NOT_ATTEMPTED 중 하나")
            String status,
            @Schema(description = "학생 이름. status가 KNOWN일 때만 있다")
            String studentName,
            @Schema(description = "화면 표시용 학번. status가 KNOWN일 때만 있다")
            Integer studentNumber,
            @Schema(description = "RECORDED, DUPLICATE, STALE, REJECTED 중 하나. status가 KNOWN일 때만 있다")
            String attendance
    ) {
    }
}
