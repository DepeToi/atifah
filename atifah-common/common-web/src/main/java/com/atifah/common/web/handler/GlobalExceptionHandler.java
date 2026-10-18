package com.atifah.common.web.handler;

import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.core.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.stream.Collectors;

/**
 * REST 全局异常处理，保证接口返回统一结构；服务端渲染页面不受影响。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    /**
     * 处理业务异常，映射为对应 HTTP 状态。
     *
     * @param ex 业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        log.warn("business exception: code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(resolveStatus(ex.getCode()))
                .body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理 @Valid 参数校验失败，汇总各字段错误信息。
     *
     * @param ex 参数校验异常
     * @return 400 错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), message));
    }

    /**
     * 处理方法参数约束校验失败。
     *
     * @param ex 约束校验异常
     * @return 400 错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage()));
    }

    /**
     * 处理不支持的请求方法。
     *
     * @param ex 请求方法异常
     * @return 405 错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 兜底处理未预期异常。
     *
     * @param ex 未预期异常
     * @return 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(ErrorCode.INTERNAL_ERROR));
    }

    /**
     * 将业务状态码映射为 HTTP 状态。
     *
     * @param code 业务状态码
     * @return 对应的 HTTP 状态，未命中时回退为 400
     */
    private HttpStatus resolveStatus(int code) {
        return switch (code) {
            case 40100 -> HttpStatus.UNAUTHORIZED;
            case 40300 -> HttpStatus.FORBIDDEN;
            case 40400 -> HttpStatus.NOT_FOUND;
            case 40900 -> HttpStatus.CONFLICT;
            case 42900 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
