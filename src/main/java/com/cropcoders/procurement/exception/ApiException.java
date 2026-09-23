package com.cropcoders.procurement.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * An expected error returned to the browser as JSON.
 * The code (e.g. "OTP_INVALID") lets farmer pages show the message in the farmer's language;
 * params fill placeholders such as {left} or {seconds}.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> params;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> params) {
        super(message);
        this.status = status;
        this.code = code;
        this.params = params;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
