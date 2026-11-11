-- ============================================================
-- 数据库契约（MySQL 8.0）
-- 服务：atifah-auth
-- 逻辑库：atifah_auth
-- 契约版本：0.1.0
-- 负责人：Claude（架构）
--
-- 说明：
--   1. 本文件是逻辑 Schema 契约；实际迁移脚本放
--      atifah-services/auth-service/src/main/resources/db/migration/。
--   2. t_auth_account 与现有 V1__init.sql 的逻辑结构一致；t_auth_refresh_token 为新增表，
--      实现阶段用 V2 迁移追加，禁止修改已执行的 Flyway 脚本。
--   3. 手机号按 SEC-01「个人敏感字段」处理：明文存储于 phone 列并建唯一索引，
--      接口与日志必须脱敏；不落 *_cipher。决策见 docs/adr/0003-auth-baseline.md。
--   4. Refresh Token 只存 SHA-256 十六进制哈希，禁止存明文令牌；会话以本表为唯一真相源。
-- ============================================================

CREATE TABLE t_auth_account (
    id            BIGINT UNSIGNED NOT NULL COMMENT '主键（雪花 ID）',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '全局用户 ID（雪花 ID，逻辑外键）',
    phone         VARCHAR(20)     NOT NULL COMMENT '登录手机号；明文存储，接口与日志必须脱敏（ADR 0003）',
    password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码哈希（禁止明文/可逆加密）',
    status        TINYINT         NOT NULL DEFAULT 0 COMMENT '账号状态：0 正常，1 禁用',
    last_login_at DATETIME(3)     NULL COMMENT '最后登录成功时间',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted       TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_account_phone (phone),
    UNIQUE KEY uk_auth_account_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '认证账号：登录标识、密码哈希与账号状态';

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
