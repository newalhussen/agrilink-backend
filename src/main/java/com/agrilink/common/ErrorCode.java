package com.agrilink.common;

import org.springframework.http.HttpStatus;

/** Machine-readable error codes. Mobile and web clients switch on these, never on message text. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN),
    PHONE_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    OTP_INVALID(HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),
    OTP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),
    LISTING_UNAVAILABLE(HttpStatus.CONFLICT),
    INVALID_HANDOVER_CODE(HttpStatus.BAD_REQUEST),
    HANDOVER_LOCKED(HttpStatus.LOCKED),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED),
    INSUFFICIENT_FUNDS(HttpStatus.CONFLICT),
    FILE_REJECTED(HttpStatus.BAD_REQUEST),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
