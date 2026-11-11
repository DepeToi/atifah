package com.atifah.common.web.handler;

import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * 全局异常处理的框架级 400 映射测试。
 *
 * @author DepeToi
 * @since 1.0.0
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void messageNotReadableReturnsBadRequest() {
        ResponseEntity<Result<Void>> response =
                handler.handleMessageNotReadable(new HttpMessageNotReadableException("bad body"));

        assertBadRequest(response, "请求体缺失或格式错误");
    }

    @Test
    void missingHeaderReturnsBadRequest() {
        MethodParameter parameter = mock(MethodParameter.class);

        ResponseEntity<Result<Void>> response =
                handler.handleMissingRequestHeader(new MissingRequestHeaderException("Idempotency-Key", parameter));

        assertBadRequest(response, "缺少请求头：Idempotency-Key");
    }

    @Test
    void missingParameterReturnsBadRequest() {
        ResponseEntity<Result<Void>> response = handler.handleMissingServletRequestParameter(
                new MissingServletRequestParameterException("page", "int"));

        assertBadRequest(response, "缺少请求参数：page");
    }

    @Test
    void typeMismatchReturnsBadRequest() {
        MethodParameter parameter = mock(MethodParameter.class);

        ResponseEntity<Result<Void>> response = handler.handleMethodArgumentTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Integer.class, "page", parameter, null));

        assertBadRequest(response, "参数类型不正确：page");
    }

    @Test
    void bindExceptionReturnsFieldMessage() {
        BindException ex = new BindException(new Object(), "request");
        ex.getBindingResult().addError(new FieldError("request", "phone", "手机号格式不正确"));

        ResponseEntity<Result<Void>> response = handler.handleBindException(ex);

        assertBadRequest(response, "手机号格式不正确");
    }

    private void assertBadRequest(ResponseEntity<Result<Void>> response, String expectedMessage) {
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Result<Void> body = response.getBody();
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), body.getCode());
        assertEquals(expectedMessage, body.getMessage());
    }
}
