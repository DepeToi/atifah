#!/usr/bin/env node
/**
 * workflow-check 的回归测试（Node 内置 node:test，无第三方依赖）。
 *
 * 运行：node --test .github/scripts/workflow-check.test.mjs
 *
 * 每个用例在系统临时目录创建一个最小 Git 仓库，通过真实 commit/tag/diff
 * 验证门禁的正向与负向分支；不访问网络，不依赖 Docker 或数据库。
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const script = path.resolve(path.dirname(fileURLToPath(import.meta.url)), 'workflow-check.mjs');
const cleanEnv = { ...process.env };
for (const key of [
  'BASE_SHA',
  'HEAD_SHA',
  'HEAD_REF',
  'PR_NUMBER',
  'PR_LABELS',
  'GITHUB_EVENT_NAME',
  'GITHUB_REF_NAME',
]) {
  delete cleanEnv[key];
}
cleanEnv.GIT_CONFIG_NOSYSTEM = '1';

function sh(cwd, ...args) {
  return execFileSync('git', args, { cwd, encoding: 'utf8', env: cleanEnv }).trim();
}

function write(cwd, files) {
  for (const [relative, content] of Object.entries(files)) {
    const full = path.join(cwd, relative);
    mkdirSync(path.dirname(full), { recursive: true });
    writeFileSync(full, content, 'utf8');
  }
}

function commit(cwd, files, message) {
  write(cwd, files);
  sh(cwd, 'add', '-A');
  sh(cwd, 'commit', '-q', '-m', message);
  return sh(cwd, 'rev-parse', 'HEAD');
}

function initRepo(t) {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'workflow-check-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  sh(dir, 'init', '-q', '-b', 'main');
  sh(dir, 'config', 'user.email', 'workflow-check@example.com');
  sh(dir, 'config', 'user.name', 'workflow-check');
  sh(dir, 'config', 'core.autocrlf', 'false');
  sh(dir, 'config', 'commit.gpgsign', 'false');
  sh(dir, 'config', 'tag.gpgsign', 'false');
  commit(dir, { 'README.md': '# fixture\n' }, 'init');
  return dir;
}

function check(dir, { branch, args = [] }) {
  const result = spawnSync(
    process.execPath,
    [script, '--base', 'main', '--head', 'HEAD', '--branch', branch, ...args],
    { cwd: dir, encoding: 'utf8', env: cleanEnv },
  );
  return {
    code: result.status,
    output: `${result.stdout || ''}${result.stderr || ''}`,
  };
}

const APPROVED = ['--labels', '["review:approved"]'];

function taskText({ status = 'in-review', allowed, report = null } = {}) {
  const allowedPaths = allowed || ['atifah-services/auth-service/**'];
  return [
    '---',
    'id: T-001',
    'title: auth demo',
    'change_class: feature',
    `status: ${status}`,
    'owner: codebuddy',
    'created: 2026-01-01',
    'updated: 2026-01-01',
    'status_reason: fixture',
    'contract_baseline: arch-baseline-v0.1',
    'related_adr: []',
    '---',
    '',
    '# T-001 auth demo',
    '',
    '## 1. 目标与背景',
    '',
    '- 目标：fixture',
    '',
    '## 4. 变更范围',
    '',
    '### 允许修改',
    '',
    '```text',
    ...allowedPaths,
    '```',
    '',
    '## 11. 评审与自测试记录',
    '',
    report ? `- 评审报告：${report}` : '- 评审报告：无',
    '',
    '## 12. 状态变更记录',
    '',
    '| 日期 | 从 | 到 | 操作人 | 依据/证据 |',
    '| --- | --- | --- | --- | --- |',
    '',
  ].join('\n');
}

function reportText(sha) {
  return [
    '# 评审报告',
    '',
    `> Reviewed SHA：${sha}`,
    '> 评审人：Codex',
    '',
    '## 结论',
    '',
    '- Verdict：`APPROVE`',
    '',
  ].join('\n');
}

/**
 * 构造 Feature PR fixture：
 * base(main) → feat/T-001-demo: 任务包 + 业务代码 → （可选后续代码）→ 归档 docs-only 提交。
 */
function featureRepo(t, options = {}) {
  const dir = initRepo(t);
  if (options.tagOnFeatureBranch) {
    sh(dir, 'checkout', '-q', '-b', 'feat/T-001-demo');
    commit(
      dir,
      { 'atifah-services/auth-service/src/main/java/Baseline.java': 'class Baseline {}\n' },
      'feat: baseline commit',
    );
    sh(dir, 'tag', 'arch-baseline-v0.1');
  } else {
    sh(dir, 'tag', 'arch-baseline-v0.1');
    sh(dir, 'checkout', '-q', '-b', 'feat/T-001-demo');
  }

  const allowed = options.allowed || ['atifah-services/auth-service/**'];
  const codeSha = commit(
    dir,
    {
      'docs/tasks/T-001-demo.md': taskText({ status: options.status, allowed }),
      'atifah-services/auth-service/src/main/java/Demo.java': 'class Demo {}\n',
      ...(options.codeFiles || {}),
    },
    'feat: T-001 demo',
  );

  if (options.afterReviewFiles) {
    commit(dir, options.afterReviewFiles, 'feat: changes after review');
  }

  const short = codeSha.slice(0, 7);
  const report = `docs/review/reports/T-001-r1-${short}.md`;

  if (options.archive !== false) {
    let archivedTask = taskText({ status: options.status, allowed, report });
    if (options.mutateArchivedTask) {
      archivedTask = options.mutateArchivedTask(archivedTask, report);
    }
    const archiveFiles = { [report]: reportText(options.reviewedSha || codeSha) };
    if (options.archiveTask !== false) {
      archiveFiles['docs/tasks/T-001-demo.md'] = archivedTask;
    }
    Object.assign(archiveFiles, options.archiveFiles || {});
    commit(dir, archiveFiles, 'docs: archive review');
  }

  return { dir, codeSha, short, report };
}

function archRepo(t, options = {}) {
  const dir = initRepo(t);
  sh(dir, 'checkout', '-q', '-b', 'arch/fixture');
  const files = {
    'atifah-common/atifah-common-core/src/main/java/Fixture.java': 'class Fixture {}\n',
  };
  if (options.withAdr !== false) {
    files['docs/adr/0005-fixture.md'] = '# 0005. Fixture\n\n- 状态：已采纳\n';
  }
  const codeSha = commit(dir, files, 'arch: fixture');
  if (options.archive !== false) {
    const report = `docs/review/reports/PR-12-${codeSha.slice(0, 7)}.md`;
    commit(dir, { [report]: reportText(codeSha) }, 'docs: archive review');
    return { dir, codeSha, report };
  }
  return { dir, codeSha };
}

// ---------------------------------------------------------------------------
// Feature：正向
// ---------------------------------------------------------------------------

test('Feature PR：归档报告与 Reviewed SHA 范围通过', (t) => {
  const { dir } = featureRepo(t);
  const result = check(dir, { branch: 'feat/T-001-demo', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 0, result.output);
});

test('Feature PR：同时归档历史轮次报告时，以当前 Reviewed SHA 报告为准', (t) => {
  const dir = initRepo(t);
  sh(dir, 'tag', 'arch-baseline-v0.1');
  sh(dir, 'checkout', '-q', '-b', 'feat/T-001-demo');
  const firstSha = commit(
    dir,
    {
      'docs/tasks/T-001-demo.md': taskText(),
      'atifah-services/auth-service/src/main/java/Demo.java': 'class Demo {}\n',
    },
    'feat: T-001 demo',
  );
  const secondSha = commit(
    dir,
    { 'atifah-services/auth-service/src/main/java/Demo.java': 'class Demo { void run() {} }\n' },
    'feat: T-001 fix',
  );
  const firstReport = `docs/review/reports/T-001-r1-${firstSha.slice(0, 7)}.md`;
  const secondReport = `docs/review/reports/T-001-r2-${secondSha.slice(0, 7)}.md`;
  commit(
    dir,
    {
      [firstReport]: reportText(firstSha),
      [secondReport]: reportText(secondSha),
      'docs/tasks/T-001-demo.md': taskText({ report: secondReport }),
    },
    'docs: archive reviews',
  );
  const result = check(dir, { branch: 'feat/T-001-demo', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 0, result.output);
});

test('本地预检：ready 状态、无标签、无报告也通过', (t) => {
  const { dir } = featureRepo(t, { status: 'ready', archive: false });
  const result = check(dir, { branch: 'feat/T-001-demo', args: ['--local-precheck'] });
  assert.equal(result.code, 0, result.output);
  assert.match(result.output, /本地预检/);
});

// ---------------------------------------------------------------------------
// Feature：状态与范围
// ---------------------------------------------------------------------------

test('Feature PR：任务包仍为 ready 时失败（DOC-04）', (t) => {
  const { dir } = featureRepo(t, { status: 'ready' });
  const result = check(dir, { branch: 'feat/T-001-demo', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 1);
  assert.match(result.output, /in-review/);
});

test('Feature PR：仅在功能分支创建的 contract_baseline 标签失败（SCOPE-00）', (t) => {
  const { dir } = featureRepo(t, { tagOnFeatureBranch: true });
  const result = check(dir, { branch: 'feat/T-001-demo', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 1);
  assert.match(result.output, /不是 PR base/);
});

test('Feature PR：任务包“允许修改”不能放行 deploy/.github/ADR/根规则（SCOPE-01）', (t) => {
  const allowed = [
    'atifah-services/auth-service/**',
    'deploy/**',
    '.github/**',
    'docs/adr/**',
    'AGENTS.md',
  ];
  const { dir } = featureRepo(t, {
    allowed,
    codeFiles: {
      'deploy/docker-compose/docker-compose.extra.yml': 'services: {}\n',
      '.github/scripts/extra.mjs': 'export {};\n',
      'docs/adr/0009-fixture.md': '# ADR\n',
      'AGENTS.md': '# fixture\n',
      'atifah-services/AGENTS.md': '# fixture\n',
    },
  });
  const result = check(dir, { branch: 'feat/T-001-demo', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 1);
  assert.match(result.output, /不得夹带 Arch\/Ops\/非业务路径/);
  assert.match(result.output, /deploy\/docker-compose/);
  assert.match(result.output, /\.github\/scripts/);
  assert.match(result.output, /docs\/adr/);
});

test('Feature PR：业务代码超出任务包允许路径时失败（SCOPE-01）', (t) => {
  const { dir } = featureRepo(t, {
    codeFiles: {
      'atifah-services/other-service/src/main/java/Other.java': 'class Other {}\n',
    },
  });
  const result = check(dir, { branch: 'feat/T-001-demo', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 1);
  assert.match(result.output, /不在任务包允许路径内/);
});

// ---------------------------------------------------------------------------
// Feature：归档提交与 Reviewed SHA
// ---------------------------------------------------------------------------

test('Feature PR：Reviewed SHA 之后又推送代码时失败', (t) => {
  const { dir } = featureRepo(t, {
    afterReviewFiles: {
      'atifah-services/auth-service/src/main/java/AfterReview.java': 'class AfterReview {}\n',
    },
  });
  const result = check(dir, { branch: 'feat/T-001-demo', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 1);
  assert.match(result.output, /非归档变更/);
});

test('Feature PR：归档提交夹带业务代码时失败', (t) => {
  const { dir } = featureRepo(t, {
    archiveFiles: {
      'atifah-services/auth-service/src/main/java/Extra.java': 'class Extra {}\n',
    },
  });
  const result = check(dir, { branch: 'feat/T-001-demo', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 1);
  assert.match(result.output, /非归档变更/);
});

test('Feature PR：归档提交修改任务包 §11/§12 之外的章节时失败', (t) => {
  const { dir } = featureRepo(t, {
    mutateArchivedTask: (text) => text.replace('- 目标：fixture', '- 目标：被篡改'),
  });
  const result = check(dir, { branch: 'feat/T-001-demo', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 1);
  assert.match(result.output, /超出 §11\/§12/);
});

test('Feature PR：归档提交未在 §11 引用报告时失败（DOC-03）', (t) => {
  const { dir } = featureRepo(t, { archiveTask: false });
  const result = check(dir, { branch: 'feat/T-001-demo', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 1);
  assert.match(result.output, /§11 未引用当前归档报告/);
});

// ---------------------------------------------------------------------------
// Arch / Docs 分类与报告命名
// ---------------------------------------------------------------------------

test('Arch PR：缺少 ADR 变更时失败（SCOPE-00）', (t) => {
  const { dir } = archRepo(t, { withAdr: false });
  const result = check(dir, { branch: 'arch/fixture', args: [...APPROVED, '--pr', '12'] });
  assert.equal(result.code, 1);
  assert.match(result.output, /缺少 ADR 变更/);
});

test('Arch PR：缺少 review:approved 时失败', (t) => {
  const { dir } = archRepo(t, { archive: false });
  const result = check(dir, { branch: 'arch/fixture' });
  assert.equal(result.code, 1);
  assert.match(result.output, /review:approved/);
});

test('Arch PR：PR-<n>-<sha> 归档报告通过', (t) => {
  const { dir } = archRepo(t);
  const result = check(dir, {
    branch: 'arch/fixture',
    args: [...APPROVED, '--pr', '12'],
  });
  assert.equal(result.code, 0, result.output);
});

test('Arch PR：review:approved 但缺少归档报告时失败', (t) => {
  const { dir } = archRepo(t, { archive: false });
  const result = check(dir, {
    branch: 'arch/fixture',
    args: [...APPROVED, '--pr', '12'],
  });
  assert.equal(result.code, 1);
  assert.match(result.output, /没有符合命名的归档报告/);
});

test('Ops PR：受保护路径变更不再要求 maintainer-approved，仅输出警告', (t) => {
  const dir = initRepo(t);
  sh(dir, 'checkout', '-q', '-b', 'ops/fixture');
  const codeSha = commit(
    dir,
    { 'deploy/nacos/config/common-application.yaml': 'app:\n  name: fixture\n' },
    'ops: protected config',
  );
  const report = `docs/review/reports/PR-21-${codeSha.slice(0, 7)}.md`;
  commit(dir, { [report]: reportText(codeSha) }, 'docs: archive review');
  const result = check(dir, { branch: 'ops/fixture', args: [...APPROVED, '--pr', '21'] });
  assert.equal(result.code, 0, result.output);
  assert.match(result.output, /修改受保护路径/);
  assert.match(result.output, /deploy\/nacos\/config/);
});

test('Docs PR：docs/architecture.md 不受评审门禁约束（SCOPE-00）', (t) => {
  const dir = initRepo(t);
  sh(dir, 'checkout', '-q', '-b', 'docs/arch-note');
  commit(dir, { 'docs/architecture.md': '# 架构\n' }, 'docs: architecture note');
  const result = check(dir, { branch: 'docs/arch-note' });
  assert.equal(result.code, 0, result.output);
  assert.match(result.output, /class: docs/);
});
