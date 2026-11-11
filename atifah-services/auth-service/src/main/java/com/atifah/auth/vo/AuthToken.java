package com.atifah.auth.vo;

/**
 * 登录令牌结果。
 * userId 以字符串返回，避免雪花 ID 在 JavaScript 中丢失精度；
 * refreshToken 为原始令牌，服务端只存其哈希。
 *
 * @param userId 全局用户 ID
 * @param accessToken JWT 访问令牌
 * @param refreshToken 不透明刷新令牌
 * @param tokenType 令牌类型，固定 Bearer
 * @param expiresIn Access Token 有效秒数
 * @param refreshExpiresIn Refresh Token 有效秒数
 * @author DepeToi
 * @since 1.0.0
 */
public record AuthToken(
        String userId,
        String accessToken,
        String refreshToken,
        String tokenType,
        int expiresIn,
        int refreshExpiresIn) {
}
