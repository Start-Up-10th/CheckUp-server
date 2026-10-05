package com.checkup.checkup.domain.face.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.checkup.checkup.domain.face.dto.FaceFrameResponse;
import com.checkup.checkup.domain.face.dto.FaceSessionCreateRequest;
import com.checkup.checkup.domain.face.dto.FaceSessionResponse;
import com.checkup.checkup.domain.face.service.FaceRecognitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Arrays;
import java.util.UUID;

/** Administrator camera endpoints. Browser SESSION is never forwarded to FastAPI. */
@Tag(name = "얼굴 인식(관리자 카메라)", description = "관리자 카메라 화면의 얼굴 인식 세션. 브라우저 SESSION은 얼굴 인식 서버로 넘기지 않는다.")
@RestController
@RequestMapping("/api/v1/face/sessions")
@RequiredArgsConstructor
public class FaceRecognitionController {
    private final FaceRecognitionService faceRecognitionService;

    @Operation(summary = "얼굴 인식 세션 생성", description = "카메라 페이지마다 새 세션을 만든다. 성공 201.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FaceSessionResponse create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody FaceSessionCreateRequest request
    ) {
        return faceRecognitionService.create(memberId, request.purpose());
    }

    @Operation(summary = "프레임 얼굴 인식", description = "본문은 image/jpeg 또는 image/webp 원본 바이트다. X-Frame-Id 헤더는 선택이다. 받은 이미지는 처리 뒤 메모리에서 지운다.")
    @PostMapping(value = "/{sessionId}/frames", consumes = {"image/jpeg", "image/webp"})
    public FaceFrameResponse recognize(
            @AuthenticationPrincipal Long memberId,
            @Parameter(description = "얼굴 인식 세션 생성 응답의 세션 id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID sessionId,
            @Parameter(description = "프레임 id(선택, 최대 128자). 없으면 서버가 만든다. 응답의 frameId로 돌아온다", example = "frame-1") @RequestHeader(name = "X-Frame-Id", required = false) String frameId,
            @Parameter(description = "image/jpeg 또는 image/webp", example = "image/jpeg") @RequestHeader(name = "Content-Type") String contentType,
            @RequestBody byte[] image
    ) {
        try {
            return faceRecognitionService.recognize(memberId, sessionId, frameId, contentType, image);
        } finally {
            if (image != null) {
                Arrays.fill(image, (byte) 0);
            }
        }
    }

    @Operation(summary = "얼굴 인식 세션 종료", description = "이 페이지의 세션만 종료한다. 성공 204.")
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@AuthenticationPrincipal Long memberId, @Parameter(description = "얼굴 인식 세션 생성 응답의 세션 id", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") @PathVariable UUID sessionId) {
        faceRecognitionService.close(memberId, sessionId);
    }
}
