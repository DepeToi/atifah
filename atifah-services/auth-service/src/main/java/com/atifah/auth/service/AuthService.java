package com.atifah.auth.service;

import com.atifah.auth.dto.LoginRequest;
import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.vo.AuthToken;
import com.atifah.auth.vo.RegisterResult;

/**
 * 认证业务接口。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public interface AuthService {

    /**
     * 手机号注册。
     *
     * @param request 注册请求
     * @param idempotencyKey 幂等键，规范 UUID
     * @return 注册结果，仅包含全局用户 ID
     */
    RegisterResult register(RegisterRequest request, String idempotencyKey);

    /**
     * 手机号密码登录。
     *
     * @param request 登录请求
     * @return 访问令牌与刷新令牌
     */
    AuthToken login(LoginRequest request);
}
