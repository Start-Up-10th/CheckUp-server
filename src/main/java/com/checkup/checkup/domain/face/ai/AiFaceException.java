package com.checkup.checkup.domain.face.ai;

/** An upstream failure without its response body or request data. */
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
