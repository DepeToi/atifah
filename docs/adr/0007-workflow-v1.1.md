# 0007. 工作流 v1.1：变更分类、四状态与报告归档解耦

- 状态：已采纳
- 日期：2026-11-07
- 决策人：项目维护者（Claude 起草）

> 本 ADR 修订 [0001](0001-three-tool-collaboration.md) 中的状态机、报告命名与角色可写范围；三工具协作的仓库交接框架不变。
>
> 2026-11-06 的 workflow v2/v2.1（备份见 `backup/pre-rollback-5356b41`）曾引入四分类、四状态、A/B/C 自测试与 `workflow-check` 机械门禁，随后被整体回滚。本 ADR 只采纳其中低成本的分类、状态与自测试分级，**不引入**新脚本、新标签与新依赖。

## 背景

ADR 0001 建立的“仓库为唯一交接面”方向正确，但运行一个月后暴露了结构性摩擦：

1. **非功能变更没有合法路径**。PR #7（部署配置）靠维护者口头豁免；PR #10（网关迁移）、PR #11（Nacos 持久化）都在合并后才补审为 `REQUEST_CHANGES`；PR #11 的硬编码数据库口令也是合并后才发现。
2. **报告归档存在 SHA 死结**。Codex 不能提交，但流程要求报告在合并前落盘；提交报告又会改变 head SHA，使 `T-xxx-<head-sha>` 报告名与复审依据失效。T-001 §11 已记录“报告落盘晚于 PR 合并”，PR #10/#11 只能合并后补审。
3. **状态机与人工门禁过重**。6 个状态、4 个人工动作，且状态机在 `AGENTS.md`、`docs/tasks/README.md`、`docs/rules/git-workflow.md`、PR 模板重复定义。
4. **任务包模板过重**。T-005 只有 3 个文件、+83/−10，仍写满 12 节；必读文件、交付物、自检命令与 `AGENTS.md`/`docs/rules` 重复。
5. **机械检查靠人**。SCOPE-01/02、DOC-03/04、GIT-02 都可以由脚本判断，却全部由 Codex 人工核对；实际已出现“PR 链接待补”等漏检。
6. **自测试一刀切**。CI 已跑全量 `mvn verify`，Codex 再在独立 worktree 跑一次全量；Ops/Docs 是否免跑靠人工判断，标准不一致。

## 决策

### 1. 变更分四类

| 类型 | 范围 | 必需产物 | 评审 |
| --- | --- | --- | --- |
| Feature | 业务服务、管理端业务代码 | 任务包 + 契约引用 | Codex 语义评审 + A 级自测 |
| Arch | `atifah-common/**`、`atifah-gateway/**`、根/父 `pom.xml`、`docs/contracts/**`、`docs/rules/**`、ADR | ADR（涉及契约时附契约 diff） | Codex 语义评审 + A 级自测 |
| Ops | `deploy/**`、`.github/**`、Dockerfile、构建/部署脚本 | PR 描述中的变更说明、影响面、验证命令与回滚方式 | Codex 轻评审（安全/配置）+ B 级自测 |
| Docs | `docs/**`（非契约/ADR/rules）、`README.md` | PR 描述 | CI + 人工；Codex 可免 |

- 混合变更按最高风险类型处理；Feature 不得夹带 Arch/Ops 路径；契约变更必须单独 PR。
- `docs/architecture.md`、`docs/domain.md`、`docs/db.md` 默认属于 Docs；同一 PR 同时修改契约、ADR 或 `docs/rules/**` 时按 Arch 处理。
- 受保护文件（根/父 pom 的 parent 与版本、`deploy/nacos/config/*`、`deploy/docker-compose` 端口映射、`deploy/sql` 已执行脚本、`.gitattributes`）仍需维护者显式确认，不因分类而豁免。

### 2. 状态机收敛为四状态

`draft → ready → in-review → done`，任意状态可进入 `blocked`。

- 去掉 `in-progress`：分支与 PR 本身就是实现证据，CodeBuddy 在 PR 创建后直接置 `in-review`。
- `ready` 以维护者批准任务包/契约为准，不再要求 Claude 二次改文件。
- `done` 以合并与报告归档为准，由维护者批量收口，不要求每个 PR 合并后立即提交。
- 人工决策点从 4 个降为 2 个：批准开工、批准合并。
- 状态只对 Feature 任务包生效；Arch/Ops/Docs 不新建任务包，也不占用 `T-xxx` 编号。

### 3. 任务包只用于 Feature，模板收敛为 8 节

模板保留：目标与非目标、上下文与必读（只引路径）、变更范围、实现要点、验收标准、测试与自检、风险与回滚、评审与状态记录（§8.1/§8.2）。删除与 `AGENTS.md`/`docs/rules` 重复的必读清单、交付物清单与自检命令章节。

### 4. 报告归档改为“Reviewed SHA + docs-only 归档提交”

1. 报告按 **Reviewed SHA**（Codex 实际测试的代码提交）命名，不按归档提交或最终 head SHA 命名。
   - Feature：`T-xxx-rN-<reviewed-sha>.md`
   - Arch/Ops/Docs：`PR-<n>-<reviewed-sha>.md`
2. APPROVE 后，由实现者（Feature：CodeBuddy；Arch/Ops：Claude）把报告原文作为 **docs-only 提交** 归档，并更新任务包 §8.1。该提交只允许改 `docs/review/reports/**` 与任务包 front matter / §8.1 / §8.2，不触发语义复审。
3. 如果 Codex 无法写文件或访问 GitHub：在 PR 评论输出完整报告，由实现者/维护者原样保存；归档前不得把任务置为 `done`。
4. 如果 PR 先合并：允许在任务关闭前补归档；DOC-03 在任务关闭时校验，不再把“合并前落盘”作为合并门禁。
5. Codex 的写入范围仍仅限 `docs/review/reports/**`；不得修改代码、契约、规则、任务实现或测试。

### 5. 自测试分级

- **A 级**：Feature、Arch、`common`、`gateway`、契约、安全相关——独立 worktree，全量 `mvn -B -ntp verify`；涉及前端时 `npm ci && npm run build`。
- **B 级**：Ops、`deploy`、配置、依赖升级、构建脚本——受影响模块 `verify` + `docker compose config -q` / YAML 解析（如适用）；独立干净检出。
- **C 级**：Docs、纯文档/规则（不含契约）——链接/格式检查；可免构建，报告中说明理由。
- A 级不得只跑单模块；B 级无法起 Docker/中间件时写“未覆盖”，不得默认通过。

### 6. 单一真相与规则别名

- 状态机只在 `docs/tasks/README.md` §2；报告归档只在 `docs/review/standards.md` §6.1；其他文档只引用。
- 同一根因只报一个 canonical 规则编号；别名仅用于索引，不重复计数：

| canonical | alias |
| --- | --- |
| SCOPE-01 | GIT-03 |
| ARCH-01 | DB-01 |
| API-04 | SEC-04 |
| DB-03 | CODE-06 |
| DOC-02 | GIT-05 |
| OPS-02 | GIT-04 |

### 7. 明确基础设施归属

`atifah-gateway/**`、`deploy/**`、`.github/**` 归 Claude（架构角色）；业务服务与管理端业务代码归 CodeBuddy。此归属解决 `TODO.md` P0-4 指出的“`atifah-gateway/**` 与 `deploy/**` 没有 AI 角色”的缺口。

## 明确不做

- 不引入 `workflow-check`、ArchUnit、Spectral、JaCoCo 等新脚本或依赖；机械项自动化单独立项。
- 不引入 `review:approved` / `size-exception` / `maintainer-approved` 等新标签。
- 不做自动合并、自动打标签、自动改任务状态。
- 不追溯修改历史任务包、历史报告与 ADR 0001 正文；新规则从本 ADR 合并后生效。
- 不取消 Codex 独立自测试；A 级变更仍不得只信 CI。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 维持现状，靠维护者逐次豁免 | 不改文档 | 流程持续被绕过，合并后才发现 Blocker | 否决 |
| 所有变更都保留任务包 | 形式统一 | 非功能变更成本过高，必然继续绕过 | 否决 |
| 恢复 backup 中的 workflow v2.1 | 机制完整、已评审 | 461 行脚本 + 371 行测试 + 3 个标签，与“简化”目标相反，且已被回滚 | 否决 |
| 四分类 + 四状态 + Reviewed SHA + 8 节模板（本 ADR） | 改动小、可执行、保留独立评审 | 状态与归档仍需人工核对 | 采纳 |

## 影响

- 正面：非功能变更有了合法路径；人工决策点减半；报告归档不再依赖合并后补审；小任务的前置文档成本下降；自测试与变更风险匹配。
- 负面/代价：报告的 docs-only 归档提交需要实现者执行一次；机械项仍由 Codex/维护者核对；`done` 改为批量收口后，任务状态可能短暂滞后于合并。
- 需要同步修改的文档：`AGENTS.md`、`README.md`、`docs/tasks/README.md`、`docs/tasks/_TEMPLATE.md`、`docs/review/standards.md`、`docs/review/reports/README.md`、`docs/review/report-template.md`、`docs/rules/git-workflow.md`、`docs/contracts/README.md`、`.github/pull_request_template.md`、`docs/adr/README.md`。
- 回滚方式：恢复上述文档到本 ADR 合并前的版本；已归档报告与任务包不回改。

## 关联

- 相关契约：`docs/contracts/README.md`
- 相关任务包规范：`docs/tasks/README.md`
- 相关评审规则：`docs/review/standards.md`
- 修订：[0001-three-tool-collaboration.md](0001-three-tool-collaboration.md)
- 备份参考：`backup/pre-rollback-5356b41`（workflow v2/v2.1，不恢复）
