-- ============================================================
-- auth-service 初始化 DDL
-- 与 docs/contracts/db/auth-service.sql 保持一致（逻辑 Schema 契约）
-- 已执行脚本禁止修改；后续变更新增 V2__xxx.sql
-- ============================================================

CREATE TABLE t_auth_account (
    id            BIGINT UNSIGNED NOT NULL COMMENT '认证账号主键（雪花 ID）',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '全局用户 ID（雪花 ID，逻辑外键）',
    phone         VARCHAR(20)     NOT NULL COMMENT '登录手机号；展示脱敏，日志禁止打印',
    password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密码哈希（禁止存明文/可逆加密）',
    status        TINYINT         NOT NULL DEFAULT 0 COMMENT '账号状态：0 正常，1 禁用',
    last_login_at DATETIME(3)     NULL COMMENT '最后登录时间（登录成功后更新）',
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
