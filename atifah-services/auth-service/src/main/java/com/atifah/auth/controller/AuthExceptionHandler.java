package com.atifah.auth.controller;

import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证接口的异常补充处理。
 * common-web 的兜底 handler 会把请求体缺失 / JSON 非法当成未预期异常返回 500，
 * 契约要求返回 40000，因此在服务内优先处理该类框架异常，避免请求体进入日志。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = AuthController.class)
public class AuthExceptionHandler {

    /**
     * 请求体缺失或无法解析时返回 40000，不打印请求体。
     *
     * @param ex 请求体解析异常
     * @return 400 错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("auth request body unreadable");
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST));
    }
}
