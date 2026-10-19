package com.atifah.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 登录成功返回的令牌信息。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public class AuthTokenVO {

    /** JWT access token。 */
    private final String accessToken;

    /** 不透明 refresh token，仅返回一次，客户端需安全存储。 */
    private final String refreshToken;

    /** 令牌类型。 */
    private final String tokenType;

    /** access token 有效期（秒）。 */
    private final long expiresIn;

    /** 全局用户 ID（字符串返回）。 */
    private final String userId;
}
