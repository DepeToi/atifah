package com.atifah.common.core.result;

import com.atifah.common.core.enums.ErrorCode;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应结果。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码，0 表示成功。 */
    private final int code;

    /** 提示消息。 */
    private final String message;

    /** 业务数据。 */
    private final T data;

    /** 响应时间戳（毫秒）。 */
    private final long timestamp;

    /**
     * 私有构造，构建统一响应结果。
     *
     * @param code 业务状态码
     * @param message 提示消息
     * @param data 业务数据
     */
    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 构建成功响应，无业务数据。
     *
     * @return 成功响应结果
     */
    public static <T> Result<T> ok() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    /**
     * 构建成功响应。
     *
     * @param data 业务数据
     * @return 成功响应结果
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /**
     * 按错误码构建失败响应。
     *
     * @param errorCode 错误码
     * @return 失败响应结果
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 按自定义状态码与消息构建失败响应。
     *
     * @param code 业务状态码
     * @param message 提示消息
     * @return 失败响应结果
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 是否成功。
     *
     * @return true 表示成功；false 表示失败
     */
    public boolean isSuccess() {
        return code == ErrorCode.SUCCESS.getCode();
    }
}
