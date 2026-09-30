package com.checkup.checkup.domain.face.controller;

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
@RestController
@RequestMapping("/api/v1/face/sessions")
@RequiredArgsConstructor
public class FaceRecognitionController {
    private final FaceRecognitionService faceRecognitionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FaceSessionResponse create(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody FaceSessionCreateRequest request
    ) {
        return faceRecognitionService.create(memberId, request.purpose());
    }

    @PostMapping(value = "/{sessionId}/frames", consumes = {"image/jpeg", "image/webp"})
    public FaceFrameResponse recognize(
            @AuthenticationPrincipal Long memberId,
            @PathVariable UUID sessionId,
            @RequestHeader(name = "X-Frame-Id", required = false) String frameId,
            @RequestHeader(name = "Content-Type") String contentType,
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

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@AuthenticationPrincipal Long memberId, @PathVariable UUID sessionId) {
        faceRecognitionService.close(memberId, sessionId);
    }
}
