# Claude → CodeBuddy → Codex 自动化开发流水线（草案）

> 状态：草案 v0.1（未采纳，不构成规则）
> 日期：2026-11-12
> 起草：Claude（架构角色）
> 适用范围：本地 Feature 开发的编排方案；不替代 PR 评审、CI 与人工门禁
> 关联：`AGENTS.md` §0/§5、`docs/tasks/README.md` §2/§6、`docs/review/standards.md` §2/§4/§6/§7、`docs/rules/git-workflow.md` GIT-01/02/03/07、`docs/adr/0001-lightweight-ai-workflow.md`

## 0. 摘要

本方案让 Claude 作为调度器，通过本机 CLI 串起三个工具：

```text
Claude（架构/调度）
  → 拉起 CodeBuddy（开发）
  → CodeBuddy 完成后自动回到 Claude
  → Claude 自检并交给 Codex（只读预评审）
  → Codex 结果回到 Claude
  → Claude 按 [Arch] / [Impl] / [Question] 分流处理
```

核心机制不是新的 Agent 框架，而是：

1. CodeBuddy 与 Codex 都支持**非交互 CLI**；
2. Claude Code 的 Bash 工具支持**后台任务**，进程退出后会自动重新唤醒 Claude；
3. 三方通过**独立 git worktree + 文件状态 + 日志**交接，不依赖对话记忆。

默认在 `push / PR / 合并 / 契约变更` 前停下，等人工确认。方案稳定后再考虑写成 ADR 并更新 `AGENTS.md`；在此之前，现有三角色流程不变。

## 1. 背景与问题

当前 `AGENTS.md` §0 已定义 Claude / CodeBuddy / Codex 的职责与交接，但交接动作仍由人工在工具之间复制粘贴：

- Claude 写完任务包后，需要人工切到 CodeBuddy 启动开发；
- CodeBuddy 完成后，需要人工把分支/PR 信息交给 Codex；
- Codex 评审结论需要人工再带回 Claude 判断是改架构还是改实现；
- 每一轮修复都要重复上述动作，上下文容易丢失。

目标是把这些**机械交接**自动化，同时保留现有的规则边界与人工门禁。

## 2. 目标与非目标

### 2.1 目标

- 一条命令启动一个任务包对应的开发 → 自检 → 预评审 → 修复循环。
- 每个阶段有明确的输入、输出、退出码与落盘产物，失败可恢复、可审计。
- CodeBuddy 只改任务包允许路径；Codex 只读；Claude 只做调度、自检、架构判断与契约修改。
- 严格保留人工门禁：`ready` 确认、push/PR、契约变更、合并、`done`。
- 不改变现有契约、DDL、事件与业务代码结构。

### 2.2 非目标

- 不做无人值守的合并、部署或发布。
- 不替代 PR 上的官方 Codex 评审与 CI 验证。
- 不让 Claude 直接实现业务 CRUD；业务实现仍由 CodeBuddy 完成。
- 不引入新的 Agent 框架、队列或服务端组件。
- 不做多任务并发调度（首版串行；后续可扩展 worktree 级并行）。

## 3. 现状与可行性核对

### 3.1 本机 CLI

| 工具 | 版本 | 关键能力 | 状态 |
| --- | --- | --- | --- |
| `claude` | 2.1.319 | Bash 后台任务、skill/command、文件读写 | 可用 |
| `codebuddy` | 2.182.0 | `-p/--print` 非交互、`--output-format json`、`--permission-mode`、`--allowedTools`、`--append-system-prompt`、`--session-id`、`-r/--resume`、`--max-turns`、`-w/--worktree` | 已安装 |
| `codex` | codex-cli 0.167.0 | `codex exec -C <dir> -s read-only`、`codex review --uncommitted/--base`、`-o/--output-last-message`、`--json` | 已安装；当前 `codex login status` 为 **Not logged in**，运行前需 `codex login` |

### 3.2 需要验证的假设

以下行为在本文中按“预期”描述，P2 冒烟阶段必须逐项确认：

- [ ] `codebuddy` 与 Claude Code 同源，会加载仓库 `CLAUDE.md` / `AGENTS.md`；因此必须用 `--append-system-prompt` 覆盖角色，否则它可能按 `CLAUDE.md` 的“架构师”角色拒绝写业务代码。
- [ ] `codebuddy -p` 在 `--permission-mode acceptEdits` + 窄 `--allowedTools` 下可无人值守完成编辑与 `mvn` 自检；未被允许的工具会被拒绝而不是阻塞等待输入。
- [ ] `codebuddy --output-format json` 输出包含 `session_id`、`result`、`is_error` 等字段，可用于 `-r` 续跑。
- [ ] `codex review --uncommitted` 为非交互只读评审；若实际仍可能写文件，改用 `codex exec -C <worktree> -s read-only`。
- [ ] `codex review` 能读取仓库 `AGENTS.md` 与 `docs/review/standards.md` 并按其格式输出。
- [ ] Bash 后台任务退出后，Claude Code 能自动重新唤醒当前会话（不依赖轮询）。

## 4. 方案对比与选择

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| A. Claude 主会话编排（本方案） | 复用现有会话与规则；Claude 可在交接前做范围/编译自检；`[Arch]` 结果直接回到 Claude；无需新服务 | 依赖 Claude 会话持续存活；命令权限需配置 | **推荐** |
| B. 外部 shell 脚本编排 | 完全脱离对话；可进 CI/定时任务 | 需要另起 `claude -p`，上下文与规则加载不可控；出错定位困难；人工门禁不易插入 | 备选，首版不采用 |
| C. CodeBuddy 完成后由 hook 直接调 Codex | 少一次跳转 | 跳过 Claude 自检；Codex 的 `[Arch]` 结论还要回 Claude；两个工具可能同时写同一工作区；hook 可移植性差 | 否决 |
| D. Workflow / Agent 子代理直接生成 CodeBuddy/Codex | 看似统一 | `Agent`/`Workflow` 只能创建 Claude 子代理，不能直接创建 CodeBuddy/Codex；仍需 Bash 调 CLI | 不适用 |

结论：采用方案 A。CodeBuddy/Codex 都是外部进程，Claude 只做**编排与判断**，不把它们的职责合并进 Claude。

## 5. 总体设计

### 5.1 角色与职责

| 角色 | 在本流水线中的职责 | 不做什么 |
| --- | --- | --- |
| Claude（主会话） | 校验任务包、创建 worktree、拼装提示词、启动/监控后台任务、自检、分流评审、写汇报、在人工授权后推进 push/PR | 不实现业务 CRUD；不自动 commit/push；不改 `in-review`/`done` 状态 |
| CodeBuddy（子进程） | 按任务包实现代码与测试，运行任务包要求的自检命令 | 不改契约/规则/ADR/父 pom/其他服务；不 commit/push；不越出允许路径 |
| Codex（子进程） | 只读预评审，按 `docs/review/standards.md` 输出分级问题与结论 | 不改代码/测试/配置；不改任务状态；不写仓库报告文件 |
| 人类维护者 | 确认 `ready`、授权 push/PR、处理 `[Arch]` 与 `[Question]`、合并、设置 `done` | — |

### 5.2 架构图

```text
┌──────────────────────────────────────────────────────────────┐
│ Claude Code 主会话（架构 + 调度）                             │
│                                                              │
│  1. 读任务包 / 契约 / 规则                                    │
│  2. git worktree add .worktrees/T-xxx                        │
│  3. Bash(run_in_background) ──► codebuddy -p ...             │
│  4. 等待后台退出通知                                          │
│  5. 自检：范围 / 编译 / 测试 / 敏感信息                        │
│  6. Bash(run_in_background) ──► codex review --uncommitted   │
│  7. 读取评审，按 [Arch]/[Impl]/[Question] 分流                │
│  8. 修复循环或停在人工门禁                                     │
└──────────────────────────────────────────────────────────────┘
          │                                   ▲
          ▼                                   │
  .pipeline/T-xxx/                     .pipeline/T-xxx/
  codebuddy-N.json                     review-N.md
  codebuddy-N.log                      summary.md
  state.json                           verify-N.log
          │                                   ▲
          ▼                                   │
┌──────────────────────┐          ┌──────────────────────┐
│ CodeBuddy 子进程      │          │ Codex 子进程          │
│ 在独立 worktree 写代码 │          │ 只读评审同一 worktree  │
└──────────────────────┘          └──────────────────────┘
```

### 5.3 一次任务的时序

```text
人: /feature-pipeline T-004
Claude: 校验 status=ready、契约基线、工作区无冲突
Claude: 创建 worktree + 分支 feat/T-004-<slug>
Claude: 生成 dev-1.md，后台启动 CodeBuddy
CodeBuddy: 读任务包 → 实现 → 运行自检 → 退出
Claude: 收到完成通知 → 检查 JSON/日志 → 范围校验 → 编译/测试
Claude: 生成 review-1.md（含预评审快照），后台启动 Codex
Codex: 只读 diff → 输出分级问题 → 退出
Claude: 读 review-1.md
  ├─ [Arch]        → 停线，标记 blocked，等人工确认后改 ADR/契约
  ├─ [Impl]        → 生成 fix-2.md，codebuddy -r <session_id> 修复 → 回到自检
  ├─ [Question]    → 汇总给人工
  └─ LOCAL_PASS    → 写 summary.md，停在 push/PR 前
人: 授权 push / 开 PR
Claude/CodeBuddy: 按授权执行 commit/push/PR；CodeBuddy 设置 in-review
Codex（官方）: 在 PR 上按 Reviewed SHA + CI 评审
人: 合并并将任务置为 done
```

## 6. 流水线状态机

### 6.1 内部状态

```text
INIT
  → PREPARED
  → DEV_RUNNING
  → DEV_DONE
  → VERIFYING
      ├─ VERIFY_FAILED → FIXING（round++）→ VERIFYING
      └─ VERIFY_PASSED
  → PRE_REVIEW_RUNNING
  → PRE_REVIEW_DONE
      ├─ REQUEST_CHANGES + [Impl]   → FIXING（round++）→ VERIFYING
      ├─ REQUEST_CHANGES + [Arch]   → ARCH_BLOCKED
      ├─ REQUEST_CHANGES + 仅 Question → AWAITING_HUMAN
      └─ LOCAL_PASS                 → AWAITING_PUSH
  → PR_OPEN
  → PR_REVIEW（官方 Codex）
  → DONE（人工合并）
```

约束：

- `round` 从 1 开始，默认上限 3；超过上限 → `BLOCKED`，把评审与本轮修复摘要交人工。
- `ARCH_BLOCKED` 时不做代码修复，先由 Claude 更新 ADR/契约（独立 Arch 分支）；解除后任务回 `ready`，重新开始一轮。
- 任何阶段出现“无法解析的 Codex 输出 / 空输出 / 非零退出” → 不自动判通过，转 `AWAITING_HUMAN`。
- 同一 `worktree` 同时只允许一个写者；流水线用文件锁避免同一任务重复启动。

### 6.2 与任务包状态机的映射

| 流水线状态 | 任务包 `status` | 设置者 | 说明 |
| --- | --- | --- | --- |
| `INIT` / `PREPARED` | `ready` | Claude（人工已确认） | 未 `ready` 直接拒绝启动 |
| `DEV_RUNNING` ~ `AWAITING_PUSH` | `ready` | 不变 | 代码尚未形成 PR 证据 |
| `ARCH_BLOCKED` | `blocked` | Claude | 写明契约/ADR 阻塞原因与解除条件 |
| `PR_OPEN` | `in-review` | CodeBuddy | 必须有 PR URL 证据 |
| `DONE` | `done` | 人类维护者 | 合并 + CI + 评审满足要求 |

流水线**不得**设置 `in-review` 或 `done`。`in-review` 通过一次只改 front matter 的 CodeBuddy 调用来完成，避免 Claude 越权。

### 6.3 人工门禁

| 门禁 | 触发点 | 需要的人工动作 |
| --- | --- | --- |
| G1 任务可开发 | 启动前 | 确认任务包 `ready` 与契约基线 |
| G2 代码外发 | `AWAITING_PUSH` | 明确授权 commit / push / 开 PR |
| G3 架构变更 | `ARCH_BLOCKED` | 确认 ADR/契约变更，重新批准 `ready` |
| G4 合并 | `PR_REVIEW` 后 | 合并 PR、设置 `done` |
| G5 自动模式（可选） | 启动前 | 若希望跳过 G2 的人工停顿，需按任务显式预授权 |

默认只自动化到 G2 之前；G2 之后的动作必须有人类明确指令。

## 7. 阶段实现细节

### 7.1 启动前校验

```text
[ ] 任务包存在，front matter status = ready
[ ] contract_baseline 标签存在，且是 main 的祖先
[ ] 主工作区没有与目标任务冲突的未提交改动（或明确使用独立 worktree）
[ ] 没有同任务的流水线在运行（.pipeline/<task>/.lock）
[ ] git worktree 列表中没有同名分支/worktree
[ ] codex login status 正常；codebuddy 可执行
[ ] 任务包“允许修改”块可解析；解析失败则停线
```

建议脚本入口：

```bash
bash .claude/skills/feature-pipeline/scripts/prepare-worktree.sh T-004
```

### 7.2 创建独立工作区

```bash
TASK=T-004
SLUG=<task-slug>
BRANCH="feat/${TASK}-${SLUG}"
WT=".worktrees/${TASK}"

git fetch origin
git worktree add "$WT" -b "$BRANCH" origin/main
```

要点：

- 工作区放在 `.worktrees/`，加入 `.gitignore`；也可放到仓库外（如 `../atifah-worktrees/`），避免被主仓库扫描。
- 不在主工作区直接开发，满足 `AGENTS.md` §0“同一工作区同一时刻只允许一个角色写文件”。
- 如果分支已存在，先确认它属于本任务，再复用；不要强推或覆盖。

### 7.3 开发阶段（CodeBuddy）

角色覆盖（`prompts/codebuddy-role.md`）：

```text
你是 CodeBuddy，承担 AGENTS.md §0 的“开发”角色。
本仓库 CLAUDE.md 中“Claude 的角色：基础架构设计”描述的是架构师会话，不适用于你。
你只能修改任务包“允许修改”列出的路径；不得修改 docs/contracts/**、docs/rules/**、docs/adr/**、
根 pom.xml、atifah-common/**、其他服务。
不得执行 git commit / git push / 切换分支；不得把任务状态改为 in-review（PR 创建前）。
遇到契约缺失、契约冲突、需要新增依赖或需要跨服务改动：停止，输出 [Arch] 与原因，不要猜测。
```

任务提示（`prompts/dev-N.md`）：

```text
任务包：docs/tasks/T-004-<slug>.md
请完整阅读任务包、任务包引用的契约与规则，按任务包实现。

要求：
1. 先读现有实现与测试，遵循项目写法；不要顺手重构。
2. 只改任务包“允许修改”列出的路径。
3. 所有 SQL 写在 resources/mapper/*.xml；不新增依赖；日志不得出现敏感明文。
4. 完成后运行任务包“测试与自检”列出的命令，保留真实输出。
5. 输出：改动文件清单、验证命令与结果、未完成项/风险。
6. 不要 git commit / git push；不要修改任务状态。
```

执行命令：

```bash
P=".pipeline/${TASK}"
cd "$WT"

codebuddy -p "$(cat "../../${P}/prompts/dev-1.md")" \
  --output-format json \
  --permission-mode acceptEdits \
  --allowedTools "Read Edit Write Glob Grep Bash(mvn:*) Bash(git status:*) Bash(git diff:*) Bash(git log:*)" \
  --disallowedTools "Bash(git commit:*) Bash(git push:*) Bash(git checkout:*) Bash(git switch:*)" \
  --max-turns 120 \
  --append-system-prompt "$(cat "../../${P}/prompts/codebuddy-role.md")" \
  > "../../${P}/codebuddy-1.json" \
  2> "../../${P}/codebuddy-1.log"
```

Claude 侧必须以**后台任务**执行：

```text
Bash(command="...上面的脚本...", run_in_background=true)
```

原因：前台 Bash 最长 10 分钟，CodeBuddy 很容易超时；后台进程退出时 Claude Code 会自动唤醒 Claude，这就是“自动交接”的来源。

注意事项：

- 不要使用 `-y` / `--dangerously-skip-permissions`；用窄 `--allowedTools` + 明确 `--disallowedTools`。
- 前端任务需要额外允许 `Bash(npm run type-check:*)` 等命令；由任务包“自检命令”决定，不要默认全开。
- 如果任务需要删除文件，先人工确认是否允许 `Bash(rm:*)`；首版默认不放开。
- CodeBuddy 若返回 `is_error=true` 或 JSON 无法解析，不进入评审，先保留日志并转人工。

### 7.4 自检阶段（Claude）

CodeBuddy 退出后，Claude 依次执行：

```text
1. 读取 codebuddy-1.json：session_id、is_error、result。
2. 范围校验：git status --porcelain 对照任务包“允许修改”白名单。
3. diff 检查：git diff --check；确认没有契约/规则/父 pom/其他服务改动。
4. 编译：mvn -o -q -DskipTests compile（按 AGENTS.md §8）。
5. 测试：任务包列出的最小测试命令；前端任务执行 npm run type-check。
6. 敏感信息：检查日志、异常、测试输出中是否出现手机号/密码/情绪正文/量表结果。
7. 记录 verify-1.log（真实命令与输出）。
```

范围校验建议：

```bash
# 以任务包“允许修改”代码块为白名单
# 1) git -C "$WT" status --porcelain=v1 -z
# 2) 逐个路径做前缀匹配（目录以 / 结尾，文件精确匹配）
# 3) 允许任务包自身仅改 front matter（status/updated/status_reason）
# 4) 任一越界路径 → 非 0 退出，流水线停线
```

越界处理：

- 不要直接 `git reset --hard`（可能丢失人工改动）；先保存 `git diff`，再在专用 worktree 内回退越界文件。
- 越界视为 `Blocker` 级流程失败；修复后必须重新跑自检与预评审。

### 7.5 预评审阶段（Codex）

评审提示（`prompts/review-N.md`）：

```text
你是 Codex，承担 AGENTS.md §0 的“只读评审”角色。
评审对象：当前工作树的未提交改动（本地预评审）。
必读：AGENTS.md §0、docs/review/standards.md §2/§3/§4/§6/§7、任务包 docs/tasks/T-004-<slug>.md、
相关契约 docs/contracts/openapi/...、docs/contracts/db/...、docs/contracts/events/...。

检查项：
- 契约一致性：路径、字段、错误码、DDL、事件 payload。
- 范围：是否只改任务包允许路径；是否越权修改契约/规则/父 pom/其他服务。
- 安全：敏感字段、日志、密钥、鉴权、幂等（SEC-01~SEC-04）。
- 数据与消息：SQL 是否在 XML、是否显式 deleted=0、是否有 N+1、MQ 幂等/重试。
- 接口与编码：Result/PageResult、ErrorCode、@Valid、构造注入、日志占位符。
- 测试：成功/失败/边界/幂等/重复消费是否覆盖；断言是否真实。

输出要求：
- 严格按 docs/review/standards.md §6 的条目格式：规则编号、file:line、证据、影响、建议。
- 每条问题标注 [Arch] / [Impl] / [Question]；[Arch] 必须说明涉及的 ADR/契约路径。
- 本地预评审没有 Reviewed SHA 与 CI，结论只能写 LOCAL_PASS 或 REQUEST_CHANGES，
  不得写官方 APPROVE。
- 附 Pre-review snapshot: sha256:<由 Claude 传入>；列出未覆盖范围。
- 不要修改任何文件；不要写 docs/review/reports；不要执行 git commit/push。
```

执行命令：

```bash
cd "$WT"
codex review --uncommitted --title "${TASK} 本地预评审 第 1 轮" \
  "$(cat "../../${P}/prompts/review-1.md")" \
  > "../../${P}/review-1.md" 2>&1
```

说明：

- `--uncommitted` 覆盖 staged / unstaged / untracked；适合 CodeBuddy 未提交的工作树。
- 如果工作树已有提交，改用 `codex review --base main`。
- 若确认 `codex review` 不是严格只读，改用：
  ```bash
  codex exec -C "$WT" -s read-only \
    -o "../../${P}/review-1.md" \
    "$(cat "../../${P}/prompts/review-1.md")"
  ```
- 预评审快照由 Claude 计算并写入提示词，例如：
  ```bash
  SNAPSHOT=$(
    { git -C "$WT" diff --binary; \
      git -C "$WT" ls-files --others --exclude-standard -z \
        | while IFS= read -r -d '' f; do printf '%s\0' "$f"; cat "$WT/$f"; done; } \
    | sha256sum | awk '{print $1}'
  )
  ```
  该值用于把评审结论与具体改动对应起来，不替代 PR 的 Reviewed SHA。

### 7.6 分流与修复循环

| Codex 结论 | 处理者 | 动作 |
| --- | --- | --- |
| `[Arch]`（架构/契约/跨服务边界） | Claude | 停线；标记任务 `blocked`；在独立 `arch/` 分支更新 ADR/契约；人工确认后回 `ready` |
| `[Impl]` + `Blocker/Major` | CodeBuddy | 生成 fix-N.md，`codebuddy -r <session_id>` 修复；重跑自检与预评审 |
| `[Question]` | 人类 + Claude | 汇总问题与影响，等人工决策；涉及契约的按 `[Arch]` 处理 |
| 仅 `Minor` | 人类 | 可记录为后续任务；不阻塞本地预评审 |
| `LOCAL_PASS` 且无 `[Arch]` / `Blocker` / `Major` | Claude | 写 `summary.md`，进入 `AWAITING_PUSH` |

修复提示（`prompts/fix-N.md`）：

```text
Codex 预评审未通过。请只修复标注为 [Impl] 的问题。
[Arch] 问题不要通过改代码绕过；把 [Arch] 条目原样列出并停止。
评审报告：.pipeline/T-004/review-1.md
要求：
1. 保持任务包允许路径，不顺手重构。
2. 修复后重跑任务包要求的自检命令，保留真实输出。
3. 输出：修复清单、未修复项及原因、验证结果。
4. 不要 git commit / git push；不要修改任务状态。
```

续跑命令：

```bash
SID=$(jq -r '.session_id' "../../${P}/codebuddy-1.json")
codebuddy -r "$SID" -p "$(cat "../../${P}/prompts/fix-2.md")" \
  --output-format json \
  --permission-mode acceptEdits \
  --allowedTools "..." \
  --disallowedTools "..." \
  --max-turns 120 \
  --append-system-prompt "$(cat "../../${P}/prompts/codebuddy-role.md")" \
  > "../../${P}/codebuddy-2.json" \
  2> "../../${P}/codebuddy-2.log"
```

如果 `-r` 无法续跑或会话丢失：

- 不要假装续跑成功；改为新会话，把任务包、当前 diff、上一轮评审一起传入。
- 新会话更容易产生重复实现，修复成本更高；优先排查会话 ID 与 CLI 版本。

### 7.7 收口阶段

`LOCAL_PASS` 后，Claude 写 `.pipeline/<task>/summary.md`，至少包含：

```text
- 任务包 / 分支 / worktree / 预评审快照
- 改动文件与 diff stat
- 自检命令与真实结果
- Codex 预评审结论、Blocker/Major/Minor/Question 汇总
- 未覆盖范围与风险
- 建议的下一步：push / PR / 需要人工确认的问题
```

然后停线，向人类请求 G2 授权。获得明确授权后才执行：

```bash
git add <允许路径>
git commit -m "feat(<scope>): T-004 <摘要>"
git push -u origin "$BRANCH"
gh pr create --base main --title "feat(<scope>): T-004 <摘要>"
```

提交信息规范见 `docs/rules/git-workflow.md` GIT-01；PR 描述使用 `.github/pull_request_template.md`，不要用任务包正文代替。

PR 创建后，由一次只改 front matter 的 CodeBuddy 调用把任务状态改为 `in-review`：

```bash
codebuddy -r "$SID" -p "PR 已创建：<URL>。只修改任务包 front matter 的 status=in-review、updated、status_reason，不要改其他内容。"
```

官方评审仍由 Codex 在 PR 上按 `docs/review/standards.md` 执行；本地预评审不能替代 Reviewed SHA + CI 的官方结论。

## 8. 产物与状态文件

```text
.pipeline/<task>/
├── state.json                 # 编排状态（Claude/脚本写）
├── summary.md                 # 最终汇报
├── prompts/
│   ├── codebuddy-role.md
│   ├── dev-1.md
│   ├── review-1.md
│   ├── fix-2.md
│   └── ...
├── codebuddy-1.json          # CodeBuddy 原始 JSON（含 session_id）
├── codebuddy-1.log
├── verify-1.log
├── review-1.md               # Codex 预评审输出
└── diff-1.patch              # 可选的改动快照
```

`state.json` 建议字段：

```json
{
  "task": "T-004",
  "task_file": "docs/tasks/T-004-<slug>.md",
  "stage": "PRE_REVIEW_DONE",
  "round": 1,
  "max_rounds": 3,
  "branch": "feat/T-004-<slug>",
  "worktree": ".worktrees/T-004",
  "base": "main",
  "session_id": "<codebuddy session id>",
  "snapshot": "sha256:<hash>",
  "verdict": "REQUEST_CHANGES",
  "arch_blocked": false,
  "created_at": "2026-11-12T10:00:00+08:00",
  "updated_at": "2026-11-12T10:30:00+08:00"
}
```

要求：

- `.pipeline/`、`.worktrees/` 加入 `.gitignore`，不提交。
- 日志可能包含代码片段与 diff，不要放密钥；不要提交到仓库。
- `state.json` 由 Claude/脚本维护，不由 CodeBuddy/Codex 修改。
- 任务状态只改任务包 front matter；Git 历史与 PR 是流转依据（`docs/tasks/README.md` §2）。

## 9. 失败处理与重试

| 现象 | 判定 | 处理 |
| --- | --- | --- |
| CodeBuddy 非零退出 | 开发失败 | 读日志尾部，判断是否可续跑；同一错误不无限重试 |
| JSON 无法解析 / 无 `session_id` | 会话不可恢复 | 保存原始输出，转人工或新会话重来 |
| 改动越界 | 流程 `Blocker` | 保存 diff，回退越界文件，重新实现或停线 |
| 编译/测试失败 | `[Impl]` | `codebuddy -r` 修复，最多 3 轮 |
| Codex 未登录 / 非零退出 | 预评审不可用 | 停线，提示 `codex login`；不跳过评审 |
| Codex 输出为空/无结论 | 不可判读 | 不自动 PASS；转人工 |
| `[Arch]` 出现 | 架构/契约问题 | 停线，任务 `blocked`，Claude 更新 ADR/契约 |
| 3 轮仍未通过 | 超限 | `BLOCKED`，汇总评审与修复记录交人工 |
| 超时 | 进程失控 | `timeout` 包裹或人工终止；保留日志，转人工 |
| 同一任务重复启动 | 并发冲突 | 文件锁拒绝；先确认上一个进程已退出 |

建议默认值（可后续调整）：

- `max_rounds = 3`
- `max_turns = 120`（按任务规模调整）
- CodeBuddy 单次运行 `timeout 7200`（2 小时）
- Codex 单次运行 `timeout 1800`（30 分钟）
- 任何阶段失败都保留原始日志，不覆盖上一轮产物。

## 10. 安全与合规

### 10.1 与项目规则的对应

| 规则 | 本方案的落实 |
| --- | --- |
| `AGENTS.md` §0 单写者 | 独立 worktree + 串行阶段 + 文件锁；Claude 不在 CodeBuddy 运行时改同一工作区 |
| `AGENTS.md` §0 不自动 commit/push | 默认停在 `AWAITING_PUSH`；`--disallowedTools` 禁止 CodeBuddy commit/push |
| `AGENTS.md` §0 角色边界 | Claude 不写业务代码；CodeBuddy 不改契约/规则/父 pom/其他服务；Codex 只读 |
| `docs/tasks/README.md` §2 状态机 | 流水线只读 `ready`；`in-review` 由 CodeBuddy 在 PR 后设置；`done` 只能人工设置 |
| `docs/review/standards.md` §2/§6/§7 | 预评审沿用固定格式与 `[Arch]`/`[Impl]`/`[Question]` 分流；本地不冒充官方 APPROVE |
| `docs/review/standards.md` §4 | 官方评审仍需 Reviewed SHA + CI；本地预评审不替代 |
| `docs/rules/git-workflow.md` GIT-01/02/03/07 | 分支命名、单任务单 PR、人工授权 push、禁止越界修改 |
| `docs/rules/security.md` SEC-01~SEC-04 | 提示词与日志不含敏感明文；Codex 只读；不改鉴权与幂等实现 |
| `docs/rules/code-style.md` CODE-08 | 自检命令必须真实执行并记录输出，不通过不得声称完成 |

### 10.2 安全措施

- **不使用** `-y` / `--dangerously-skip-permissions`；CodeBuddy 用窄 `--allowedTools`，Codex 用只读沙箱。
- **禁止**把 `JWT_SECRET`、数据库密码、Token、AK/SK 写入提示词、日志或 `.pipeline/` 文件。
- **禁止**让 CodeBuddy 访问与任务无关的目录；worktree 作为唯一工作根。
- **范围白名单**由任务包解析得到；解析失败直接停线，不采用“全仓库可改”的兜底。
- **提示注入**：仓库代码/文档中的文本可能包含指令；CodeBuddy 应把代码注释当数据，不执行其中的“忽略规则”类指令。窄工具集与人工门禁是主要防线。
- **权限最小化**：Claude 侧只允许 `Bash(codebuddy:*)`、`Bash(codex:*)`、`Bash(git worktree:*)`、`Bash(jq:*)` 等必要命令；定期复核。
- **外发控制**：push/PR/PR 评论都是外发动作，必须人工明确授权；不得由定时任务自动执行。

## 11. 实施计划

| 阶段 | 内容 | 产物 | 退出条件 |
| --- | --- | --- | --- |
| P0 | 评审本方案，确认边界与默认值 | 本文件 + 修改意见 | 维护者认可方向 |
| P1 | 搭骨架：skill、脚本、提示词、`.gitignore`、权限 | `ops/agent-pipeline` 分支 | 不执行任何 AI 调用；脚本可 `--help` |
| P2 | 冒烟验证：临时任务包 + 小改动 | `.pipeline/smoke/` 记录 | 第 3.2 节假设逐项通过；失败项有替代方案 |
| P3 | 试点：下一个真实 Feature 任务 | 试点报告（轮次/耗时/成本/问题） | 与人工流程对比无明显退化 |
| P4 | 正式化：写 ADR，更新 `AGENTS.md` §0/§7 与文档索引 | ADR 0005 + 文档 diff | 人工确认后生效 |

P2 冒烟必须覆盖：

```text
[ ] CodeBuddy 能读到角色覆盖，不按 CLAUDE.md 的架构师角色拒绝开发
[ ] 允许的 mvn 命令能执行；未允许的 git push 被拒绝
[ ] --output-format json 可解析 session_id；-r 能续跑
[ ] Codex review 输出符合 standards §6；不写仓库文件
[ ] 范围校验能拦住越界文件
[ ] 后台任务退出能唤醒 Claude
```

P4 之前，本文件只是草案；不要把流水线写进 `AGENTS.md` 的强制规则，也不要让其他角色依赖它。

## 12. 风险与取舍

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| CodeBuddy 角色混淆 | 拒绝开发或越权修改 | `--append-system-prompt` 覆盖角色；P2 验证；范围校验兜底 |
| 本地预评审被误当官方评审 | 跳过 CI/Reviewed SHA，带病合并 | 结论只允许 `LOCAL_PASS`；PR 官方评审不可省略 |
| Codex 输出不稳定/格式漂移 | 自动分流误判 | 解析失败即停线；保留原文；不自动 PASS |
| 提示注入 | 越权改文件或泄露数据 | 窄工具集、worktree、无 push、人工门禁 |
| 成本与耗时 | 每轮双模型调用 | `max_rounds=3`、`max_turns`、超时、按任务选择模型 |
| CLI 版本变化 | 参数/输出不兼容 | 记录版本；P2/P3 前重新核对 `--help`；脚本做能力检测 |
| 权限配置过宽 | Claude 可执行任意命令 | 只允许必要命令；定期复核 settings |
| 并发写冲突 | 文件互相覆盖 | 每任务独立 worktree；同任务文件锁；首版不并行 |
| 会话丢失 | 修复轮次重复实现 | JSON 落盘、session_id 记录；失败时新会话带完整上下文 |
| 主工作区不干净 | worktree 基线不一致 | 启动前校验；从 `origin/main` 创建；拒绝带冲突启动 |

## 13. 待确认问题（后续改进）

1. 触发方式：只做 `/feature-pipeline T-xxx`，还是在任务 `ready` 后自动触发？自动触发需要 hook，风险和可解释性更差。
2. 自动边界：默认停在 push/PR 前；是否允许按任务预授权 push/PR？是否允许自动发 PR 评论？
3. 产物位置：`.pipeline/` 本地保留，还是把最终 `summary.md` 归档到 `docs/review/reports/`？
4. 模型选择：CodeBuddy 默认模型、Codex 默认模型是否按任务风险分级？
5. 轮次与预算：`max_rounds`、`max_turns`、超时与 token/费用上限如何定？
6. 预评审时机：每轮自检后都跑 Codex，还是仅在准备 push 前跑一次？
7. `Minor` / `Question`：是否自动生成后续任务包，还是只汇总给人工？
8. `[Arch]` 处理：Claude 是否在同一会话直接改 ADR/契约，还是必须另开 Arch 分支并停下等确认？
9. 适用范围：首版只支持 Feature，还是也覆盖 Arch/Ops/Docs（后三者没有任务包，需另一套输入）？
10. 测试分工：Claude 自检跑最小测试，Codex 只做静态评审，还是让 Codex 也补跑风险测试？避免与 CI 重复。
11. 主工作区有未提交改动时：拒绝启动，还是自动 stash/另建 worktree？
12. Windows/Git Bash 兼容：脚本是否只支持 Linux/Git Bash？是否需要 PowerShell 版本？
13. 权限文件：放项目 `.claude/settings.json` 还是个人 `settings.local.json`？前者团队共享但改动需评审。
14. 成本记录：是否从 CodeBuddy JSON / Codex 输出提取 token 与费用，写入 `summary.md`？
15. 失败恢复：进程被 kill 后，如何从 `state.json` 安全恢复而不重复实现？

## 14. 附录

### 14.1 CLI 参数速查（2026-11-12 核对）

CodeBuddy：

```text
-p, --print                         非交互输出
--output-format text|json|stream-json
--permission-mode <mode>            acceptEdits / bypassPermissions / default / plan / dontAsk / auto
--allowedTools <tools...>           逗号或空格分隔
--disallowedTools <tools...>
--append-system-prompt <prompt>
--system-prompt-file <path>
--session-id <id>
-r, --resume [sessionId]
--max-turns <number>
-w, --worktree [name]
```

Codex：

```text
codex exec [OPTIONS] [PROMPT]
  -C, --cd <DIR>
  -s, --sandbox read-only|workspace-write|danger-full-access
  -o, --output-last-message <FILE>
  --json
  --output-schema <FILE>

codex review [OPTIONS] [PROMPT]
  --uncommitted
  --base <BRANCH>
  --commit <SHA>
  --title <TITLE>
```

### 14.2 启动前检查清单

```bash
codebuddy --version
codex --version
codex login status
jq --version
git status --porcelain
git worktree list
git tag --list 'arch-baseline-*'
```

### 14.3 默认提示词清单

| 文件 | 用途 |
| --- | --- |
| `prompts/codebuddy-role.md` | 覆盖 CLAUDE.md 角色，声明 CodeBuddy 边界 |
| `prompts/dev-N.md` | 任务包 + 实现要求 + 自检要求 |
| `prompts/review-N.md` | Codex 只读预评审 + 固定输出格式 + 快照 |
| `prompts/fix-N.md` | 只修 `[Impl]`，`[Arch]` 停线 |

### 14.4 与现有文档的关系

- `AGENTS.md`：唯一规则入口；本方案不修改其条款，稳定后再提 ADR 并更新。
- `docs/tasks/README.md`：任务包与状态机；流水线只消费，不重写。
- `docs/review/standards.md`：评审格式与分流；预评审复用其 §6/§7，但不冒充官方结论。
- `docs/rules/git-workflow.md`：分支、提交、变更边界；流水线必须遵守。
- `docs/adr/0001-lightweight-ai-workflow.md`：三角色轻量流程；本方案是其自动化延伸。

---

## 变更记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v0.1 | 2026-11-12 | 初稿：可行性、总体设计、状态机、命令、提示词、失败处理与实施计划 |
