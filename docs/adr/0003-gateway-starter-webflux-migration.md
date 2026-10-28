# 0003. 网关 starter 迁移至 spring-cloud-gateway-server-webflux

- 状态：已采纳（2026-10-29 人类维护者确认走路线 B）
- 日期：2026-10-29
- 决策人：DepeToi

## 背景

网关当前依赖 `spring-cloud-starter-gateway`（经典坐标），启动时输出两类 WARN：

1. starter / `spring-cloud-gateway-server` 的 deprecation 横幅（下个大版本移除）；
2. 40+ 条 `spring.cloud.gateway.* → spring.cloud.gateway.server.webflux.*` 配置 key 改名提示（靠属性迁移器临时映射）。

2026-10 核实的阻塞事实：

- SCA **2025.0.0.0**（当前使用）的 `spring-cloud-alibaba-sentinel-gateway` pom **非 optional** 依赖经典 starter——单独换新 starter 会导致经典 jar 经传递依赖回流，新旧两套 Gateway 配置类共存，风险不可控；
- Sentinel `sentinel-spring-cloud-gateway-v6x-adapter` 1.8.9 对 `spring-cloud-gateway-server` 是 **provided** 依赖，且 SCG 4.3.x 改名只改坐标不改包名（`org.springframework.cloud.gateway.*` 不变）——适配器对"运行时由谁提供 gateway 类"无硬性要求；
- SCA **2025.1.0.0**（配套 SC 2025.1.x）的 sentinel-gateway pom 已将经典 starter 依赖改为 **`optional`**——上游强制回流问题在该线已解除；
- 2026-10-29 补充核实：**SC 2025.1.2 BOM 声明要求 Spring Boot 4.0.7**（Boot 3.5 不可用），gateway 5.0.x 已删除经典坐标——路线 A 实为 **Boot 3.5→4.0 跨大版本平台迁移**（Spring Framework 6.2→7.0，全部 15 个模块受影响，MyBatis-Plus/Redisson/RocketMQ starter 的 Boot 4 适配待核实），工作量远超版本号变更，必须另立迁移项目。

不决策的后果：SC 发布移除经典坐标的版本时网关无法升级，且属性迁移器兜底随时可能在新版本中失效（路由静默丢失）。

## 决策

采用**路线 B**（当前依赖线内完成迁移，一次 PR、原子提交），并将**路线 A**（随 SC 2025.1.x 平台升级）列为后续独立任务：

1. `atifah-gateway/pom.xml`：`spring-cloud-starter-gateway` → `spring-cloud-starter-gateway-server-webflux`；并在 `spring-cloud-alibaba-sentinel-gateway` 上 exclude `spring-cloud-starter-gateway`（阻断经典 jar 回流）。
2. `atifah-gateway/src/main/resources/application.yml`：全部网关 key 一次性迁移到 `spring.cloud.gateway.server.webflux.*`（routes、discovery.locator、globalcors）。**pom 与 key 必须同一 PR，不得只做一半**。
3. 验收清单（全部通过才算完成）：
   - 启动 console 无 deprecation 横幅、无 key 改名 WARN；
   - `SentinelSpringCloudGateway` 过滤器与 BlockExceptionHandler 注册日志仍在；
   - `/actuator/gateway/routes` 显示全部 11 条路由；
   - `GET /api/v1/auth/login` → 503（LB 无实例，路由链路通）；`/actuator/health` → UP；
   - Sentinel dashboard（8858）能看到 `atifah-gateway`；对任一路由下发 QPS 规则后超限请求返回 429/阻断响应（验证 v6x 适配器在新坐标下真实生效）。
4. 回滚方案：revert 单个 PR 即可（不动公共模块与根 pom）。

明确不做：

- 不升 Spring Cloud 2025.1.x / SCA 2025.1.0.0（跨线升级，涉及 9 服务依赖矩阵，走独立 ADR + 人工窗口）；
- 不在本次迁移中引入 LoadBalancer Caffeine 缓存（见根 `README.md` 已知依赖说明，另行决策）。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| A. 随 SC 2025.1.x + SCA 2025.1.0.0 平台升级一并迁移 | 一次升到位、无排除依赖的临时手段；上游 optional 化已解除堵点 | 实为 Boot 4.0 跨大版本平台迁移，波及全部模块，需独立 ADR 与全量回归 | 采纳为独立平台升级项目（后续另立 ADR） |
| B. 当前线内换坐标 + exclude + key 原子迁移 | 改动小（2 个文件）、可独立验证与回滚；消除两类 WARN | Sentinel 限流链路需实测兜底；exclude 属临时手段，路线 A 时移除 | 采纳（本次） |
| C. 维持现状不动 | 零成本 | WARN 持续；SCG 下个大版本移除经典坐标时被迫紧急迁移 | 否决 |

## 执行结果（2026-10-29）

路线 B 已完成并通过验收：

- `mvn -pl atifah-gateway -am package` 通过；dependency:tree 确认 SCG 依赖链为
  `server-webflux:4.3.5 → gateway-server:4.3.5`（4.3.x 官方过渡设计），SCA 侧经典回流已由 exclusion 阻断。
- 启动 console：deprecation / key 改名 WARN 均为 0；Sentinel 过滤器与 BlockExceptionHandler 正常注册；
  注册 IP 为物理网卡（配合 `spring.cloud.inetutils.ignored-interfaces`）。
- 行为验证：`/actuator/health` UP；10 条 REST 路由 + `/ws/**` 逐路径探测全部 503（路由匹配、后端未启动），
  路由谓词工厂 14 个全部加载。
- 遗留：`/actuator/gateway/**` 端点在 SCG 4.3.5 上不注册（迁移前即如此，非本次回归），已记录于
  根 `README.md` 已知依赖说明；Sentinel dashboard 下发 QPS 规则实测限流生效属人工验收项，待 auth-service
  接入流量后补测。
