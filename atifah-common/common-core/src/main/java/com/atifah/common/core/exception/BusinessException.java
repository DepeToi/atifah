package com.atifah.common.core.exception;

import com.atifah.common.core.enums.ErrorCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常，由全局异常处理器统一转换为 Result。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码。 */
    private final int code;

    /**
     * 使用默认错误码构建业务异常。
     *
     * @param message 提示消息
     */
    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.BAD_REQUEST.getCode();
    }

    /**
     * 按错误码构建业务异常。
     *
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 按自定义状态码与消息构建业务异常。
     *
     * @param code 业务状态码
     * @param message 提示消息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
