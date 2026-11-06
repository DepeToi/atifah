# TODO — 配置文件加密（ENC）迁移

> 交接对象：架构角色（Claude）。用途：确认迁移范围 → 出 ADR / 规则补充 / 任务包，再按三角色流程实施。
> 能力侧已由 CodeBuddy 实现并验证（2026-11-01），本文只列**待决策项与迁移清单**，不重复设计背景。
> 当前状态：ADR 0005 与 SEC-02 已落地（`arch/config-crypto` PR）；D-1 = C+A，配置迁移待 T-012。

## 0. 已完成（不需要重做）

| 能力 | 位置 |
| --- | --- |
| AES-256-GCM 加解密（`ENC(...)`，随机 IV，异常不含密文/密钥） | `atifah-common/common-security/src/main/java/com/atifah/common/security/crypto/ConfigCipher.java` |
| 解密覆盖层（置顶属性源，不持快照，Nacos 刷新后仍生效） | `.../crypto/ConfigCryptoPropertySource.java` |
| 启动装载器（`EnvironmentPostProcessor`，最低优先级；启动期校验主密钥与全部密文，缺密钥/错误密钥/篡改/截断时 fail-fast） | `.../crypto/ConfigCryptoEnvironmentPostProcessor.java`，注册于 `.../resources/META-INF/spring.factories` |
| 离线工具 `keygen` / `encrypt` / `decrypt` | `.../crypto/ConfigEncryptTool.java` |
| 使用说明 | `deploy/nacos/README.md` § 配置密文（ENC） |

验证：`mvn -o -pl atifah-common/common-security -am test` 23 用例通过；`mvn -o -q -DskipTests compile` 全量通过；工具 keygen→encrypt→decrypt 往返正确。

支持两种写法：
- 直写：`password: "ENC(...)"`
- 环境变量注入：`password: ${MYSQL_PASSWORD:}` 且 `MYSQL_PASSWORD=ENC(...)`

## 1. 待决策（阻塞迁移）

| 编号 | 决策点 | 选项 | 建议 |
| --- | --- | --- | --- |
| D-1 | 迁移范围 | A 仅 JWT secret / B JWT+DB+Redis / C 不迁移、只立规范 | **C+A（已采纳，ADR 0005 §4）**：现有值本就是 `${ENV:default}`，真实凭据不在仓库；先立规范（新增敏感配置必须 ENC 或环境变量），有真实值入库时再逐项加密 |
| D-2 | 主密钥托管 | compose `.env` / K8s Secret / 启动参数 | 环境变量 `CONFIG_ENC_KEY`，每环境独立，不入仓库；`.env.example` 由 T-012 更新（已采纳，ADR 0005 §5） |
| D-3 | 规则补充 | `docs/rules/security.md` SEC-02 是否新增「配置密文」条款 | 已补：SEC-02 条款（本 PR）；主密钥只走环境变量；轮换流程 |
| D-4 | 是否要 ADR | 该机制仅用 JDK，不新增依赖 | 已由 ADR 0005 记录选型（自研 vs Jasypt）、主密钥 32 字节 base64url、fail-fast 策略 |
| D-5 | 是否要任务包 | `deploy/nacos/config/*` 属规则禁止擅改范围 | ADR 0005 选择不批量迁移；需要时创建 T-012 并明确允许路径与验收 |

## 2. 迁移清单（D-1 选 A 或 B 时按此执行）

| 文件:行 | 现行写法 | 目标写法 | 说明 |
| --- | --- | --- | --- |
| `deploy/nacos/config/common-application.yaml:83` | `secret: ${JWT_SECRET:atifah-dev-secret-change-me-please-32bytes}` | `secret: ${JWT_SECRET:ENC(...)}` | 与网关同一份密文，值必须一致 |
| `atifah-gateway/src/main/resources/application.yml:131` | 同上 | 同上 | 网关不导入 common，需单独改 |
| `deploy/nacos/config/common-application.yaml:22` | `password: ${REDIS_PASSWORD:}` | `password: ${REDIS_PASSWORD:ENC(...)}` | 仅当 Redis 口令要写进 Nacos（方案 B） |
| `atifah-services/*/src/main/resources/application.yml:31`（9 个） | `password: ${MYSQL_PASSWORD:root}` | `password: ${MYSQL_PASSWORD:ENC(...)}` | 仅方案 B；本地兜底 `root` 建议保留，不要写生产密文进本地 yml |
| `atifah-admin-web/backend/src/main/resources/application.yml:31` | 同上 | 同上 | 方案 B，随 9 个服务一起 |

生成密文（密钥只在执行者环境里，不入仓库）：

```bash
mvn -q -DskipTests compile
CP=atifah-common/common-security/target/classes
java -cp $CP com.atifah.common.security.crypto.ConfigEncryptTool keygen
CONFIG_ENC_KEY=<主密钥> java -cp $CP com.atifah.common.security.crypto.ConfigEncryptTool encrypt
  # 终端输入明文，不回显；或从受限权限文件经 stdin 读取：
CONFIG_ENC_KEY=<主密钥> java -cp $CP com.atifah.common.security.crypto.ConfigEncryptTool encrypt < plaintext.txt
CONFIG_ENC_KEY=<主密钥> java -cp $CP com.atifah.common.security.crypto.ConfigEncryptTool decrypt 'ENC(...)'
```

## 3. 明确不迁移

| 配置项 | 位置 | 原因 |
| --- | --- | --- |
| `spring.cloud.nacos.{discovery,config}.password` | 各服务 yml:20/:27、网关、admin BFF | 连接 Nacos 发生在 config data 装载期，早于解密器；只能走 `NACOS_PASSWORD` |
| `NACOS_AUTH_TOKEN` | `deploy/docker-compose/.env.example:7` | Nacos 服务端启动参数，非 Spring 配置 |
| `spring.datasource.username`、`MYSQL_HOST/PORT` | 同上 | 非密钥 |
| `MYSQL_PASSWORD`（compose 环境变量） | `deploy/docker-compose/docker-compose.app.yml:31` | 环境变量本身已是仓库外通道，保持现状 |

## 4. 验收标准（迁移执行后）

- 无 `CONFIG_ENC_KEY`、配置中无密文时：服务正常启动（本地开发零成本）——已满足。
- 配置中有密文、无主密钥时：启动失败并提示 `CONFIG_ENC_KEY`——已满足。
- 主密钥格式非法、错误密钥、密文被篡改或截断时：启动期失败——已满足。
- 迁移后：带主密钥启动，日志不出现明文；`/actuator/env` 展示解密后的值属已知暴露面，生产必须关闭或限制（见 ADR 0005 §5）。
- 配置中不再出现生产真实凭据明文（`git grep` 抽查）。
- `mvn -B -ntp verify` 通过。

## 5. 风险与限制

- **本地开发**：迁移 JWT secret 后，未设 `CONFIG_ENC_KEY` 且未用 `JWT_SECRET` 覆盖时启动失败 → 必须在 `deploy/docker-compose/.env.example` 与 README 说明本地兜底方式（建议本地设明文 `JWT_SECRET`）。
- **Nacos 刷新**：覆盖层每次读取回查实时属性源，刷新后仍解密；但若值来自 `ENC()` 嵌套占位符（`${A:${B}}`）不支持，需在规范中禁止。
- **主密钥轮换**：需全量重新加密并同批切换，旧密钥解不开即 fail-fast，无灰度窗口。
- **`/actuator/env` 暴露**：内存中为明文，`management.endpoint.health.show-details: never` 已有，但 `env` 端点未默认关闭，需 D-4 一起确认。
- **审计**：`deploy/sql` 等已执行脚本不受影响，本次不涉及 DDL。

## 6. 建议拆分

1. ADR 0005（选型 + 密钥管理 + fail-fast）+ SEC-02 规则补充 —— 本 PR 完成。
2. T-012 配置迁移（仅 D-1 定下范围时）—— 单 PR，diff 预计 < 40 行。
3. `.env.example` / README 补充 `CONFIG_ENC_KEY` 与本地兜底说明 —— 可并入 T-012。
