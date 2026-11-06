# 评审报告（第二轮）

> 变更类型：Arch（`common-security` 公共安全能力 + ADR/规则）
> 评审对象：PR #13，分支 `arch/config-crypto`，base `ed1733f7e3a4cea9dc4b7f30e5ea0d23c7d6ef03`，head `de2cb15a0eecb2d3869d46df575680bd211cfa39`
> 任务包 / ADR：`docs/adr/0005-config-encryption.md`
> 契约基线：不涉及业务接口 / DDL / 事件契约变更
> Reviewed SHA：de2cb15a0eecb2d3869d46df575680bd211cfa39
> 评审人：Codex
> 日期：2026-11-06

## 结论

`APPROVE`

- Blocker：0
- Major：0
- Minor：1
- Question：0
- 自测试：PASS
- 自测试级别：A

## 范围检查

- [x] 变更类型与 diff 一致：公共安全能力、ADR、SEC-02 规则和部署说明属于 Arch 范围。
- [x] 必备 ADR 存在，未修改业务接口 / DDL / 事件契约。
- [x] 只修改 `atifah-common/common-security/**`、相关文档、TODO 与 `.gitignore`；未跨模块实现业务代码。
- [x] Arch 非文档代码低于 500 行阈值；`workflow-check --local-precheck` 通过。
- [x] 未新增第三方依赖，AES-256-GCM 仍为纯 JDK 实现。
- [x] 最终 HEAD 不含 `.codebuddy/settings.local.json`；历史提交中的该文件内容为空对象 `{}`，未发现凭据。
- [ ] GitHub CI、PR 标签和描述未取得：`gh` 未认证，无法读取私有仓库元数据。
- [ ] Nacos 实际导入/刷新未执行：当前环境没有 Nacos。

## 上轮问题闭环

- 主密钥现在严格要求 Base64URL 解码后为 32 字节，非法格式和错误长度均 fail-fast；已移除 JVM system property 回退。
- 主密钥存在时会遍历可枚举属性源并验证全部 `ENC(...)`；错误密钥、篡改和截断失败已前移到启动阶段。
- 解密值中的 `${` 会转义后交给 Spring 解析，直接读取、Binder 绑定、嵌套属性引用均保持字面值。
- `ConfigEncryptTool encrypt` 已禁止明文 argv，只从无回显终端或 stdin 读取；主密钥不再接受命令行来源。
- TODO、ADR、SEC-02 与 Nacos README 已同步。
- 测试由 12 个增加到 23 个，测试主密钥改为运行时生成，避免密钥扫描误报。

## 发现详情

### [Minor] TODO.md:197 — TEST-01 — 文档声称 23 个用例覆盖“截断”，但没有对应的自动化测试

- 证据：当前测试只有篡改最后一个 tag 字节的用例；在 `ConfigCryptoEnvironmentPostProcessorTest` 和 `ConfigCipherTest` 中未找到截断用例。`TODO.md` 却写“错误密钥、篡改/截断 fail-fast（23 用例覆盖）”，`TODO-config-crypto.md` 与部署 README 也声明截断已验证。
- 影响：当前实现经独立命令验证会在截断时失败关闭，不是现有功能缺陷；但后续重构可能破坏该边界而 CI 不会发现，文档中的测试覆盖声明也不准确。
- 建议：增加“不足 IV 长度”和“保留 IV 但截断 tag/ciphertext”两个显式用例；或删除“23 用例覆盖截断”的表述，避免把手工验证写成自动化覆盖。

## 自测试记录

- 级别：A
- commit：`de2cb15a0eecb2d3869d46df575680bd211cfa39`
- 工作区：`D:\Project\atifah\.review-de2cb15-r2`，由 `git worktree add --detach` 创建的独立干净检出
- 环境：Windows / JDK 21.0.2 / Maven 3.9.12 / Node 24.13.1 / Git 2.56.0

| 命令 / 检查 | 退出码 | 结果 | 关键输出 |
| --- | ---: | --- | --- |
| `git status --short --branch` | 0 | PASS | 干净 detached HEAD |
| `git diff --check origin/main...HEAD` | 0 | PASS | 无空白错误 |
| `mvn -B -ntp verify` | 0 | PASS | 20/20 模块 SUCCESS |
| common-security test suite | 0 | PASS | 23 tests，0 failure/error/skip |
| `workflow-check --base origin/main --head HEAD --branch arch/config-crypto --local-precheck` | 0 | PASS | class: arch，门禁通过 |
| 真实 CLI：keygen → stdin encrypt → decrypt | 0 | PASS | 32 字节密钥；密文往返一致 |
| CLI 尝试通过 argv 传明文 | 2 | PASS | 按设计拒绝 |
| Spring Boot 自动装配 smoke probe | 0 | PASS | `SPRING_AUTODISCOVERY_DECRYPTED=true` |
| 解密值 `${...}` 字面量与嵌套引用探针 | 0 | PASS | 普通、含前置反斜杠、嵌套引用均保持字面值 |
| 短密文与截断密文命令 | 1 | PASS | 两者均失败关闭 |

- 失败用例：无。
- 与 CI 结果是否一致：无法核对；本地构建和门禁通过，仍需等待 GitHub Actions 全绿。

## 未覆盖 / 不确定

- Nacos 实际启动与刷新链路未覆盖；已检查 Spring Cloud Alibaba 的刷新监听器会原位替换 `NacosPropertySource`，不会绕过置顶解密层。
- 本地未安装 gitleaks，未执行完整密钥扫描；最终 diff 未发现真实凭据。
- 未运行前端构建：本次不涉及前端运行时代码。
- PR 描述、标签与 GitHub Actions 结果因鉴权不可见。

## 建议回归

- [ ] 补充显式的截断密文自动化测试，或修正文档中的覆盖声明。
- [ ] CodeBuddy 将本报告原样复制为 PR 最后一个 docs-only 提交。
- [ ] 维护者等待 GitHub Actions 全绿并添加 `review:approved` 后合并；推送新代码需移除标签并复审。

## 归档记录

- Reviewed SHA：`de2cb15a0eecb2d3869d46df575680bd211cfa39`
- 归档提交范围：`docs/review/reports/**`（非 Feature PR 不修改任务包）
- 归档提交：由 `workflow-check` 按 `Reviewed SHA..HEAD` 识别，报告不记录归档提交自身 SHA
- `review:approved` 标签：APPROVE 后由维护者添加
