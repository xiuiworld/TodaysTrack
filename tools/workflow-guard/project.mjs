import { readFileSync, writeFileSync, mkdirSync, existsSync, lstatSync, readdirSync, cpSync } from 'node:fs';
import { resolve, relative, dirname, isAbsolute, sep, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createHash, randomUUID } from 'node:crypto';

export const projectRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const toolRoot = dirname(fileURLToPath(import.meta.url));
const sha = data => createHash('sha256').update(data).digest('hex');
const fail = message => { throw new Error(message); };
const submissionIds = ['S1', 'S2', 'S3', 'S4'];
const followUpIds = Array.from({ length: 9 }, (_, i) => 'P' + i);
const ids = [...submissionIds, ...followUpIds];
const within = (parent, child) => { const r = relative(parent, child); return r === '' || (!isAbsolute(r) && r !== '..' && !r.startsWith('..' + sep)); };
const selected = (parent, child) => child === parent || child.startsWith(parent + '/');
const unique = values => [...new Set(values)];
const broad = new Set(['.', 'vehicle', 'server', 'phone', 'docs', 'validation', 'tools']);

function safeRelative(path) {
  if (typeof path !== 'string' || !path || isAbsolute(path) || /[\\:\0\r\n]/.test(path) ||
      path.split('/').some(s => !s || ['.', '..', '.git', '.codex', '.agents', '.aws', '.workflow-guard', 'work'].includes(s)) ||
      path.startsWith('validation/music-probe') || /(?:^|\/)\.env(?:\.|$)|\.(?:pem|key|keystore|jks)$/.test(path)) fail('Unsafe scope path: ' + path);
  return path;
}

function guardedPath(root, path) {
  safeRelative(path); const result = resolve(root, path);
  if (!within(root, result)) fail('Path escaped project');
  let current = root;
  for (const part of path.split('/')) {
    current = join(current, part);
    if (existsSync(current) && lstatSync(current).isSymbolicLink()) fail('Reparse/symlink refused: ' + path);
  }
  return result;
}

export function validateProfile(p) {
  const policy = p.policy;
  if (p.profileVersion !== 3 || p.projectId !== 'todaystrack' || p.parallelism !== 1 ||
      p.workspaceMode !== 'isolated-source-copy' || p.guardVersion !== '1.2.0' ||
      policy?.requireStageApproval !== true || policy.autoAuthorizePrerequisites !== false ||
      policy.autoContinueUnapprovedStages !== false || policy.paidApiRequiresSeparateBudget !== true ||
      policy.executionEnabledByConfiguration !== false || policy.deploymentEnabled !== false) fail('Invalid project approval policy');
  if (p.execution?.defaultMode !== 'inline' || p.execution.guardEnabledByDefault !== false || p.execution.guardPrepareRequiresOptIn !== true ||
      p.submissionStages?.join(',') !== submissionIds.join(',') || p.followUpStages?.join(',') !== followUpIds.join(',')) fail('Invalid submission/default execution policy');
  if (Object.keys(p.stages ?? {}).join(',') !== ids.join(',') || !p.constraints?.length || !p.sourcePaths?.length) fail('Missing whole-project stages/constraints');
  for (const path of p.sourcePaths) {
    safeRelative(path); if (!/^[a-f0-9]{64}$/.test(p.sourceHashes?.[path] ?? '')) fail('Missing source pin: ' + path);
  }
  for (const stage of ids) {
    const s = p.stages[stage];
    if (s.track !== (submissionIds.includes(stage) ? 'submission' : 'follow-up-reference') || typeof s.allowsPaidApi !== 'boolean') fail('Invalid stage track/payment policy');
    if (!s.title || !s.copyRoots?.length || !s.allowedRoots?.length || !Array.isArray(s.prerequisitesForFullCompletion)) fail('Invalid stage catalog: ' + stage);
    for (const path of [...s.allowedRoots, ...s.copyRoots]) safeRelative(path);
    if (s.allowedRoots.some(path => broad.has(path)) || s.prerequisitesForFullCompletion.some(id => !ids.includes(id) || id === stage)) fail('Invalid stage scope/dependency');
  }
  return p;
}

export function collectSources(p, root) {
  validateProfile(p);
  return p.sourcePaths.map(path => {
    const bytes = readFileSync(guardedPath(root, path));
    if (sha(bytes) !== p.sourceHashes[path]) fail('Changed source: ' + path + '; review and re-pin before a new run');
    const content = bytes.toString('utf8').replace(/\r\n/g, '\n');
    return { relative: path, fileHash: sha(bytes), content, sha256: sha(content) };
  });
}

export function validateApproval(p, a, { allowTest = false } = {}) {
  validateProfile(p);
  if (!a || a.schemaVersion !== 1 || a.kind !== 'execution' || typeof a.userInstruction !== 'string' || !a.userInstruction.trim()) fail('Actual execution approval required');
  if (a.testOnly && !allowTest) fail('Test-only approval cannot prepare a real contract');
  if (/지금은\s*설정만|설정만\s*(?:수정|변경|만들)|configuration.only/i.test(a.userInstruction)) fail('Configuration-only request is not execution approval');
  if (!Array.isArray(a.approvedStages) || !a.approvedStages.length || new Set(a.approvedStages).size !== a.approvedStages.length ||
      a.approvedStages.some(stage => !ids.includes(stage))) fail('Nonempty explicit stage approval required');
  for (const stage of a.approvedStages) {
    const quote = a.stageEvidence?.find(e => e.stage === stage)?.quote;
    if (typeof quote !== 'string' || !quote.includes(stage) || !a.userInstruction.includes(quote) ||
        !/(진행|시작|실행|착수|start|run|implement)/i.test(quote) || /하지\s*마|아직|금지|보류|중단|do not|don't|not yet/i.test(quote)) fail('Missing/negative stage approval: ' + stage);
  }
  if (a.stageEvidence?.some(e => !a.approvedStages.includes(e.stage))) fail('Unapproved stage evidence');
  const permissions = { deviceExecution: false, paidApi: null, ...(a.permissions ?? {}) };
  if (Object.keys(permissions).some(k => !['deviceExecution', 'paidApi'].includes(k)) || typeof permissions.deviceExecution !== 'boolean') fail('Unknown permission grant');
  if (permissions.paidApi) {
    const b = permissions.paidApi;
    if (!b.quote || !a.userInstruction.includes(b.quote) || !Number.isSafeInteger(b.maxRequests) || b.maxRequests < 1 ||
        !Number.isFinite(b.maxCost) || b.maxCost <= 0 || !/^[A-Z]{3}$/.test(b.currency ?? '') ||
        /하지\s*마|금지|아직|do not|not yet/i.test(b.quote)) fail('Separate bounded paid approval required');
  }
  return { ...a, permissions };
}

export function validateScope(p, approval, scope) {
  if (!scope || scope.schemaVersion !== 1 || !scope.units || !Array.isArray(scope.evidence ?? [])) fail('Bounded scope contract required');
  const stages = Object.keys(scope.units);
  if (!stages.length || stages.some(stage => !approval.approvedStages.includes(stage)) ||
      approval.approvedStages.some(stage => !stages.includes(stage))) fail('Scope contains an unapproved or missing stage');
  for (const e of scope.evidence ?? []) {
    if (!ids.includes(e.stage) || !/^[a-f0-9]{64}$/.test(e.sha256 ?? '')) fail('Invalid prerequisite evidence');
    safeRelative(e.path);
  }
  for (const stage of stages) {
    const u = scope.units[stage], catalog = p.stages[stage];
    if (!['partial', 'full'].includes(u.completion) || !u.instruction?.trim() || !u.paths?.length || !u.artifacts?.length ||
        !u.requirements?.length || !u.checks?.length) fail('Incomplete stage unit: ' + stage);
    for (const path of u.paths) {
      safeRelative(path);
      if (broad.has(path) || !catalog.allowedRoots.some(root => selected(root, path))) fail('Path outside approved stage scope: ' + path);
    }
    for (const path of u.artifacts) {
      safeRelative(path); if (!u.paths.some(root => selected(root, path))) fail('Artifact outside unit paths');
    }
    const requirementIds = u.requirements.map(r => r.id);
    if (unique(requirementIds).length !== requirementIds.length || u.requirements.some(r => !/^[A-Z0-9_-]+$/.test(r.id) || !r.text?.trim())) fail('Invalid unit requirements');
    for (const dependency of u.dependsOn ?? []) if (!stages.includes(dependency) || dependency === stage) fail('Unapproved task prerequisite');
    if (u.completion === 'full') {
      for (const dependency of catalog.prerequisitesForFullCompletion) {
        if (scope.units[dependency]?.completion !== 'full' && !(scope.evidence ?? []).some(e => e.stage === dependency)) fail('Missing full-completion prerequisite: ' + dependency);
      }
    }
    for (const c of u.checks) {
      if (!/^[A-Z0-9_-]+$/.test(c.id ?? '') || !Array.isArray(c.argv) || !c.argv.length || c.argv.some(x => typeof x !== 'string' || /[\0\r\n]/.test(x)) ||
          !['sandbox', 'caller'].includes(c.execution) || !['local', 'device', 'paid-api'].includes(c.effect) ||
          (c.timeoutMs !== undefined && (!Number.isSafeInteger(c.timeoutMs) || c.timeoutMs < 1 || c.timeoutMs > 300000))) fail('Invalid pinned check');
      if (c.effect === 'device' && !approval.permissions.deviceExecution) fail('Device execution is not approved');
      if (c.effect === 'paid-api' && (!approval.permissions.paidApi || !catalog.allowsPaidApi)) fail('Paid API check lacks separate approved budget/stage');
      if (c.effect !== 'local' && c.execution !== 'caller') fail('External check must use caller permissions');
    }
    for (const path of u.readOnlyPaths ?? []) safeRelative(path);
  }
  return scope;
}

export function makeSpec(p, sources, { workspace, approval, scope, allowTest = false }) {
  const a = validateApproval(p, approval, { allowTest }); validateScope(p, a, scope);
  const constraints = p.constraints.map(s => '- ' + s).join('\n');
  const order = ids.filter(id => a.approvedStages.includes(id));
  const request = `오늘의 트랙 프로젝트 전체에 Workflow Guard를 적용한다.\n실제 사용자 실행 지시 원문:\n${a.userInstruction}\n` +
    `승인 단계: ${order.join(', ')}\n검증/유료 권한: ${JSON.stringify(a.permissions)}\n` +
    `이번 작업 단위: ${JSON.stringify(scope)}\n${constraints}\n` +
    'S1~S4는 제출 묶음, P0~P8은 후속 기술 분류다. S full을 P 전체/제품 완료로 승격하지 않는다. 승인하지 않은 단계/선행 작업은 실행하지 않는다. 외부 증거는 실제 코드/환경에 적용되는지 독립 리뷰로 확인한다. 비용 상한은 단순 선언으로 실행을 보장하는 보안 경계가 아니며 실제 서버 원장/검사를 요구한다.';
  const requirements = [{ id: 'AUTHORITY', text: '실제 사용자 원문/단계/파일/검사/유료 권한 범위를 유지한다. 전체 프로젝트 설정은 실행 승인이 아니다.' }];
  const tasks = [], checks = [];
  for (const stage of order) {
    const u = scope.units[stage], stageIds = u.requirements.map(r => stage + '-' + r.id);
    requirements.push(...u.requirements.map(r => ({ id: stage + '-' + r.id,
      text: `${u.completion === 'partial' ? '부분 작업이며 전체 단계 완료가 아니다. ' : '단계의 선행 증거와 실제 완료 조건까지 확인한다. '}${r.text}`,
      artifacts: u.artifacts })));
    const dependencies = unique([...(u.dependsOn ?? []), ...(u.completion === 'full' ? p.stages[stage].prerequisitesForFullCompletion.filter(d => scope.units[d]?.completion === 'full') : [])]);
    const context = unique([...p.sourcePaths.map(path => 'context/' + path), 'context/project-run-contract.md',
      ...(scope.evidence ?? []).map(e => 'context/prerequisites/' + e.stage + '/' + e.path),
      ...(u.readOnlyPaths ?? []).map(path => 'context/extra/' + path), ...(u.runtimeContextPaths ?? [])]);
    // Runtime contexts are fixed adapter files, not arbitrary read grants.
    if ((u.runtimeContextPaths ?? []).some(path => !['guard/check-driver.mjs', 'guard/check-evidence.mjs', 'guard/session.json', 'context/p0-run-contract.md'].includes(path))) fail('Invalid runtime context');
    tasks.push({ id: stage, instruction: `${constraints}\n\n현재 단계: ${stage} ${p.stages[stage].title}\n` +
      `작업 단위: ${u.instruction}\n${u.completion === 'partial' ? '부분 작업이며 전체 단계 완료가 아니다.' : '전체 단계 완료는 선언한 선행/완료 증거를 실제로 확인할 때만 보고한다.'}\n` +
      'context/project-run-contract.md와 승인 원문을 따른다. 다른 단계/유료 호출/검사 확대로 고치지 않는다. 필요 증거가 없으면 해당 의존성만 미확인으로 남긴다. 고정 검사 외부 실행은 caller 티켓을 사용하고 직접 우회하지 않는다. 도구/설정 파일은 읽기 전용이다.',
      requirementIds: ['AUTHORITY', ...stageIds], paths: u.paths, contextPaths: context, dependsOn: dependencies });
    for (const c of u.checks) checks.push({ id: stage + '-' + c.id, argv: c.argv,
      execution: c.execution, timeoutMs: c.timeoutMs ?? 60000, requirementIds: stageIds });
  }
  return { workspace: resolve(workspace), request, priorityInstruction: a.userInstruction, parallelism: 1, requirements, tasks, checks,
    goalContext: { projectId: p.projectId,
      objective: '차량 기록→주행 후 AI 실제 곡→저장/재감상→폰/앨범 소장. 전체 목표는 유지하고 현재 승인한 단계/단위만 수행한다.',
      items: ids.map(stage => ({ id: stage, text: `${a.approvedStages.includes(stage) ? '이번 승인 범위' : '이번 미승인, 실행하지 않음'}: ${p.stages[stage].title} (${p.stages[stage].track})` })),
      sources: sources.map(s => ({ id: 'goal-source:' + s.sha256, path: resolve(workspace, 'context', s.relative), sha256: s.sha256, content: s.content })), missingSources: [] } };
}

function copyTree(root, relativePath, workspace, baseline) {
  const source = guardedPath(root, relativePath); if (!existsSync(source)) return;
  if (lstatSync(source).isDirectory()) {
    for (const entry of readdirSync(source, { withFileTypes: true })) {
      if (['build', '.gradle', '.kotlin', '.idea', '.git', '.workflow-guard', 'node_modules', '__pycache__'].includes(entry.name)) continue;
      if (relativePath === 'validation/journey-probe' && entry.name === 'runs') continue;
      if (entry.name === 'local.properties' || /^\.env(?:\.|$)|\.(pem|key|keystore|jks)$/.test(entry.name)) continue;
      copyTree(root, relativePath + '/' + entry.name, workspace, baseline);
    }
  } else {
    const bytes = readFileSync(source), target = join(workspace, relativePath);
    mkdirSync(dirname(target), { recursive: true }); writeFileSync(target, bytes); baseline[relativePath] = sha(bytes);
  }
}

function verifyCopiedOriginals(root, baseline) {
  for (const [path, hash] of Object.entries(baseline)) if (!existsSync(guardedPath(root, path)) || sha(readFileSync(guardedPath(root, path))) !== hash) fail('Original changed during preparation: ' + path);
}

function prerequisiteSources(scope, root) {
  return (scope.evidence ?? []).map(e => {
    const bytes = readFileSync(guardedPath(root, e.path));
    if (sha(bytes) !== e.sha256 || bytes.includes(0)) fail('Changed/non-text prerequisite evidence');
    return { ...e, content: bytes.toString('utf8') };
  });
}

export async function checkConfiguration(p, root) {
  const sources = collectSources(p, root), pkg = JSON.parse(readFileSync(resolve(p.guardRoot, 'package.json'), 'utf8'));
  if (pkg.version !== p.guardVersion) fail('Installed Guard version changed');
  const { validateSpec } = await import(pathToFileURL(resolve(p.guardRoot, 'src/workspace.ts')).href);
  // In-memory test contracts only. No request, run, worker or store is created.
  for (const stage of ids) {
    const instruction = `SELF_TEST_ONLY: ${stage} 단계 진행해줘.`;
    const approval = { schemaVersion: 1, kind: 'execution', testOnly: true, userInstruction: instruction,
      approvedStages: [stage], stageEvidence: [{ stage, quote: `${stage} 단계 진행해줘.` }] };
    const unit = { completion: 'partial', instruction: 'SELF_TEST_ONLY: schema validation', paths: [p.stages[stage].examplePath],
      artifacts: [p.stages[stage].examplePath], requirements: [{ id: 'SCHEMA', text: 'SELF_TEST_ONLY: no product execution' }],
      checks: [{ id: 'SCHEMA', argv: [process.execPath, '--version'], effect: 'local', execution: 'sandbox' }] };
    validateSpec(makeSpec(p, sources, { workspace: root, approval, scope: { schemaVersion: 1, units: { [stage]: unit }, evidence: [] }, allowTest: true }));
  }
  const bytes = sources.reduce((n, s) => n + Buffer.byteLength(s.content), 0);
  if (bytes > 220000) fail('Pinned context leaves insufficient Guard evidence budget');
  return { project: p.projectId, guardVersion: pkg.version, defaultExecutionMode: p.execution.defaultMode, guardEnabledByDefault: false,
    submissionStages: submissionIds, followUpStages: followUpIds, supportedStages: ids, schemaValidatedStages: ids,
    pinnedSources: sources.length, sourceContextBytes: bytes, approvedStages: [], runStarted: false, productExecuted: false, paidCalls: 0 };
}

export async function prepare({ profile, root, approval, scope, confirmation, session, enableGuard = false }) {
  const a = validateApproval(profile, approval);
  if (confirmation !== 'APPROVED_STAGES_ONLY') fail('APPROVED_STAGES_ONLY confirmation required');
  if (enableGuard !== true) fail('Guard is optional and off by default; explicit --enable-guard true required');
  if (!scope) {
    if (a.approvedStages.length !== 1 || a.approvedStages[0] !== 'P0') fail('A bounded scope file is required for the approved stages');
    const { p0Unit } = await import('./p0.mjs'); scope = { schemaVersion: 1, units: { P0: p0Unit() }, evidence: [] };
  }
  validateScope(profile, a, scope);
  await checkConfiguration(profile, root);
  const sources = collectSources(profile, root), dependencies = prerequisiteSources(scope, root);
  const probeUnits = Object.entries(scope.units).filter(([, u]) => u.checks.some(c => c.argv.includes('guard/check-driver.mjs')));
  if (probeUnits.length > 1 || probeUnits.some(([stage]) => !['S1', 'P0'].includes(stage))) fail('Probe adapter belongs only to one S1/P0 unit');
  const isP0Runtime = probeUnits.length === 1;
  if (isP0Runtime) { const { validateP0Session } = await import('./p0.mjs'); validateP0Session(session); }
  const id = new Date().toISOString().replace(/[:.]/g, '-') + '-' + randomUUID().slice(0, 8);
  const base = resolve(root, '.workflow-guard', 'runs', id), workspace = join(base, 'workspace');
  if (!within(root, workspace)) fail('Stage escaped project');
  let ancestor = root;
  for (const part of relative(root, workspace).split(sep)) {
    ancestor = join(ancestor, part); if (existsSync(ancestor) && lstatSync(ancestor).isSymbolicLink()) fail('Reparse/symlink destination refused');
  }
  mkdirSync(workspace, { recursive: true });
  const baseline = {};
  for (const path of unique(a.approvedStages.flatMap(stage => profile.stages[stage].copyRoots))) copyTree(root, path, workspace, baseline);
  for (const s of sources) {
    const target = join(workspace, 'context', s.relative); mkdirSync(dirname(target), { recursive: true }); writeFileSync(target, s.content);
  }
  for (const e of dependencies) {
    const target = join(workspace, 'context/prerequisites', e.stage, e.path); mkdirSync(dirname(target), { recursive: true }); writeFileSync(target, e.content);
  }
  for (const u of Object.values(scope.units)) for (const path of u.readOnlyPaths ?? []) {
    const bytes = readFileSync(guardedPath(root, path));
    if (bytes.includes(0)) fail('Explicit context must be text');
    const target = join(workspace, 'context/extra', path); mkdirSync(dirname(target), { recursive: true }); writeFileSync(target, bytes);
  }
  cpSync(join(toolRoot, 'project-run-contract.md'), join(workspace, 'context/project-run-contract.md'));
  if (isP0Runtime) {
    mkdirSync(join(workspace, 'guard'), { recursive: true });
    for (const name of ['check-driver.mjs', 'check-evidence.mjs']) cpSync(join(toolRoot, name), join(workspace, 'guard', name));
    cpSync(join(toolRoot, 'p0-run-contract.md'), join(workspace, 'context/p0-run-contract.md'));
    writeFileSync(join(workspace, 'guard/session.json'), JSON.stringify({ ...session, runId: id, sessionNonce: randomUUID() }, null, 2) + '\n');
    writeFileSync(join(workspace, 'validation/vehicle-sample/local.properties'), 'sdk.dir=' + session.sdkRoot.replaceAll('\\', '/') + '\n');
  }
  verifyCopiedOriginals(root, baseline); collectSources(profile, root); prerequisiteSources(scope, root);
  const spec = makeSpec(profile, sources, { workspace, approval: a, scope });
  const { validateSpec } = await import(pathToFileURL(resolve(profile.guardRoot, 'src/workspace.ts')).href); validateSpec(spec);
  // Verify all declared text context is actually present and fits the native budget.
  const contextPaths = unique(spec.tasks.flatMap(t => t.contextPaths));
  const contextBytes = contextPaths.reduce((n, path) => {
    const bytes = readFileSync(join(workspace, path)); if (bytes.includes(0)) fail('Binary required context'); return n + bytes.length;
  }, 0);
  if (contextBytes > 260000) fail('Context leaves insufficient room for source/evidence; narrow the approved unit without weakening acceptance');
  writeFileSync(join(base, 'original-baseline.json'), JSON.stringify({ root, sourceHashes: profile.sourceHashes, files: baseline }, null, 2) + '\n');
  writeFileSync(join(base, 'approval.json'), JSON.stringify(a, null, 2) + '\n');
  const specPath = join(base, 'stage.spec.json'); writeFileSync(specPath, JSON.stringify(spec, null, 2) + '\n');
  return { specPath, workspace, approvedStages: a.approvedStages, runStarted: false, productExecuted: false,
    next: 'Review the exact user approval, unit scope and evidence; explicitly start this spec only within the existing user authorization. No automatic start/copy-back.' };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [command, ...args] = process.argv.slice(2), p = JSON.parse(readFileSync(join(toolRoot, 'project.profile.json'), 'utf8'));
    let result;
    if (command === 'check' && !args.length) result = await checkConfiguration(p, projectRoot);
    else if (command === 'prepare') {
      const options = {};
      if (args.length % 2) fail('Expected option/value pairs');
      for (let i = 0; i < args.length; i += 2) {
        if (!['--approval-file', '--scope-file', '--confirm', '--session-file', '--enable-guard'].includes(args[i]) || args[i] in options) fail('Unknown/duplicate option');
        options[args[i]] = args[i + 1];
      }
      const json = path => path ? JSON.parse(readFileSync(path, 'utf8')) : undefined;
      result = await prepare({ profile: p, root: projectRoot, approval: json(options['--approval-file']),
        scope: json(options['--scope-file']), confirmation: options['--confirm'], session: json(options['--session-file']), enableGuard: options['--enable-guard'] === 'true' });
    } else fail('Usage: node tools/workflow-guard/project.mjs check | prepare --approval-file FILE --scope-file FILE --confirm APPROVED_STAGES_ONLY --enable-guard true [--session-file FILE]. Never starts a run.');
    process.stdout.write(JSON.stringify(result, null, 2) + '\n');
  } catch (error) { process.stderr.write(error.message + '\n'); process.exitCode = 1; }
}
