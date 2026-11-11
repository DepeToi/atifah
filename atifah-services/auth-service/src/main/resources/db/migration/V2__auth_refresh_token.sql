-- ============================================================
-- auth-service V2：刷新令牌表
-- 与 docs/contracts/db/auth-service.sql 的 t_auth_refresh_token 保持一致
-- 已执行脚本禁止修改；后续变更新增 V3__xxx.sql
-- ============================================================

CREATE TABLE t_auth_refresh_token (
    id         BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花 ID）',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '全局用户 ID（雪花 ID，逻辑外键）',
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Refresh Token 的 SHA-256 十六进制哈希（小写），禁止存明文',
    expires_at DATETIME(3)     NOT NULL COMMENT '过期时间（签发时 + 7 天）',
    revoked_at DATETIME(3)     NULL COMMENT '撤销时间；NULL 表示未撤销',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted    TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_refresh_token_hash (token_hash),
    KEY idx_auth_refresh_token_user (user_id, revoked_at, expires_at),
    KEY idx_auth_refresh_token_expires_at (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '刷新令牌：哈希、有效期与撤销状态';
