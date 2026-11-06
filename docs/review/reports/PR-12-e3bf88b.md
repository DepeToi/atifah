# 评审报告

> 变更类型：Arch（夹带 Ops/CI 支撑变更）
> 评审对象：PR #12，分支 `arch/workflow-v2`，base `b047559395dd362688b6ff1b7ce51631db742fa8`，head `e3bf88b47d59168e994d00b3efb663fa7db840a0`
> 任务包 / ADR：`docs/adr/0004-workflow-v2.md`
> 契约基线：不涉及契约内容变更
> Reviewed SHA：e3bf88b47d59168e994d00b3efb663fa7db840a0
> 归档 SHA：待报告归档提交产生后填写
> 评审人：Codex
> 日期：2026-11-06

## 结论

- Verdict：`REQUEST_CHANGES`
- Blocker：3
- Major：3
- Minor：1
- Question：0
- 自测试：PASS（本地 B 级自测试通过；GitHub CI 未取得）
- 自测试级别：B

## 范围检查

- [x] 变更类型与实际 diff 基本一致：修改工作流规则、ADR 和 CI 门禁，属于 Arch + Ops。
- [x] 必需产物齐全：新增 ADR 0004；未修改 OpenAPI / DDL / 事件契约。
- [ ] CI 状态未取得：GitHub CLI token 无效，公开 API 对私有仓库返回 404；PR 头和编号通过 `refs/pull/12/head` 确认。
- [x] `git diff --check` 通过。
- [x] Arch/Ops 非文档代码 485 行，低于 500 行阈值。
- [x] 本地 `mvn -B -ntp verify` 在独立干净克隆中通过。
- [ ] 前端未运行：本次 diff 不涉及前端运行时代码。

## 发现详情

### [Blocker] docs/adr/0004-workflow-v2.md:50 — DOC-03 — 归档 SHA 被要求写入其自身所属提交，流程不可完成

- 证据：ADR 要求报告“原样复制为 PR 最后一个 docs-only 提交”，同时要求报告记录“归档提交 SHA”；`docs/review/report-template.md:10`、`docs/tasks/README.md:70,94` 也要求在提交前填写该 SHA。
- 影响：Git commit SHA 由提交内容计算，提交内容又包含该 SHA，形成不可满足的固定点。要么修改报告导致不再是原样复制，要么再提交一个 docs-only 提交导致前者不再是最后一个。ADR 声称修复的 SHA 死锁因此仍然存在。
- 建议：移除合并前对“归档 SHA”的强制要求；或改为可执行的两阶段归档，由后续机械步骤记录 SHA，并明确最后一个提交及其允许范围。

### [Blocker] .github/scripts/workflow-check.mjs:447 — DOC-03 — 归档门禁只检查报告是否存在，未校验最后提交、范围与 Reviewed SHA

- 证据：`reports = files.filter(...)` 后仅判断报告数量；Feature 额外检查任务文件中是否出现报告文件名。脚本没有检查 HEAD 最后一个提交是否 docs-only、是否只包含报告及任务 §11/§12，也没有从报告或文件名解析 Reviewed SHA 并与代码提交比较。
- 影响：旧报告、过期审批或审批后新增业务代码都可继续通过 `review:approved` 门禁；这与 `docs/review/reports/README.md:24` 声称的“workflow-check 校验归档提交范围”不一致，直接削弱合并门禁。
- 建议：校验最后一个或最后一批提交只能修改 `docs/review/reports/**` 与任务 §11/§12；解析 Reviewed SHA，确认其是当前代码头，且 `Reviewed SHA..HEAD` 只包含允许的归档差异；为这些路径补充正向和负向测试。

### [Blocker] .github/scripts/workflow-check.mjs:371 — SCOPE-01 — Feature 越界检查遗漏 Ops 与部分 Arch 路径

- 证据：`configFiles` 只收集 `atifah-common/`、`atifah-gateway/`、契约文件和根/父 pom。Feature 任务若将 `deploy/**`、`.github/**`、`docs/rules/**`、根/模块 `AGENTS.md` 或 ADR 写入允许路径，`isAllowed` 会放行，而后续检查不会报错。
- 影响：违反 `AGENTS.md:26` 的“Feature PR 不得夹带 Arch/Ops 路径”。机械门禁反而能接受任务包授权越界，无法承担 SCOPE-01/GIT-03 的拦截职责。
- 建议：使用与 `classify()` 相同或更严格的 Arch/Ops 分类，对 Feature diff 中所有非 Feature 路径统一拒绝；任务包“允许修改”不能覆盖全局禁止路径。

### [Major] .github/scripts/workflow-check.mjs:143 — SCOPE-00 — 架构文档分类与根规则不一致

- 证据：脚本把 `docs/architecture.md`、`docs/domain.md`、`docs/db.md` 归为 Arch；`AGENTS.md:31` 将 Docs 定义为 `docs/**` 中除契约、ADR、rules 以外的文档。
- 影响：仅修改上述文档时，按规则应走 `docs/<topic>`，但脚本会要求 `arch/*` 分支和 `review:approved`，导致合法 Docs PR 被机械门禁误拦。
- 建议：由 ADR 明确这些文件的分类，并让 `AGENTS.md`、`classify()` 和分支规则保持一致。

### [Major] .github/scripts/workflow-check.mjs:1 — TEST-01 — 新增 461 行合并门禁没有任何自动化测试

- 证据：本次新增完整的 `workflow-check.mjs`，但仓库没有对应的 Node 测试或 fixture；CI 仅直接执行脚本，没有覆盖合法/非法分支、Feature 范围、尺寸、受保护路径、标签、报告归档和 SHA 过期等分支。
- 影响：该脚本控制 Arch/Ops/Feature 的合并入口，却无法回归验证。本次已发现多个错误的允许条件，说明缺少负向测试会持续放大流程风险。
- 建议：使用 Node 内置 `node:test`，在临时 Git 仓库构造最小 commit/tag/报告 fixture，至少覆盖 Feature 夹带 deploy/.github、旧 Reviewed SHA、非 docs-only 归档提交、Arch/Docs 分类和标签门禁。

### [Major] .github/scripts/workflow-check.mjs:351 — DOC-04 — PR 阶段错误接受 `ready` 状态

- 证据：脚本允许 Feature 任务状态为 `ready` 或 `in-review`；`docs/tasks/README.md:22,28,92` 规定 PR 已创建时应由 CodeBuddy 置为 `in-review`。
- 影响：任务状态与 PR 证据不一致时仍可通过机械检查，四状态机的必经流转没有被执行。
- 建议：PR 场景只接受 `in-review`；若需要支持尚未建 PR 的本地检查，应通过显式参数区分本地预检和 CI 门禁。

### [Minor] docs/review/standards.md:220 — DOC-02 — 非 Feature 报告命名在规则间不一致

- 证据：`standards.md:220` 写非 Feature 使用 `PR-<n>-<reviewed-sha>.md`，`reports/README.md:10` 与 `report-template.md:4` 允许或要求可选的 `-r<n>`。
- 影响：同一轮评审可能产生不同文件名，削弱检索与归档一致性。
- 建议：选择一个唯一格式，并同步模板、归档说明和脚本正则。

## 自测试记录

- 级别：B
- commit：e3bf88b47d59168e994d00b3efb663fa7db840a0
- 工作区：`D:\Project\atifah\.review-e3bf88b-clean`，由 `git clone D:\Project\atifah` 创建的独立干净克隆
- 环境：Windows 11 / JDK 21.0.2 / Maven 3.9.12 / Node 24.13.1

| 命令 | 结果 | 备注 |
| --- | --- | --- |
| `git status --short --branch` | PASS | 审查开始时工作区干净 |
| `git diff --check origin/main...HEAD` | PASS | 无空白错误 |
| `node --check .github/scripts/workflow-check.mjs` | PASS | 语法检查通过 |
| SnakeYAML 2.6 解析 `.github/workflows/ci.yml` | PASS | `YAML_PARSE_PASS` |
| `node .github/scripts/workflow-check.mjs --base origin/main --head HEAD --branch arch/workflow-v2` | EXPECTED_FAIL | 未加标签时按设计卡在 `review:approved` |
| 同上，`PR_LABELS=["review:approved"]` | EXPECTED_FAIL | 当前尚无报告归档提交，按设计提示缺少归档报告 |
| `mvn -B -ntp verify` | PASS | 20/20 模块 SUCCESS；auth-service 27 tests，0 failure/error/skip |

首次在系统临时目录构建时，javac 关闭 `common-core` jar 失败并报 Windows `AccessDeniedException`；在同一提交的仓库内独立干净克隆重跑后 20/20 模块通过，因此判定为临时目录环境问题，不是本次代码缺陷。

## 未覆盖 / 不确定

- GitHub Actions 实际运行结果未取得：`gh` token 无效，未认证 API 对私有仓库返回 404；不能以本地自测试替代 CI。
- 未运行前端构建：本次 diff 不含前端运行时代码。
- 未启动 Docker/中间件：本次变更不涉及运行时部署配置，且门禁脚本可用本地 Git fixture 验证；当前缺少对应自动化测试。
- 未取得 PR 描述，因此未核对其中的变更说明、验证证据和回滚说明。

## 建议回归

- [ ] 修正归档 SHA 流程，确保正常提交即可满足门禁。
- [ ] 为归档提交范围与 Reviewed SHA 增加负向测试。
- [ ] 为 Feature 夹带 `deploy/**`、`.github/**`、rules/ADR 增加负向测试。
- [ ] 统一 Docs/Arch 分类和报告命名规则。
- [ ] 在修复后的新 head 上重跑 `workflow-check`、全量 Maven 和 CI。

## 归档记录

- Reviewed SHA：e3bf88b47d59168e994d00b3efb663fa7db840a0
- 归档 SHA：待报告归档提交产生后填写
- 归档提交范围：`docs/review/reports/**`（非 Feature PR 不修改任务包）
- `review:approved` 标签：待修复并复审通过后由维护者添加
