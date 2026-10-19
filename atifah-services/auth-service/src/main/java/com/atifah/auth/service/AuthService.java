package com.atifah.auth.service;

import com.atifah.auth.dto.LoginRequest;
import com.atifah.auth.dto.RegisterRequest;
import com.atifah.auth.vo.AuthTokenVO;
import com.atifah.auth.vo.RegisterResponse;

/**
 * 认证服务：注册与登录。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public interface AuthService {

    /**
     * 注册认证账号。
     *
     * @param request        注册请求
     * @param idempotencyKey 幂等键（必填，UUID）
     * @return 注册结果（全局 userId）
     */
    RegisterResponse register(RegisterRequest request, String idempotencyKey);

    /**
     * 手机号 + 密码登录。
     *
     * @param request 登录请求
     * @return 登录令牌
     */
    AuthTokenVO login(LoginRequest request);
}
