package com.atifah.auth.service;

/**
 * 刷新令牌服务：签发、撤销与哈希。
 *
 * @author DepeToi
 * @since 1.0.0
 */
public interface RefreshTokenService {

    /**
     * 签发刷新令牌并落库（仅存哈希）。
     *
     * @param userId 用户 ID
     * @return 原始刷新令牌
     */
    String issue(Long userId);

    /**
     * 撤销刷新令牌。
     *
     * @param rawToken 原始刷新令牌
     */
    void revoke(String rawToken);

    /**
     * 计算原始令牌的 SHA-256 十六进制哈希。
     *
     * @param rawToken 原始刷新令牌
     * @return 哈希值
     */
    String hash(String rawToken);
}
