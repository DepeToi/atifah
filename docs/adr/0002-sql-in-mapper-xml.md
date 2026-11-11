# 0002. SQL 统一写在 Mapper XML

- 状态：已采纳
- 日期：2026-11-11
- 决策人：项目维护者

## 背景

原规则只要求“复杂 SQL 才写 XML”，简单查询可使用 MyBatis-Plus 的 `BaseMapper` / `IService` 或 Wrapper。边界依赖主观判断，SQL 会分散在 Java 代码与 XML 中，评审时难以确认查询是否完整、是否带逻辑删除条件，也不便于统一做索引和慢 SQL 优化。项目仍处于骨架阶段，此时统一约定成本最低。

## 决策

- 所有 SQL（SELECT / INSERT / UPDATE / DELETE）必须在 Mapper 接口声明方法，并在 `src/main/resources/mapper/*.xml` 中实现。
- Mapper 接口不继承 `BaseMapper`，只声明自定义方法；业务代码禁止调用 `BaseMapper` / `IService` 的任何 CRUD 方法。
- INSERT 的 `id` 由 Service 用 `IdWorker.getId()` 生成，XML 显式插入 `id`；`created_at` / `updated_at` 由数据库默认值 / `ON UPDATE` 维护，`MetaObjectHandler` 不作用于 XML。
- 自定义 XML 不受 `@TableLogic` 影响：查询 / 更新 / 删除必须显式带 `deleted = 0`，逻辑删除写 `deleted = 1`。
- XML 的 `namespace` 为 Mapper 全限定名，statement id 与接口方法名一致；参数一律 `#{}`，动态 SQL 使用 `<if>` / `<where>` / `<foreach>`，排序字段走白名单。
- 分页查询第一个参数使用 MyBatis-Plus `Page<T>`，由 `PaginationInnerInterceptor` 追加 `LIMIT`；禁止在 XML 手写 `LIMIT`。
- `mybatis-plus.mapper-locations` 在 Nacos 公共配置与各服务本地 `application.yml` 中保持同值 `classpath*:/mapper/**/*.xml`：Nacos 优先，本地作为 Nacos 未配置时的回落。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 保持“简单查询用 BaseMapper/Wrapper，复杂 SQL 才写 XML” | 开发快 | 边界模糊，SQL 分散，评审口径不一致 | 否决 |
| 只把查询写 XML，写入继续用 BaseMapper | 查询集中，写入省样板 | SQL 仍有两条来源，ID/时间/逻辑删除行为不一致 | 否决 |
| 所有 SQL 写 XML，Mapper 不继承 BaseMapper | 全部 SQL 可检索、可审、行为一致 | XML 样板增加；ID、时间、逻辑删除需显式处理 | 采纳 |

## 影响

- 正面：SQL 集中存放，便于评审、索引优化和慢 SQL 排查；没有 Wrapper / BaseMapper 隐式 SQL；所有写操作的字段处理方式一致。
- 负面/代价：简单 SQL 也需维护 XML；`id` 必须由 Service 生成；`created_at` / `updated_at` 依赖数据库默认值；`deleted` 必须显式维护；`mapper-locations` 在 Nacos 与本地 yaml 双写，修改时必须同步两处。
- 需要同步修改的文档与代码：`docs/rules/database.md`、`docs/rules/code-style.md`、`AGENTS.md` §4/§8、`docs/review/standards.md`、`atifah-services/AGENTS.md`、`atifah-common/AGENTS.md`、`README.md`、`docs/architecture.md`、`deploy/nacos/config/common-application.yaml`（修改后需重新导入 Nacos）、各业务服务与 admin-service 的 `application.yml`、`atifah-common/common-mybatis`（移除 `MetaObjectHandler`，`BaseEntity` 去掉 MyBatis-Plus 注解）。
- 回滚方式：恢复“简单查询用 BaseMapper/Wrapper、复杂 SQL 才写 XML”的规则，恢复 `MetaObjectHandler` 与实体注解。

## 关联

- 相关契约：无
- 相关任务包：无
- 替代/被替代的 ADR：无
