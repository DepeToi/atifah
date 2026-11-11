package com.atifah.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 刷新与登出共用的请求体。
 * 原始令牌为敏感凭据，禁止打印到日志。
 *
 * @param refreshToken 登录或刷新返回的不透明刷新令牌
 * @author DepeToi
 * @since 1.0.0
 */
public record RefreshTokenRequest(

        @NotBlank(message = "刷新令牌不能为空")
        @Size(min = 43, max = 128, message = "刷新令牌格式不正确")
        String refreshToken) {
}
