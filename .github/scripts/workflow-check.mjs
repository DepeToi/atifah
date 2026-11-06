#!/usr/bin/env node
/**
 * workflow-check.mjs — 工作流机械门禁（无第三方依赖，Node 18+）
 *
 * CI：由 .github/workflows/ci.yml 在 pull_request 上调用，读取
 *   BASE_SHA / HEAD_SHA / HEAD_REF / PR_NUMBER / PR_LABELS。
 *
 * 本地（在仓库根目录执行）：
 *   node .github/scripts/workflow-check.mjs \
 *     --base origin/main --head HEAD --branch "$(git branch --show-current)" \
 *     --labels '["review:approved"]'
 *
 * 检查项：变更分类、任务包与状态、允许路径、diff 规模、分支命名、
 * 契约与 ADR 配套、受保护路径、git diff --check、归档报告。
 */

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';

const args = parseArgs(process.argv.slice(2));
if (args.help) {
  console.log('用法: node .github/scripts/workflow-check.mjs --base <ref> [--head <ref>] [--branch <name>] [--labels <json>] [--pr <number>]');
  process.exit(0);
}

const base = args.base || process.env.BASE_SHA;
const head = args.head || process.env.HEAD_SHA || 'HEAD';
const branch = args.branch || process.env.HEAD_REF || process.env.GITHUB_REF_NAME || '';
const prNumber = String(args.pr || process.env.PR_NUMBER || '');
const labels = parseLabels(args.labels ?? process.env.PR_LABELS);
const isPullRequest = process.env.GITHUB_EVENT_NAME === 'pull_request' || Boolean(args.base);

if (!base) {
  if (!isPullRequest) {
    console.log('workflow-check: 非 pull_request 事件，跳过。');
    process.exit(0);
  }
  fail('缺少 base：请传 --base 或设置 BASE_SHA。');
  finish();
}

const errors = [];
const warnings = [];

function fail(message) {
  errors.push(message);
}

function warn(message) {
  warnings.push(message);
}

function finish() {
  if (warnings.length) {
    console.log('\n警告：');
    for (const item of warnings) console.log(`  - ${item}`);
  }
  if (errors.length) {
    console.error('\nworkflow-check 未通过：');
    for (const item of errors) console.error(`  - ${item}`);
    process.exit(1);
  }
  console.log('\nworkflow-check 通过。');
  process.exit(0);
}

function parseArgs(argv) {
  const result = {};
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (key === '--base') result.base = argv[++i];
    else if (key === '--head') result.head = argv[++i];
    else if (key === '--branch') result.branch = argv[++i];
    else if (key === '--labels') result.labels = argv[++i];
    else if (key === '--pr') result.pr = argv[++i];
    else if (key === '--help') result.help = true;
  }
  return result;
}

function parseLabels(raw) {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
}

function git(...gitArgs) {
  try {
    return execFileSync('git', gitArgs, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
  } catch (error) {
    const detail = (error.stdout || '') + (error.stderr || '');
    throw new Error(`git ${gitArgs.join(' ')} 失败：${detail.trim() || error.message}`);
  }
}

function gitCheck(...gitArgs) {
  try {
    execFileSync('git', gitArgs, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
    return { ok: true, output: '' };
  } catch (error) {
    return { ok: false, output: `${error.stdout || ''}${error.stderr || ''}`.trim() };
  }
}

function changedFiles() {
  let output;
  try {
    output = git('diff', '--name-only', `${base}...${head}`);
  } catch (error) {
    fail(error.message);
    finish();
  }
  return output.split(/\r?\n/).filter(Boolean);
}

function numstat() {
  const output = git('diff', '--numstat', `${base}...${head}`);
  return output.split(/\r?\n/).filter(Boolean).map((line) => {
    const parts = line.split('\t');
    return {
      added: parts[0] === '-' ? 0 : Number(parts[0]) || 0,
      deleted: parts[1] === '-' ? 0 : Number(parts[1]) || 0,
      file: parts.slice(2).join('\t'),
    };
  });
}

function isArtifact(file) {
  return file.startsWith('docs/tasks/') || file.startsWith('docs/review/reports/');
}

function isSupporting(file) {
  // 任务包、报告、ADR 是支撑材料，不单独决定变更类型；
  // 纯 ADR PR 在 classify() 的兜底分支按 Arch 处理。
  return isArtifact(file) || file.startsWith('docs/adr/');
}

function isArchPath(file) {
  return (
    file.startsWith('docs/contracts/') ||
    file.startsWith('docs/adr/') ||
    file.startsWith('docs/rules/') ||
    file === 'docs/architecture.md' ||
    file === 'docs/domain.md' ||
    file === 'docs/db.md' ||
    file === 'AGENTS.md' ||
    file === 'CLAUDE.md' ||
    /(^|\/)AGENTS\.md$/.test(file) ||
    /(^|\/)CLAUDE\.md$/.test(file) ||
    file.startsWith('atifah-common/') ||
    file.startsWith('atifah-gateway/') ||
    file === 'pom.xml' ||
    file === 'atifah-services/pom.xml'
  );
}

function isFeaturePath(file) {
  if (file.endsWith('.md') || isArchPath(file)) return false;
  return (
    file.startsWith('atifah-services/') ||
    file.startsWith('atifah-admin-web/') ||
    file.startsWith('atifah-app/')
  );
}

function isOpsPath(file) {
  return (
    file.startsWith('deploy/') ||
    file.startsWith('.github/') ||
    file === '.dockerignore' ||
    /(^|\/)Dockerfile$/.test(file) ||
    /(^|\/)docker-compose.*\.ya?ml$/.test(file)
  );
}

function classify(files) {
  const relevant = files.filter((file) => !isSupporting(file));
  if (relevant.some(isFeaturePath)) return 'feature';
  if (relevant.some(isArchPath)) return 'arch';
  if (relevant.some(isOpsPath)) return 'ops';
  if (files.some((file) => file.startsWith('docs/adr/'))) return 'arch';
  return 'docs';
}

function isGeneratedOrDoc(file) {
  return (
    file.endsWith('.md') ||
    file.startsWith('docs/') ||
    /(^|\/)target\//.test(file) ||
    /(^|\/)node_modules\//.test(file) ||
    /(^|\/)dist\//.test(file) ||
    file.endsWith('CODEOWNERS') ||
    file.endsWith('package-lock.json') ||
    file.endsWith('.lock')
  );
}

function isSizeExcluded(file) {
  return isGeneratedOrDoc(file) || /(^|\/)src\/test\//.test(file);
}

function parseFrontMatter(text) {
  const match = text.match(/^---\r?\n([\s\S]*?)\r?\n---/);
  if (!match) return {};
  const fields = {};
  for (const line of match[1].split(/\r?\n/)) {
    const kv = line.match(/^([A-Za-z0-9_]+):\s*(.*)$/);
    if (kv) fields[kv[1]] = kv[2].replace(/\s+#.*$/, '').trim();
  }
  return fields;
}

function parseAllowedPaths(text) {
  const marker = text.search(/^### 允许修改\s*$/m);
  if (marker < 0) return [];
  const section = text.slice(marker);
  const fence = section.match(/```(?:text)?\r?\n([\s\S]*?)```/);
  if (!fence) return [];
  return fence[1]
    .split(/\r?\n/)
    .map((line) => line.replace(/#.*$/, '').trim())
    .filter(Boolean);
}

function globToRegex(glob) {
  let source = '^';
  for (let i = 0; i < glob.length; i += 1) {
    const char = glob[i];
    if (char === '*') {
      if (glob[i + 1] === '*') {
        source += '.*';
        i += 1;
      } else {
        source += '[^/]*';
      }
    } else if (char === '?') {
      source += '[^/]';
    } else {
      source += char.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }
  }
  return new RegExp(`${source}$`);
}

function findTaskPack(currentBranch) {
  const match = currentBranch.match(/T-(\d{3})/);
  if (!match) return { error: `分支 ${currentBranch} 缺少 T-xxx 编号` };
  const id = `T-${match[1]}`;
  const directory = 'docs/tasks';
  if (!existsSync(directory)) return { id, error: `缺少 ${directory}/ 目录` };
  const candidates = readdirSync(directory).filter((file) => file.startsWith(`${id}-`) && file.endsWith('.md'));
  if (candidates.length === 0) return { id, error: `未找到任务包 docs/tasks/${id}-*.md` };
  if (candidates.length > 1) return { id, error: `找到多个任务包：${candidates.join(', ')}` };
  const relPath = path.posix.join(directory, candidates[0]);
  return { id, path: relPath, file: candidates[0] };
}

function isReportFile(file, task, pr) {
  if (!file.startsWith('docs/review/reports/')) return false;
  const name = path.posix.basename(file);
  if (task) {
    return new RegExp(`^${task.id}-r\\d+-[0-9a-f]{7,40}\\.md$`).test(name);
  }
  const pattern = /^PR-\d+(-r\d+)?-[0-9a-f]{7,40}\.md$/;
  if (!pattern.test(name)) return false;
  return pr ? name.startsWith(`PR-${pr}-`) : true;
}

function isAllowed(file, task, allowed, pr) {
  if (task && file === task.path) return true;
  if (isReportFile(file, task, pr)) return true;
  return allowed.some((glob) => globToRegex(glob).test(file));
}

function isProtected(file) {
  return (
    file.startsWith('deploy/sql/') ||
    file.startsWith('deploy/nacos/config/') ||
    /^deploy\/docker-compose\/docker-compose.*\.ya?ml$/.test(file) ||
    file === '.gitattributes' ||
    file === 'pom.xml' ||
    file === 'atifah-services/pom.xml'
  );
}

function isAdrFile(file) {
  return /^docs\/adr\/(?!README\.md$|_TEMPLATE\.md$).+\.md$/.test(file);
}

function isContractFile(file) {
  if (!file.startsWith('docs/contracts/')) return false;
  const name = path.posix.basename(file);
  return !name.startsWith('_') && name !== 'README.md';
}

// ---------------------------------------------------------------------------
// 执行检查
// ---------------------------------------------------------------------------

const files = changedFiles();
const changeClass = classify(files);
const requiresReview = changeClass !== 'docs';
const stats = numstat();

console.log('workflow-check');
console.log(`  base: ${base}`);
console.log(`  head: ${head}`);
console.log(`  branch: ${branch || '(unknown)'}`);
console.log(`  class: ${changeClass}`);
console.log(`  changed files: ${files.length}`);
if (labels.length) console.log(`  labels: ${labels.join(', ')}`);

const branchRules = {
  feature: /^(feat|fix)\/T-\d{3}-/,
  arch: /^arch\//,
  ops: /^(ops|chore)\//,
  docs: /^docs\//,
};

if (branch) {
  if (!branchRules[changeClass].test(branch)) {
    fail(`分支命名 ${branch} 与变更类型 ${changeClass} 不匹配；期望 ${branchRules[changeClass]}`);
  }
} else {
  warn('未提供分支名，跳过分支命名检查。');
}

if (requiresReview && !labels.includes('review:approved')) {
  fail(`变更类型 ${changeClass} 需要 Codex 评审：APPROVE 后由维护者添加 review:approved 标签`);
}

let task = null;
if (changeClass === 'feature') {
  task = findTaskPack(branch);
  if (task.error) {
    fail(task.error);
  } else {
    const text = readFileSync(task.path, 'utf8');
    const frontMatter = parseFrontMatter(text);
    for (const field of ['status', 'updated', 'status_reason', 'contract_baseline']) {
      if (!frontMatter[field]) fail(`任务包 ${task.path} 缺少 front matter 字段 ${field}`);
    }
    if (frontMatter.change_class && frontMatter.change_class !== 'feature') {
      fail(`任务包 ${task.path} 的 change_class 为 ${frontMatter.change_class}；本模板只用于 Feature`);
    }
    if (!['ready', 'in-review'].includes(frontMatter.status)) {
      fail(`任务包 ${task.path} 的 status 为 ${frontMatter.status || '缺失'}；Feature PR 必须是 ready 或 in-review`);
    }
    const baseline = frontMatter.contract_baseline;
    if (!baseline) {
      fail(`任务包 ${task.path} 缺少 contract_baseline`);
    } else {
      try {
        git('rev-parse', '--verify', `refs/tags/${baseline}`);
        git('merge-base', '--is-ancestor', baseline, 'HEAD');
      } catch {
        fail(`契约基线标签 ${baseline} 不存在或不是 HEAD 的祖先；请先打 tag 并 fetch`);
      }
    }
    const allowed = parseAllowedPaths(text);
    if (allowed.length === 0) {
      fail(`任务包 ${task.path} 的“### 允许修改”代码块为空或无法解析`);
    } else {
      for (const file of files) {
        if (!isAllowed(file, task, allowed, prNumber)) {
          fail(`文件 ${file} 不在任务包允许路径内（允许：${allowed.join(', ')}）`);
        }
      }
    }
    const configFiles = files.filter((file) => file.startsWith('atifah-common/') || file.startsWith('atifah-gateway/') || isContractFile(file) || file === 'pom.xml' || file === 'atifah-services/pom.xml');
    if (configFiles.length) {
      fail(`Feature PR 不得夹带 Arch 路径：${configFiles.join(', ')}；请拆成 arch/* PR`);
    }
  }
} else {
  const contractFiles = files.filter(isContractFile);
  const adrFiles = files.filter(isAdrFile);

  if (changeClass === 'arch' && adrFiles.length === 0 && files.some((file) => file === 'AGENTS.md' || file.startsWith('docs/rules/'))) {
    warn('规则/入口文档变更未附 ADR；如属重要规则调整，请补充 ADR');
  }

  if (contractFiles.length) {
    if (changeClass !== 'arch') {
      fail(`契约变更必须单独走 arch/* PR；当前类型为 ${changeClass}`);
    }
    if (adrFiles.length === 0) {
      fail(`契约变更缺少 ADR：请在 docs/adr/ 新增或更新 ADR（README/_TEMPLATE 不计）`);
    }
    const codeFiles = files.filter((file) => isFeaturePath(file) || file.startsWith('atifah-common/') || file.startsWith('atifah-gateway/'));
    if (codeFiles.length) {
      fail(`契约 PR 不得夹带代码：${codeFiles.join(', ')}；请拆 PR`);
    }
  }

  if (changeClass === 'arch' && files.some(isFeaturePath)) {
    fail('Arch PR 不得夹带业务服务/管理端业务代码；请拆成 feat/* PR');
  }

  if (changeClass === 'ops') {
    const codeFiles = files.filter((file) => !isSupporting(file) && (isFeaturePath(file) || isArchPath(file)));
    if (codeFiles.length) {
      fail(`Ops PR 不得夹带业务/架构代码：${codeFiles.join(', ')}；请拆 PR`);
    }
  }

  if (changeClass === 'docs' && files.some((file) => !isArtifact(file) && !file.startsWith('docs/') && !file.endsWith('.md'))) {
    fail('Docs PR 只能修改文档；检测到其他路径');
  }
}

const protectedFiles = files.filter(isProtected);
if (protectedFiles.length && !labels.includes('maintainer-approved')) {
  fail(`修改受保护路径需要 maintainer-approved 标签：${protectedFiles.join(', ')}`);
}

const limit = changeClass === 'feature' ? 600 : changeClass === 'arch' || changeClass === 'ops' ? 500 : Infinity;
const lines = stats.filter((item) => !isSizeExcluded(item.file)).reduce((sum, item) => sum + item.added + item.deleted, 0);
if (Number.isFinite(limit) && lines > limit) {
  if (labels.includes('size-exception')) {
    warn(`非测试代码 ${lines} 行超过 ${changeClass} 上限 ${limit}，已由 size-exception 豁免`);
  } else {
    fail(`非测试代码 ${lines} 行超过 ${changeClass} 上限 ${limit}；请拆分或由维护者加 size-exception`);
  }
}
if (changeClass === 'feature') {
  const totalLines = stats.filter((item) => !isGeneratedOrDoc(item.file)).reduce((sum, item) => sum + item.added + item.deleted, 0);
  if (totalLines > 1000) {
    if (labels.includes('size-exception')) {
      warn(`含测试共 ${totalLines} 行超过 Feature 总计上限 1000，已由 size-exception 豁免`);
    } else {
      fail(`含测试共 ${totalLines} 行超过 Feature 总计上限 1000；请拆分或由维护者加 size-exception`);
    }
  }
}

const whitespace = gitCheck('diff', '--check', `${base}...${head}`);
if (!whitespace.ok) {
  fail(`git diff --check 未通过：\n${whitespace.output}`);
}

if (requiresReview && labels.includes('review:approved')) {
  const reports = files.filter((file) => isReportFile(file, task, prNumber));
  if (reports.length === 0) {
    fail('已添加 review:approved，但 PR 中没有符合命名的归档报告；APPROVE 后需由 CodeBuddy 将报告复制为最后一个 docs-only 提交');
  }
  if (task && reports.length) {
    const taskText = readFileSync(task.path, 'utf8');
    const referenced = reports.some((file) => taskText.includes(path.posix.basename(file)));
    if (!referenced) {
      fail(`任务包 ${task.path} §11 未引用归档报告路径：${reports.map((file) => path.posix.basename(file)).join(', ')}`);
    }
  }
}

finish();
