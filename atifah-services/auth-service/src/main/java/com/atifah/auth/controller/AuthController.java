package com.atifah.auth.controller;

import com.atifah.auth.dto.LoginRequest;
import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.service.AuthService;
import com.atifah.auth.vo.AuthTokenVO;
import com.atifah.auth.vo.RegisterResponse;
import com.atifah.common.core.constant.CommonConstants;
import com.atifah.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册与登录。
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
     * 注册认证账号。
     *
     * @param idempotencyKey 幂等键（必填，UUID）
     * @param request        注册请求
     * @return 全局 userId
     */
    @PostMapping("/register")
    public Result<RegisterResponse> register(
            @RequestHeader(value = CommonConstants.HEADER_IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request, idempotencyKey));
    }

    /**
     * 手机号 + 密码登录。
     *
     * @param request 登录请求
     * @return 登录令牌
     */
    @PostMapping("/login")
    public Result<AuthTokenVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }
}
