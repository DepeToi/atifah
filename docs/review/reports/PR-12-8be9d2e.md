# 评审报告（第二轮）

> 变更类型：Arch（含 Ops/CI 支撑变更）
> 评审对象：PR #12，分支 `arch/workflow-v2`，base `b047559395dd362688b6ff1b7ce51631db742fa8`，head `8be9d2e1f0bb3e8c92b471a4bc582b1fd495c387`
> 任务包 / ADR：`docs/adr/0004-workflow-v2.md`
> 契约基线：不涉及业务接口 / DDL / 事件契约变更
> Reviewed SHA：8be9d2e1f0bb3e8c92b471a4bc582b1fd495c387
> 评审人：Codex
> 日期：2026-11-06

## 结论

`REQUEST_CHANGES`

- Blocker：0
- Major：3
- Minor：0
- Question：0
- 自测试：FAIL（B 级构建与脚本测试通过，`workflow-check` 本地门禁失败）
- 自测试级别：B

第二轮仍有 `Major`。按 `docs/review/standards.md` §7，应升级到维护者与 Claude 重新界定范围，不再继续在本 PR 打补丁。

## 范围检查

- [x] 变更类型与实际 diff 基本一致：工作流规则、ADR、CI 门禁属于 Arch，`.github/**` 属于 Ops 支撑变更。
- [x] 必需产物：ADR 0004 存在；未修改业务接口 / DDL / 事件契约。
- [x] Feature 夹带 Arch/Ops 路径、Docs/Arch 分类、`ready` 状态放行、归档 SHA 自引用等上一轮问题已有对应修改和回归测试。
- [ ] 规模不符合 Arch/Ops 阈值：从 `origin/main` 起算的非文档、非 `src/test` 代码为 999 行，上限为 500 行。
- [ ] `workflow-check` 本地预检未通过；当前无可见 `size-exception` 标签证据。
- [ ] GitHub CI 与 PR 标签无法取得：私有仓库匿名 API 返回 404，`gh` 返回 401。
- [x] `git diff --check origin/main...HEAD` 通过。
- [x] 构建、Node 回归测试和 YAML 解析均通过。

## 发现详情

### [Major] .github/scripts/workflow-check.test.mjs:1 — SCOPE-02 — PR 非文档代码达到 999 行，超过 Arch/Ops 的 500 行上限

- 证据：`git diff --numstat origin/main...HEAD` 中，`.github/scripts/workflow-check.mjs` 为 596 行、`.github/scripts/workflow-check.test.mjs` 为 371 行、`.github/workflows/ci.yml` 为 32 行；门禁实际退出码为 1，输出“非测试代码 999 行超过 arch 上限 500”。
- 影响：在无 `size-exception` 标签时，`workflow-check` 必然失败；Arch/Ops 的变更边界无法满足，也会迫使维护者绕过规模约束。
- 建议：拆分为不超过阈值的独立 Arch/Ops PR，或由维护者添加 `size-exception` 并记录具体理由；本次评审无法读取 PR 标签，因此未把外部标签当作已豁免证据。

### [Major] .github/scripts/workflow-check.mjs:505 — SCOPE-00 — Arch PR 缺少 ADR 时仍可通过机械门禁

- 证据：只有 `AGENTS.md` 或 `docs/rules/**` 变更且无 ADR 时才产生警告；`atifah-common/**`、`atifah-gateway/**`、根/父 POM 或仅有旧 ADR 的 Arch PR 不要求 ADR。测试夹具 `archRepo()` 在只修改 `atifah-common/**`、没有新增或修改 ADR 时仍断言门禁通过。
- 影响：`AGENTS.md` §0 与评审标准规定 Arch 的必备产物为 ADR；当前测试反而固化了一个无 ADR 的合法路径，未来架构边界变更可在缺少决策记录时合并。
- 建议：当 `changeClass === 'arch'` 且没有有效的 `docs/adr/NNNN-*.md` 变更时直接失败；为“Common/Gateway/根 POM 无 ADR”增加负向测试，并在正向夹具中提供 ADR。

### [Major] .github/scripts/workflow-check.mjs:478 — SCOPE-00 — `contract_baseline` 只校验为 HEAD 的祖先，可接受功能分支自建标签

- 证据：脚本先执行 `git rev-parse --verify refs/tags/${baseline}`，再执行 `git merge-base --is-ancestor baseline HEAD`；没有校验标签位于 PR base / `main`。任务规范要求 `contract_baseline` 标签存在且是 `main` 的祖先。
- 影响：Feature 分支可以在自身提交上创建同名基线标签并推送，门禁仍会通过；任务包即可引用一个未经维护者在 `main` 冻结的“契约基线”，破坏契约冻结流程。
- 建议：校验标签提交是 PR base（CI 中的 `BASE_SHA`，本地为 `origin/main`）的祖先；增加“仅存在于功能分支的同名标签必须失败”的回归测试。

## 上轮问题闭环

- 报告不再要求记录自身归档 SHA，固定点问题已消除。
- 归档报告会解析 Reviewed SHA，并检查 `Reviewed SHA..HEAD`、HEAD 最后提交和任务 §11/§12 范围；线性归档提交场景已有测试。
- Feature 任务包中的 `deploy/**`、`.github/**`、ADR、根/模块规则不再能覆盖全局禁止路径。
- `docs/architecture.md` 已按 Docs 分类，PR 门禁只接受 `in-review`，非 Feature 报告命名已统一。
- 新增 14 个 `node:test` 用例，测试使用真实临时 Git 仓库且不依赖外部中间件。

## 自测试记录

- 级别：B
- commit：`8be9d2e1f0bb3e8c92b471a4bc582b1fd495c387`
- 工作区：`D:\Project\atifah\.review-8be9d2e-r2`，由 `git worktree add --detach` 创建的独立干净检出
- 环境：Windows / JDK 21.0.2 / Maven 3.9.12 / Node 24.13.1 / Git 2.56.0

| 命令 | 退出码 | 结果 | 关键输出 / 备注 |
| --- | ---: | --- | --- |
| `git status --short --branch` | 0 | PASS | 审查开始时为干净 detached HEAD |
| `git diff --check origin/main...HEAD` | 0 | PASS | 无空白错误 |
| `node --check .github/scripts/workflow-check.mjs` | 0 | PASS | 语法检查通过 |
| `node --check .github/scripts/workflow-check.test.mjs` | 0 | PASS | 语法检查通过 |
| `node --test .github/scripts/workflow-check.test.mjs` | 0 | PASS | 14 tests，14 pass，0 fail，25.86s |
| `mvn -B -ntp verify` | 0 | PASS | 20/20 模块 SUCCESS；auth-service 27 tests，0 failure/error/skip |
| SnakeYAML 2.6 解析 `ci.yml` 与两份 Compose YAML | 0 | PASS | 3 个文件均输出 `YAML_PARSE_PASS` |
| `node .github/scripts/workflow-check.mjs --base origin/main --head HEAD --branch arch/workflow-v2 --local-precheck` | 1 | FAIL | 非文档代码 999 行超过 arch 上限 500 |

- 失败用例：`workflow-check` 的 Arch 规模检查。
- 诊断记录：另以合成 `size-exception` 标签运行可越过规模检查并退出 0；但无法从私有仓库读取真实 PR 标签，因此不作为本次门禁通过证据。
- 与 CI 结果是否一致：无法核对，未取得 GitHub Actions 结果。

## 未覆盖 / 不确定

- GitHub Actions 实际运行结果和必需标签（`review:approved`、`size-exception`、`maintainer-approved`）未取得；不能以本地自测试替代 CI。
- 本次在 Node 24.13.1 上运行；CI 使用 Node 22，未取得 Node 22 的本地独立结果。
- 当前环境没有 Docker，未执行 `docker compose config -q`；本次 diff 未修改 Compose 文件，但已用 SnakeYAML 解析现有两份 Compose YAML。
- PR 描述未取得，Ops 变更说明、影响面、验证命令与回滚方式的完整性无法核对。
- 未运行前端构建：本次 diff 不包含前端运行时代码。

## 建议回归

- [ ] 由维护者与 Claude 决定拆分 PR 或书面批准 `size-exception`，并明确理由。
- [ ] 为缺少 ADR 的 Arch PR 增加失败测试。
- [ ] 为只在功能分支创建的 `contract_baseline` 标签增加失败测试。
- [ ] 在 Node 22 与 GitHub Actions 上重跑 `node:test`、`workflow-check`、Maven 和密钥扫描。

## 归档记录

- Reviewed SHA：`8be9d2e1f0bb3e8c92b471a4bc582b1fd495c387`
- 归档提交范围：`docs/review/reports/**`（非 Feature PR 不修改任务包）
- 归档提交：由 `workflow-check` 按 `Reviewed SHA..HEAD` 识别，报告不记录归档提交自身 SHA
- `review:approved` 标签：待维护者处理第二轮的 `Major` 后决定
