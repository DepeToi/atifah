package com.atifah.auth.controller;

import com.atifah.auth.dto.LoginRequest;
import com.atifah.auth.dto.RefreshTokenRequest;
import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.service.AuthService;
import com.atifah.auth.vo.AuthToken;
import com.atifah.auth.vo.RegisterResult;
import com.atifah.common.core.constant.CommonConstants;
import com.atifah.common.core.enums.ErrorCode;
import com.atifah.common.core.exception.BusinessException;
import com.atifah.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：手机号注册、登录、刷新与登出。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 手机号注册，必须携带 Idempotency-Key。
     *
     * @param idempotencyKey 幂等键，规范 UUID
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<RegisterResult> register(
            @RequestHeader(value = CommonConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RegisterRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "Idempotency-Key 缺失或格式非法");
        }
        return Result.ok(authService.register(request, idempotencyKey));
    }

    /**
     * 手机号密码登录，不要求 Idempotency-Key。
     *
     * @param request 登录请求
     * @return 访问令牌与刷新令牌
     */
    @PostMapping("/login")
    public Result<AuthToken> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 刷新令牌并轮换，不要求 Idempotency-Key。
     *
     * @param request 刷新请求
     * @return 轮换后的访问令牌与刷新令牌
     */
    @PostMapping("/refresh")
    public Result<AuthToken> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.ok(authService.refresh(request));
    }

    /**
     * 登出并撤销刷新令牌，幂等，不要求 Idempotency-Key。
     *
     * @param request 登出请求
     * @return 成功响应
     */
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return Result.ok();
    }
}
