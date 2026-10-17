package com.microhoov.common.core.exception;

import com.microhoov.common.core.enums.ErrorCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常，由全局异常处理器统一转换为 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.BAD_REQUEST.getCode();
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
