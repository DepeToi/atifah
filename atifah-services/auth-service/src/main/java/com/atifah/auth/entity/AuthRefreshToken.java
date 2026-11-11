package com.atifah.auth.entity;

import com.atifah.common.mybatis.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 刷新令牌实体，对应表 t_auth_refresh_token。
 * 只保存令牌的 SHA-256 哈希，原始令牌仅在签发时返回客户端（ADR 0003）。
 *
 * @author DepeToi
 * @since 1.0.0
 */
@Getter
@Setter
public class AuthRefreshToken extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 全局用户 ID（雪花 ID，逻辑外键）。 */
    private Long userId;

    /** Refresh Token 的 SHA-256 十六进制哈希，禁止存明文。 */
    private String tokenHash;

    /** 过期时间：签发时间 + 配置的有效期。 */
    private LocalDateTime expiresAt;

    /** 撤销时间；null 表示未撤销。 */
    private LocalDateTime revokedAt;
}
