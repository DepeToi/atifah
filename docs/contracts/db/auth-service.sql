-- ============================================================
-- 数据库契约：auth-service
-- 契约版本：0.1.0
-- 负责人：Claude（架构）
-- 对应任务：T-001（注册 + 登录）、T-002（刷新 + 登出）
--
-- 实现要求：
--   1. Flyway 脚本放 atifah-services/auth-service/src/main/resources/db/migration/V1__init.sql
--   2. 已执行脚本禁止修改；后续变更新增 V2__xxx.sql 并同步本文件
--   3. 本文件是逻辑 Schema 契约，与 Flyway 脚本必须一致
-- ============================================================
--
-- 边界说明：
--   - 本表只属于 atifah_auth 库；禁止跨库 JOIN。
--   - user_id 是跨服务全局用户 ID（雪花），逻辑外键，不建数据库外键。
--   - 用户昵称/头像/隐私设置归 user-service，不在本表。
--   - refresh token 只存 Redis（CacheKeys.AUTH_REFRESH），不落库。
--   - 手机号、密码哈希属于敏感数据：日志、异常、MQ 中禁止输出明文。
--   - 逻辑删除后手机号仍被唯一索引占用；注销后立即复用手机号需另行设计。
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

-- T-001 索引使用说明：
--   - 登录按 phone 精确查询，使用 uk_auth_account_phone。
--   - 注册并发由 uk_auth_account_phone 兜底，捕获 DuplicateKeyException。
--   - user_id 唯一索引保证一个全局用户只有一条认证账号。
--   - 暂无列表/统计查询，不额外建 created_at 索引。
