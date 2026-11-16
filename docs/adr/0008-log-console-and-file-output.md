# 0008. 运行日志同时输出控制台与文件

- 状态：已采纳
- 日期：2026-11-16
- 决策人：人类维护者

## 背景

ADR 0007 决定“应用只写 stdout，不配置 file appender”，落盘与轮转交给容器和 Loki。实际使用中出现以下缺口：

- 本地 JVM / IDEA 启动时没有容器日志驱动，终端关闭或滚动后日志即丢失，排障只能依赖当前控制台。
- 开发与联调环境未部署 Loki，无法按 TraceId 回查历史日志。
- 手工 `nohup java ... > app.log` 依赖每个启动入口，容易遗漏，且异常堆栈、MDC 格式与控制台不一致。

需要在**不改变 stdout 采集链路**（容器 / Loki 仍采集 stdout）的前提下，让应用同时写文件。安全边界不变：文件日志同样禁止敏感明文（SEC-01、CODE-07）。

## 决策

1. 使用 Spring Boot 3.5 原生 file appender（`logging.file.name`），不引入新依赖，不新增 `logback-spring.xml`。设置 `logging.file.name` 后，Boot 默认 logback 配置会同时挂载 `CONSOLE` 与 `FILE` 两个 appender。
2. **所有环境默认开启**文件输出。文件名为 `${spring.application.name}.log`，目录由 `${ATIFAH_LOG_DIR:logs}` 控制，多服务共用目录时靠服务名避免冲突。
3. 控制台与文件使用同一文本 pattern（`logging.pattern.file` 显式对齐 console，避免 Boot 默认 file pattern 多出 PID 与 `---`）。
4. `ATIFAH_LOG_FORMAT=logstash` 时控制台与文件**同时**输出 JSON：`logging.structured.format.file` 与 `logging.structured.format.console` 取同一环境变量。不配置 file 的 format 会导致“控制台 JSON、文件文本”的不一致。
5. 轮转策略：单文件 100MB，保留 30 个归档（按天归档，近似 30 天），备份总量上限 5GB，归档 gzip。三个值分别由 `ATIFAH_LOG_MAX_FILE_SIZE`、`ATIFAH_LOG_MAX_HISTORY`、`ATIFAH_LOG_TOTAL_SIZE_CAP` 覆盖；`clean-history-on-start` 保持关闭。
6. stdout 仍是主输出：容器 / Loki 采集链路不变。文件用于本地排障与兜底，**不作为**多副本容器间的集中日志。
7. 不修改 Dockerfile 与 docker-compose：容器内日志写在 `/app/logs`（`WORKDIR=/app`，属主 `atifah`，可写）。宿主机可见的日志挂载涉及目录属主与非 root 用户写权限，另行 Ops 变更；本次明确不做。
8. 日志内容与安全规则不变：文件不因落盘而允许出现日记正文、量表结果、手机号、密码/Token、验证码、SQL 参数、MQ payload、Redis value。
9. 修改 `logging.*` / `ATIFAH_LOG_*` 后需重启服务生效；不使用 Nacos 热刷新。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| A. Boot 原生 file appender | 零新增依赖；一份共享配置；JSON、轮转、保留策略与现有能力一致；控制台输出不变 | 文件 I/O 为同步写，高频日志有少量延迟；容器内文件随容器删除丢失 | 采纳 |
| B. 外部重定向 / Docker 日志驱动 | 应用仍纯 stdout，完全符合 ADR 0007 原文 | IDEA 运行没有文件；每个启动入口都要维护重定向；Docker 日志路径不可读、本地开发不适用 | 否决为主方案，可作为运行侧兜底 |
| C. 自定义 `logback-spring.xml` | 可按 logger / 级别拆分多个文件，可加 `AsyncAppender` | 11 个模块重复或公共资源耦合；配置分散在 Nacos 与 classpath；与“一份真相”冲突 | 暂不采用；出现错误日志独立保留或异步写需求时另行评估 |

## 影响

- 正面：
  - 本地 JVM / IDEA 与 Docker 环境都能在控制台之外保留日志，不依赖 Loki。
  - 文件日志与控制台共享同一 `traceId` / `userId` / 访问日志，排障方式一致。
  - 零新增依赖，不改变任何接口、表结构与消息契约。
- 负面/代价：
  - file appender 为同步写，满负荷访问日志会增加少量磁盘 I/O；如压测显示 P99 受影响，再评估异步 appender（需另立 ADR）。
  - 每个服务默认最多保留约 5GB 备份日志（当前文件另计 100MB），11 个服务同机运行需关注磁盘；可用环境变量下调。
  - 容器内 `/app/logs` 随容器删除而丢失，不能替代集中日志。
- 需要同步修改的文档与代码：
  - `deploy/nacos/config/common-application.yaml`（发布到 Nacos 后重启业务服务）
  - `atifah-gateway/src/main/resources/application.yml`（网关不导入公共 Nacos 配置）
  - `docs/architecture.md` §9
  - `deploy/dev-deploy.md`（日志位置说明）
- 回滚方式：
  - 移除 `logging.file.name`（以及 `pattern.file`、`structured.format.file`、`logback.rollingpolicy.*`），重新发布 Nacos 配置并重启服务；网关同步移除本地配置。
  - 回滚不涉及数据库、表结构与契约响应，无数据迁移。

## 关联

- 相关契约：无
- 相关规则：`docs/rules/code-style.md` CODE-07、`docs/rules/security.md` SEC-01
- 相关任务包：无（Arch 变更，按 ADR + 配置/文档 diff 交付）
- 替代/被替代的 ADR：部分替代 [ADR 0007](0007-logging-and-trace.md) §3 中“应用只写 stdout，不配置 file appender”的结论；0007 其余决策继续有效
