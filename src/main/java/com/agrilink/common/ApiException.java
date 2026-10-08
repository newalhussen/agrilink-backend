package com.agrilink.common;

/** Business-rule failure translated to a structured JSON error by {@link GlobalExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.BAD_REQUEST, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.ACCESS_DENIED, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }

    public static ApiException invalidTransition(String message) {
        return new ApiException(ErrorCode.INVALID_STATE_TRANSITION, message);
    }
}
