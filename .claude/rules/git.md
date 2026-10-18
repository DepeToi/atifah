---
description: Git 提交、变更边界与文档同步规则
alwaysApply: true
---

# Git 与变更边界

> 目标：让每次改动可审查、可回滚，不把无关变更混进来。

## 1. 提交权限

- **禁止自动 `git commit` / `git push`**；必须等用户明确要求。
- 提交信息用 `type: 描述`：`feat` / `fix` / `docs` / `refactor` / `chore` / `test`。
- 一个提交只做一件事；不把格式化、重命名、功能修改混在一起。
- 不提交构建产物、`target/`、`node_modules/`、`.env`、本地配置、IDE 文件。

## 2. 分支与 PR

- 默认分支 `main`；多人协作时从 `main` 拉分支，完成后走 PR 合并。
- PR 描述写清：改了什么、为什么、影响面、如何验证；关联 issue。
- 合并前至少保证编译/类型检查通过；不通过不得声称完成。

## 3. 变更边界

- 只改与任务相关的文件；不顺手重构，不新增未要求的功能。
- 计划模式下先给出改动清单（文件路径 + 改什么 + 影响面），确认后再动手。
- 长命令（`mvn package`、`npm install`）放后台执行，不要把整段编译日志贴回对话。
- 动手前先读根 `AGENTS.md`、`.claude/rules/*`、目标模块 `AGENTS.md` 与 `docs/` 对应文档。
- 发现既有实现与文档冲突：以代码现状为准，并在本次改动中修正文档。
- 有设计分歧先给两个方案与取舍，等确认再写。

## 4. 受保护文件

未经明确确认，禁止修改：

- 根 `pom.xml` 的 parent 与 BOM 版本；
- `deploy/nacos/config/*`（修改后必须提醒重新导入 Nacos）；
- `deploy/docker-compose` 端口映射；
- `deploy/sql` 已执行脚本；
- `.gitattributes` 换行设置；
- 测试与校验逻辑（禁止为通过而删除/关闭）。

## 5. 文档同步

- 改接口：先改 `docs/api-contract.md` 与 `docs/api/<service>.md`，再改 `common-core`、后端、前端 `src/api/types.ts`。
- 改路由/端口/服务名：同步 `docs/module-layout.md` 与根 `README.md` 端口表。
- 改共享配置：只改 `deploy/nacos/config/common-application.yaml`，并提醒导入 Nacos。
- 新增缓存 Key / Topic / 消费组：先登记 `CacheKeys` / `MqTopics` / `MqConsumerGroups`。
- 重要架构决策写 `docs/adr/NNNN-*.md`，不要只留在对话里。

## 6. 提交前自检

```bash
mvn -o -q -DskipTests compile
cd atifah-admin-web/frontend && npm run type-check
git diff --check        # 行尾空白 / 冲突标记
git status              # 确认没有无关文件
```
