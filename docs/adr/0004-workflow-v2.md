# 0004. 工作流 v2：变更分类、四状态与机械门禁

- 状态：已采纳
- 日期：2026-11-06
- 决策人：项目维护者（Claude 起草）

> 本 ADR 调整 ADR 0001 中的角色边界、状态机、报告归档与规模阈值；三工具协作的基本框架仍以 ADR 0001 为基础。

## 背景

ADR 0001 建立了“仓库为唯一交接面”的三角色工作流，方向正确，但运行一个月后暴露了结构性摩擦：

1. **非功能变更没有合法路径**。PR #7（部署配置）、#10（网关迁移）、#11（Nacos 持久化）都不是业务功能，却都没有任务包；#7 靠维护者口头豁免，#10/#11 在合并后才补审为 `REQUEST_CHANGES`。PR #11 的硬编码数据库口令也是合并后才发现的。
2. **角色矩阵有空白**。`AGENTS.md` §0 没有把 `atifah-gateway/**` 与 `deploy/**` 分配给任何 AI 角色，导致基础设施改动只能绕过流程或越界执行。
3. **报告归档存在 SHA 死结**。Codex 不能提交，但流程要求报告在合并前落盘；提交报告又会改变 head SHA，使 `T-xxx-<head-sha>` 报告名与复审依据失效。T-001 §11 已记录“报告落盘晚于 PR 合并”，PR #10/#11 也只能合并后补审。
4. **状态机与人工门禁过重**。6 个状态、4 个人工动作（确认 ready、合并、改 done、打标签），且同一条状态机在 5 处以上重复定义。
5. **机械检查靠人**。`SCOPE-01`、`SCOPE-02`、`DOC-03`、`DOC-04`、`GIT-02` 都可以由脚本判断，却全部由 Codex 人工核对。
6. **规模阈值失真**。T-001 的 PR #1 新增 1174 行（生产代码约 668 行），第一次任务就触发 `SCOPE-02` 书面豁免，400 行上限失去约束力。
7. **文档漂移已发生**。任务文件命名、报告命名、`CLAUDE.md` 规则加载方式、任务模板允许路径均与正文不一致。

## 决策

### 1. 变更分四类

| 类型 | 范围 | 必备产物 | 评审 |
| --- | --- | --- | --- |
| Feature | 业务服务、管理端业务代码 | 任务包 + 契约引用 | Codex 语义评审 |
| Arch | `common`、`gateway`、根/父 pom、`docs/contracts/**`、`docs/rules/**`、ADR | ADR + 契约 diff | Codex 语义评审 |
| Ops | `deploy/**`、`.github/**`、Dockerfile | 变更说明 + 验证命令 | Codex 轻评审 |
| Docs | `docs/**`（非契约/ADR/rules）、README | PR 描述 | CI + 人工 |

混合变更按最高风险类型处理；Feature PR 不得夹带 Arch/Ops 路径，契约变更必须单独 PR。`docs/architecture.md`、`docs/domain.md`、`docs/db.md` 默认属于 Docs；只有同一 PR 还修改契约、ADR 或 `docs/rules/**` 时才按 Arch 处理。

### 2. 状态机收敛为四状态

`draft → ready → in-review → done`，任意状态可进入 `blocked`。

- 去掉 `in-progress`：分支与 PR 本身就是实现证据。
- `ready` 以维护者批准任务包/契约为准，不再要求 Claude 二次改文件。
- `done` 以合并为准，由维护者每周批量收口，不要求每个 PR 合并后立即提交。
- 人工决策点从 4 个降为 2 个：批准开工、批准合并。

### 3. 任务包只用于 Feature

Arch 用 ADR + 契约 diff；Ops 用变更说明 + 验证命令；Docs 用 PR 描述。任务包不再承担“所有变更的通行证”角色。

### 4. 评审报告改为“结论在 PR、报告随 PR 归档”

1. Codex 评审后在 PR 评论给出完整 `Verdict` 与自测试摘要；报告写入 `docs/review/reports/`（工作区或 PR 评论）。
2. APPROVE 后，CodeBuddy 将报告**原样复制**为 PR 最后一个 docs-only 提交，同时更新任务包 §11；该提交只允许改报告与任务包 §11/§12，免语义复审，由 CI 机械校验。
3. 报告只记录**被评审的代码 SHA（Reviewed SHA）**，不要求记录归档提交自身的 SHA：提交 SHA 由提交内容决定，要求报告写入自身 SHA 会形成不可满足的固定点。归档提交由 `workflow-check` 通过 `Reviewed SHA..HEAD` 的差异范围机械识别，维护者以 `review:approved` 标签 + CI 全绿作为合并条件。
4. Feature 使用 `T-xxx-rN-<reviewed-sha>.md` 命名；非 Feature 使用 `PR-<n>-<reviewed-sha>.md` 命名（不再使用可选的 `-rN`），历史报告不追溯改名。

### 5. 增加 CI 机械门禁

新增 `workflow-check` job（Node 内置模块，无新依赖），检查：变更分类、任务包与状态、允许路径、diff 规模、分支命名、契约与 ADR 配套、受保护路径、`git diff --check`；在 `review:approved` 标签存在时额外校验：

- 归档报告命名与报告内 `Reviewed SHA` 一致，且该提交存在并是 HEAD 的祖先；
- 至少一份“当前轮次”报告的 `Reviewed SHA..HEAD` 只包含 `docs/review/reports/**` 与任务包 §11/§12，任务包其余章节不得变化；同一 PR 可同时归档历史轮次报告，但旧报告不能单独作为审批依据；
- HEAD 最后一个提交为 docs-only；
- Feature 的 diff 不得夹带任何非业务路径（`deploy/**`、`.github/**`、`docs/rules/**`、ADR、根/模块规则、`common`、`gateway`、根/父 pom），任务包“允许修改”不能覆盖该禁止；
- PR 事件下任务状态只接受 `in-review`。

本地预检使用 `--local-precheck`：允许任务状态为 `ready`/`in-review`，跳过 `review:approved`、`maintainer-approved` 与归档报告校验；CI 门禁不启用该参数。门禁脚本配套 `node:test` 回归测试（`.github/scripts/workflow-check.test.mjs`），CI 每次 PR 先跑测试再执行门禁。

### 6. 规模阈值按类型区分

- Feature：单服务，≤600 行非测试代码，总计 ≤1000 行；测试不计入。
- Arch/Ops：≤500 行非文档代码。
- 超限必须拆任务，或由维护者加 `size-exception` 标签并记录理由。

### 7. 自测试分级

- **A 级**：业务服务、common、gateway、契约、安全相关——独立 worktree，全量 `mvn -B -ntp verify`，涉及前端时 `npm ci && npm run build`。
- **B 级**：deploy、配置、依赖升级、构建脚本——受影响模块 `verify` + 配置/Compose 校验；独立干净检出。
- **C 级**：纯文档——链接/格式检查，可免构建。

### 8. 明确基础设施归属

`atifah-gateway/**`、`deploy/**`、`.github/**` 归 Claude（架构角色）；业务服务与管理端业务代码归 CodeBuddy；受保护文件仍需维护者显式批准。

### 9. 单一真相

`AGENTS.md` §0 是工作流唯一正文；`docs/tasks/README.md` 只保留任务包 schema 与状态细节；`docs/review/standards.md` 只保留评审分级与输出格式；其他文档一律引用，不再复写。`CLAUDE.md` 只导入 `AGENTS.md`，细则按需加载。

## 明确不做

- 不引入 Spectral、ArchUnit、JaCoCo 等新依赖；需要时单独立项并人工确认。
- 不做自动合并、自动打标签、自动改任务状态。
- 不追溯修改历史任务包与历史报告；新规则从本 ADR 合并后生效。
- 不取消 Codex 独立自测试；A 级变更仍不得只信 CI。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 维持现状，靠维护者逐次豁免 | 不改文档 | 流程持续被绕过，合并后才发现 Blocker | 否决 |
| 所有变更都保留任务包 | 形式统一 | 非功能变更成本过高，必然继续绕过 | 否决 |
| 取消 Codex 自测试，只看 CI | 快 | 失去独立验证，安全风险高 | 否决 |
| 四类变更 + 四状态 + 机械门禁（本 ADR） | 简单、可执行、机械项可自动化 | 需要一次性改文档与 CI | 采纳 |

## 影响

- 正面：非功能变更有了合法路径；人工决策点减半；范围/大小/状态由 CI 拦截；报告归档不再依赖合并后补审。
- 负面/代价：需要维护 `workflow-check` 脚本与三个 PR 标签（`size-exception`、`maintainer-approved`、`review:approved`）；维护者需要每周批量收口 `done`。
- 需要同步修改的文档与代码：`AGENTS.md`、`CLAUDE.md`、`README.md`、`docs/tasks/**`、`docs/review/**`、`docs/contracts/README.md`、`docs/rules/git-workflow.md`、模块 `AGENTS.md`、`.github/pull_request_template.md`、`.github/workflows/ci.yml`、`.github/scripts/workflow-check.mjs`。
- 回滚方式：保留本 ADR；若机械门禁误报过多，可先降级为 warning，再调整脚本；角色与状态机回滚需同步修改上述文档。

## 关联

- 相关契约：`docs/contracts/README.md`
- 相关任务包规范：`docs/tasks/README.md`
- 相关评审规则：`docs/review/standards.md`
- 替代/被替代的 ADR：部分取代 [`0001-three-tool-collaboration.md`](0001-three-tool-collaboration.md)
