package com.atifah.auth.controller;

import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.service.AuthService;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口参数校验与异常映射测试。
 */
class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void register_invalidPhone_returns40000() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", "6f8f8e1e-5c5f-4a5f-9f0e-1e1e1e1e1e1e")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"12345\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void register_invalidPassword_returns40000() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", "6f8f8e1e-5c5f-4a5f-9f0e-1e1e1e1e1e1e")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void register_missingIdempotencyKey_returns40000() throws Exception {
        when(authService.register(any(RegisterRequest.class), isNull()))
                .thenThrow(new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "缺少 Idempotency-Key"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("缺少 Idempotency-Key"));
    }

    @Test
    void login_invalidPhone_returns40000() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"abc\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
