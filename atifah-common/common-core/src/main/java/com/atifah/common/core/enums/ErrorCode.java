package com.atifah.common.core.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 统一错误码。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "OK"),
    BAD_REQUEST(40000, "请求参数错误"),
    UNAUTHORIZED(40100, "未认证或登录已过期"),
    FORBIDDEN(40300, "无访问权限"),
    NOT_FOUND(40400, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, "请求方法不支持"),
    CONFLICT(40900, "数据冲突"),
    TOO_MANY_REQUESTS(42900, "请求过于频繁"),
    INTERNAL_ERROR(50000, "系统繁忙，请稍后重试");

    /** 错误码。 */
    private final int code;

    /** 错误消息。 */
    private final String message;
}
