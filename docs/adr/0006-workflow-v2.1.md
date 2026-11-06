# 0006. 工作流 v2.1：机械门禁分工、归档单一真相与移除 maintainer-approved

- 状态：已采纳
- 日期：2026-11-06
- 决策人：项目维护者（Claude 起草）

> 本 ADR 修订 [0004](0004-workflow-v2.md) 的评审分工与门禁标签；四分类、四状态、任务包、契约基线、报告归档提交机制保持不变。

## 背景

工作流 v2（ADR 0004）合并后，执行层仍有三处冗余：

1. **评审清单与 CI 门禁重复**。`workflow-check` 已机械校验变更分类、允许路径、规模、分支命名、契约/ADR 配套、任务状态、报告归档与 Reviewed SHA 范围，但 `docs/review/standards.md` §5 仍要求 Codex 逐条复核。尤其 DOC-03：报告归档发生在 APPROVE 之后，Codex 在评审阶段无法检查归档结果。
2. **报告归档流程分散在 6 处**：`AGENTS.md` §0、`README.md` 标准交接顺序、`docs/tasks/README.md` §6、`docs/review/standards.md` §6.1、`docs/review/reports/README.md` 归档顺序、`docs/rules/git-workflow.md` GIT-05。ADR 0004 §9 已定“单一真相”，但该流程仍多处复写，存在漂移风险。
3. **`maintainer-approved` 标签冗余**。能修改受保护路径（根/父 pom、`deploy/sql/**`、`deploy/nacos/config/**`、Compose 端口映射、`.gitattributes`）的 PR 只可能是 Arch 或 Ops：Feature 已被 `workflow-check` 禁止夹带 Arch/Ops 路径，Docs 类不允许非文档文件。Arch/Ops 本来就强制 `review:approved`，两个标签又都由同一位维护者添加，没有增加第二个审批人，只增加一次点击和一个遗忘点。

## 决策

### 1. 在评审规则中标注机械门禁覆盖范围

`docs/review/standards.md` 新增 §5.0：

- **CI 全覆盖**（Codex 不逐项复核）：SCOPE-00、SCOPE-01、DOC-01、DOC-03。
- **CI + 语义**（Codex 只复核语义部分）：SCOPE-02、SCOPE-03、DOC-04、GIT-02、GIT-03。
- 评审流程第 2 步先读取 `workflow-check` 结果；CI 失败时仍按原规则给 `REQUEST_CHANGES`。
- DOC-03 的归档结果由维护者/CI 在 APPROVE 后校验；Codex 只需保证报告内容完整、命名正确。

### 2. 报告归档流程收敛到 `standards.md` §6.1

`standards.md` §6.1 是归档步骤、免复审规则与 `Reviewed SHA..HEAD` 校验的唯一正文；其他文件只保留一行引用：

- `AGENTS.md` §0 第 7 步只写“按 standards §6.1 归档并校验”；
- `README.md` 标准交接顺序第 5 步只引用；
- `docs/tasks/README.md` §6 只引用，不重复步骤；
- `docs/review/reports/README.md` 只保留命名、写入权限、保留策略与门禁链接；
- `docs/rules/git-workflow.md` GIT-05 只引用。

### 3. 删除 `maintainer-approved` 标签

- `workflow-check` 不再因受保护路径缺少 `maintainer-approved` 而失败；改为输出警告，列出受保护文件。
- 受保护路径的变更必须在评审报告与 PR 描述中列出文件、理由与影响；Codex 按 OPS-02 / GIT-04（Blocker）评审，维护者随 `review:approved` 一并确认。
- 标签从 3 个（`review:approved`、`maintainer-approved`、`size-exception`）降为 2 个（`review:approved`、`size-exception`）。

### 4. 明确不做

- 不改四分类、四状态、Feature 任务包、契约基线标签、报告归档提交机制。
- 不调整 Codex 自测试 A/B/C 分级；A 级全量 `mvn verify` 的取舍单独决策。
- 不删除或改写历史评审报告、历史任务包与 ADR 0004 正文。
- 不引入新依赖；门禁仍使用 Node 内置模块。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 保持现状，评审继续逐条复核 | 不改文档 | 评审时间浪费在 CI 已保证的项上；DOC-03 仍无法在评审阶段检查 | 否决 |
| 只做文档去重，不动标签 | 风险最低 | 保留一个无效人工动作 | 部分采纳（本 ADR 第 2 项） |
| 保留 `maintainer-approved`，仅加警告 | 多一次显式确认 | 同一人添加两个标签，不构成第二双眼睛 | 否决 |
| 本 ADR：标注 CI 覆盖 + 归档单一真相 + 移除冗余标签 | 减少评审工作量与漂移；不削弱语义评审 | 受保护路径失去独立机械门禁，依赖报告与 `review:approved` | 采纳 |

## 影响

- 正面：评审清单减少约 9 个机械项；报告归档流程只有一处正文；维护者少一个标签动作。
- 负面/代价：受保护路径变更不再有独立标签；若未来增加第二位维护者，可恢复该标签作为第二确认。
- 需要同步修改的文档与代码：`docs/review/standards.md`、`docs/review/reports/README.md`、`docs/tasks/README.md`、`docs/rules/git-workflow.md`、`AGENTS.md`、`README.md`、`.github/scripts/workflow-check.mjs`、`.github/scripts/workflow-check.test.mjs`、`docs/adr/README.md`。
- 回滚方式：恢复 `maintainer-approved` 校验与文档；将归档步骤从 `standards.md` §6.1 复制回各入口。

## 关联

- 相关契约：无
- 相关任务包：无
- 修订/被修订：[0004](0004-workflow-v2.md)
