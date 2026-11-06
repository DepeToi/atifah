# 0005. 配置项密文（ENC）与主密钥管理

- 状态：已采纳
- 日期：2026-11-06
- 决策人：项目维护者（Claude 起草）

## 背景

P0-2 解决的是“凭据不硬编码”，但没有解决“真实凭据一旦写入仓库或 Nacos 就是明文”。SEC-02 要求密钥与凭据只走环境变量，但数据库/Redis 口令、JWT 密钥在配置中心场景下仍可能被写入 Nacos 或 `application.yml`，仅靠 `${ENV:default}` 无法覆盖“配置中心里必须存值”的情况。

`common-security` 已在 2026-11-01 实现配置密文能力，但缺少 ADR、规则条款与迁移决策，属于未批准的公共能力。本 ADR 批准“能力 + 规则”，并明确迁移边界。

## 决策

### 1. 加密格式与算法

- 配置值写成 `ENC(base64(iv || ciphertext || tag))`。
- 算法：AES-256-GCM，12 字节随机 IV，128 位认证标签；纯 JDK 实现，不引入 Jasypt 等新依赖。
- 主密钥：环境变量 `CONFIG_ENC_KEY`，每个环境独立生成（32 字节随机数，base64url 无填充）；经 SHA-256 派生为 32 字节 AES 密钥。主密钥不得写入仓库、Nacos、日志或 CI 日志。
- 每次加密使用新 IV，同一明文多次加密结果不同；解密失败（密钥错误、密文篡改或截断）直接抛异常，不返回密文兜底。

### 2. 装载时序与写法

- `ConfigCryptoEnvironmentPostProcessor` 以 `Ordered.LOWEST_PRECEDENCE` 注册于 `META-INF/spring.factories`，在 config data（本地 yml + Nacos 导入）装载完成后执行。
- 置顶属性源 `ConfigCryptoPropertySource` 在读取时回查实时属性源并解密，不持有快照，Nacos 刷新后仍生效。
- 支持两种写法：
  - 直写：`password: "ENC(...)"`；
  - 环境变量注入：`password: ${MYSQL_PASSWORD:}` 且 `MYSQL_PASSWORD=ENC(...)`；也支持 `${ENV:ENC(...)}` 默认值。
- 嵌套占位符（`${A:${B}}`）不支持，禁止在密文配置中使用。

### 3. 失败策略

- 配置中不存在 `ENC(...)` 且未设置主密钥：不做任何改动，本地开发与测试零成本。
- 配置中存在 `ENC(...)`（含占位符默认值或环境变量解析后的密文）但缺少 `CONFIG_ENC_KEY`：启动失败，提示环境变量名，避免带着密文静默运行。
- 解密失败：启动失败；异常信息不包含密文与密钥。

### 4. 迁移范围（D-1）

- 本 ADR 只批准“能力 + 规则”，不批量迁移现有配置。
- 当且仅当真实凭据需要写入仓库或 Nacos 时，才使用 `ENC(...)`：JWT 密钥、数据库口令、Redis 口令、第三方 AK/SK。
- 现有 `${ENV:default}` 写法继续有效；本地默认值可为假值，生产必须通过环境变量或密文覆盖。
- 明确不迁移：`spring.cloud.nacos.*.password`（Nacos 客户端在 config data 装载期即使用，早于解密器）、`NACOS_AUTH_TOKEN`（Nacos 服务端启动参数）、`spring.datasource.username` / 主机端口（非密钥）。
- 迁移工作另立任务包（T-012），明确允许路径与验收；本 ADR 不修改 `deploy/nacos/config/**`。

### 5. 密钥管理与暴露面

- `CONFIG_ENC_KEY` 只通过环境变量注入；同名 JVM system property 仅限本地调试，生产禁用。
- 主密钥轮换：全量重新加密并同批发布；无 key id / 多密钥并存，旧密钥解不开时 fail-fast，无灰度窗口。
- 解密后的值在内存中为明文；生产不得暴露 `/actuator/env` 等会回显配置值的端点，日志与异常不得打印明文。
- 离线工具 `ConfigEncryptTool` 仅用于本地/发布机生成与校验；`decrypt` 会输出明文，禁止在 CI 日志或共享终端使用。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| Jasypt | 生态成熟、配置简单 | 新增依赖；默认算法与密钥派生需额外审计；违反“新增依赖先确认” | 否决 |
| Nacos 内置加密插件 | 与配置中心集成 | 依赖 Nacos 插件版本，非纯 JDK；本地与容器行为可能不一致 | 否决 |
| 只靠环境变量 | 零代码 | 配置中心场景无法避免明文入库，无法满足 SEC-02 的“入库即密文” | 否决 |
| 自研 AES-256-GCM（本 ADR） | 纯 JDK、无新依赖、行为可控、可 fail-fast | 需自行维护密钥轮换与离线工具 | 采纳 |

## 影响

- 正面：配置中心可安全存放密文；本地开发零成本；缺失密钥时 fail-fast；无新增依赖。
- 负面/代价：主密钥轮换需全量重加密；无 key id；`/actuator/env` 暴露面需运维约束；密文不可读，排障需使用离线工具。
- 需要同步修改的文档与代码：`docs/rules/security.md` SEC-02、`deploy/nacos/README.md`、`TODO.md` P0-5、任务包 T-012（待创建）。
- 回滚方式：回退本 ADR 与 `common-security` 代码；已加密的配置需先解密回明文或保留密钥，不能直接删除密钥。

## 关联

- 相关契约：无（不涉及接口 / DDL / 事件契约）
- 相关任务包：T-012（配置迁移，待决策 / 待创建）
- 替代/被替代的 ADR：无
