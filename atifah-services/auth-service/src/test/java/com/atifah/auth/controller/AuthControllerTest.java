package com.atifah.auth.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atifah.auth.service.AuthService;
import com.atifah.auth.vo.AuthToken;
import com.atifah.auth.vo.RegisterResult;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口接口层测试，使用 standalone MockMvc 与全局异常处理器。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";

    private static final String PHONE = "13800005678";

    private static final String PASSWORD = "Passw0rd123";

    private static final String VALID_BODY = "{\"phone\":\"13800005678\",\"password\":\"Passw0rd123\"}";

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    private Logger exceptionHandlerLogger;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        exceptionHandlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        exceptionHandlerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        exceptionHandlerLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void registerSuccessReturnsUserId() throws Exception {
        when(authService.register(any(), eq(IDEMPOTENCY_KEY)))
                .thenReturn(new RegisterResult("1856000000000000001"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("1856000000000000001"));
    }

    @Test
    void registerWithoutIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verifyNoInteractions(authService);
    }

    @Test
    void registerWithInvalidPhoneReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content("{\"phone\":\"12345\",\"password\":\"Passw0rd123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verifyNoInteractions(authService);
    }

    @Test
    void registerWithWeakPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content("{\"phone\":\"13800005678\",\"password\":\"abcdefgh\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verifyNoInteractions(authService);
    }

    @Test
    void registerWithoutBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verifyNoInteractions(authService);
        assertNoSensitiveLog();
    }

    @Test
    void registerWithMalformedBodyReturnsBadRequestWithoutLoggingPayload() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content("{\"phone\":\"" + PHONE + "\",\"password\":\"" + PASSWORD + "\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verifyNoInteractions(authService);
        assertNoSensitiveLog();
    }

    @Test
    void registerDuplicatePhoneReturnsConflict() throws Exception {
        when(authService.register(any(), eq(IDEMPOTENCY_KEY)))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT.getCode(), "手机号已注册"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONFLICT.getCode()));
    }

    @Test
    void loginSuccessReturnsTokens() throws Exception {
        when(authService.login(any())).thenReturn(
                new AuthToken("1856000000000000001", "jwt-access-token", "refresh-token", "Bearer", 900, 604800));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value("1856000000000000001"))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.refreshExpiresIn").value(604800));
    }

    @Test
    void loginWithInvalidPhoneReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"12345\",\"password\":\"Passw0rd123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verifyNoInteractions(authService);
    }

    @Test
    void loginWithShortPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800005678\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verifyNoInteractions(authService);
    }

    @Test
    void loginWithoutBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.getCode()));

        verifyNoInteractions(authService);
        assertNoSensitiveLog();
    }

    @Test
    void loginWithBadCredentialsReturnsUnauthorized() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "手机号或密码错误"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    @Test
    void loginWithDisabledAccountReturnsForbidden() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN.getCode(), "账号已禁用"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void loginWhenLockedReturnsTooManyRequests() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS.getCode(), "登录失败次数过多，请稍后再试"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOO_MANY_REQUESTS.getCode()));
    }

    /**
     * 断言请求体解析异常日志不含手机号与密码明文。
     */
    private void assertNoSensitiveLog() {
        String logs = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertFalse(logs.contains(PHONE), "日志不应包含手机号");
        assertFalse(logs.contains(PASSWORD), "日志不应包含密码");
    }
}
