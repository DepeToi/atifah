# Git 与变更边界（GIT）

> 目标：让每次改动可审查、可回滚，不把无关变更混进来。
> 三角色工作流的总览见 `AGENTS.md` §0；任务包规范见 `docs/tasks/README.md`；评审规则见 `docs/review/standards.md`。

## GIT-01 提交权限

- **禁止 AI 自动 `git commit` / `git push`**；必须等用户明确要求。
- 提交信息用 `type(scope): T-xxx 描述`（Feature）或 `type(scope): 摘要`（Arch/Ops/Docs）：`feat` / `fix` / `docs` / `refactor` / `chore` / `test`。非 Feature 不得伪造 `T-xxx`。
- 一个提交只做一件事；不把格式化、重命名、功能修改混在一起。
- 不提交构建产物、`target/`、`node_modules/`、`.env`、本地配置、IDE 文件。

## GIT-02 分支与 PR

- `main` 受保护，不直接提交；所有改动走 PR。
- 分支命名：
  - 架构：`arch/<topic>`（Claude）
  - 部署/CI：`ops/<topic>`（Claude）
  - 功能：`feat/T-xxx-<slug>`（CodeBuddy）
  - 修复：`fix/T-xxx-<slug>`（CodeBuddy）
  - 文档/规则：`docs/<topic>`（Claude）
- Feature 一个 PR 对应一个任务包；Arch/Ops/Docs 一个 PR 对应一个变更。单服务、生产语义 diff ≤400 行（口径见 `docs/review/standards.md` SCOPE-02）；超出请拆任务。
- PR 必须使用 `.github/pull_request_template.md`，写清改了什么、为什么、影响面、如何验证。
- 契约变更必须单独开 PR，不得混在功能 PR 里。

## GIT-03 变更边界

- Feature 只改任务包“允许修改”列出的路径；Arch/Ops/Docs 只改 PR 描述列出的路径；不顺手重构，不新增未要求的功能。
- 动手前先读根 `AGENTS.md`、目标模块 `AGENTS.md`、相关 `docs/rules/*` 与契约。
- 发现既有实现与文档冲突：以已冻结契约为准；契约本身有问题时先反馈给 Claude，不要自行改契约迁就代码。
- 有设计分歧先给两个方案与取舍，等确认再写。
- 计划模式下先给出改动清单（文件路径 + 改什么 + 影响面），确认后再动手。

## GIT-04 受保护文件

未经明确确认，禁止修改：

- 根 `pom.xml` 的 parent 与 BOM 版本；
- `docs/contracts/**`（契约变更除外，且必须由 Claude 执行）；
- `deploy/nacos/config/*`（修改后必须提醒重新导入 Nacos）；
- `deploy/docker-compose` 端口映射；
- `deploy/sql` 已执行脚本；
- `.gitattributes` 换行设置；
- 测试与校验逻辑（禁止为通过而删除/关闭）。

## GIT-05 文档同步

- 改接口：先改 `docs/contracts/` 契约，再改 `common-core`、后端、前端 `src/api/types.ts`。
- 改路由/端口/服务名：同步 `README.md` 端口表与相关契约/文档。
- 改共享配置：只改 `deploy/nacos/config/common-application.yaml`，并提醒导入 Nacos。
- 新增缓存 Key / Topic / 消费组：先登记 `CacheKeys` / `MqTopics` / `MqConsumerGroups`。
- 重要架构决策写 `docs/adr/NNNN-*.md`，不要只留在对话里。

## GIT-06 提交前自检

```bash
mvn -o -q -DskipTests compile
cd atifah-admin-web/frontend && npm run type-check
git diff --check        # 行尾空白 / 冲突标记
git status              # 确认没有无关文件
```

- 不通过不得声称完成；禁止删除测试、关校验、加 `@SuppressWarnings` 掩盖问题。

## GIT-07 三角色交接约束

- 同一工作区同一时刻只允许一个角色写文件；并行任务用独立分支或 `git worktree`。
- CodeBuddy 不得修改契约、规则、父 pom 或其他服务；需要变更时回到 Claude 走 ADR/契约 PR。Codex 只读评审，不提交修复或构建产物；修复回到实现者。
- Feature 由 CodeBuddy 实现；Arch/Ops/Docs 由 Claude 实现；两者不得混在同一个 PR。
- 交接顺序、评审流程与任务状态机见 `AGENTS.md` §0 与 `docs/tasks/README.md` §2，本文件不重复。
