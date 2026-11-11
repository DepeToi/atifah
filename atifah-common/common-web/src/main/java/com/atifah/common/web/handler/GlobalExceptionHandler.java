package com.atifah.common.web.handler;

import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.core.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Objects;
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
        return badRequest(joinFieldErrors(ex.getBindingResult().getFieldErrors()));
    }

    /**
     * 处理表单 / 查询参数绑定失败，汇总各字段错误信息。
     *
     * @param ex 绑定异常
     * @return 400 错误响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException ex) {
        return badRequest(joinFieldErrors(ex.getBindingResult().getFieldErrors()));
    }

    /**
     * 处理方法参数约束校验失败。
     *
     * @param ex 约束校验异常
     * @return 400 错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return badRequest(ex.getMessage());
    }

    /**
     * 处理请求体缺失或 JSON 无法解析；日志不记录请求体，避免敏感明文。
     *
     * @param ex 请求体解析异常
     * @return 400 错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("request body unreadable");
        return badRequest("请求体缺失或格式错误");
    }

    /**
     * 处理缺少必需请求头。
     *
     * @param ex 缺少请求头异常
     * @return 400 错误响应
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        log.warn("missing request header: {}", ex.getHeaderName());
        return badRequest("缺少请求头：" + ex.getHeaderName());
    }

    /**
     * 处理缺少必需请求参数。
     *
     * @param ex 缺少请求参数异常
     * @return 400 错误响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex) {
        log.warn("missing request parameter: {}", ex.getParameterName());
        return badRequest("缺少请求参数：" + ex.getParameterName());
    }

    /**
     * 处理参数类型转换失败。
     *
     * @param ex 参数类型不匹配异常
     * @return 400 错误响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("parameter type mismatch: {}", ex.getName());
        return badRequest("参数类型不正确：" + ex.getName());
    }

    /**
     * 处理方法参数校验失败（Spring 6.1+ 的 @RequestParam / @PathVariable 校验）。
     *
     * @param ex 方法参数校验异常
     * @return 400 错误响应
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        log.warn("method validation failed");
        return badRequest(ErrorCode.BAD_REQUEST.getMessage());
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
     * 汇总字段校验消息；没有消息时回退为通用参数错误提示。
     *
     * @param fieldErrors 字段错误列表
     * @return 可返回给客户端的提示
     */
    private String joinFieldErrors(Iterable<FieldError> fieldErrors) {
        String message = java.util.stream.StreamSupport.stream(fieldErrors.spliterator(), false)
                .filter(Objects::nonNull)
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("; "));
        return message.isBlank() ? ErrorCode.BAD_REQUEST.getMessage() : message;
    }

    /**
     * 构建 400 参数错误响应。
     *
     * @param message 提示信息；为空时使用通用提示
     * @return 400 错误响应
     */
    private ResponseEntity<Result<Void>> badRequest(String message) {
        String resolved = (message == null || message.isBlank())
                ? ErrorCode.BAD_REQUEST.getMessage()
                : message;
        return ResponseEntity.badRequest().body(Result.fail(ErrorCode.BAD_REQUEST.getCode(), resolved));
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
