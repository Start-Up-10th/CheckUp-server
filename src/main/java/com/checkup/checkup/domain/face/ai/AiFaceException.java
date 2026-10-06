package com.checkup.checkup.domain.face.ai;

/** AI 서버 호출 실패. 응답 본문과 요청 데이터는 담지 않는다. */
public class AiFaceException extends RuntimeException {
    private final int status;
    private final String operation;
    private final String errorCode;

    public AiFaceException(int status, String operation, String errorCode) {
        super("Face AI request failed");
        this.status = status;
        this.operation = operation;
        this.errorCode = errorCode;
    }

    public int getStatus() {
        return status;
    }

    public String getOperation() {
        return operation;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
