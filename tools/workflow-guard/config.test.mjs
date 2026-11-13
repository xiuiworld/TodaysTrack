import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { validateProfile, collectSources, makeSpec, validateApproval, prepare } from './project.mjs';
import { p0Unit } from './p0.mjs';
import { verifyEvidence } from './check-evidence.mjs';

const root = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const profile = () => JSON.parse(readFileSync(new URL('./project.profile.json', import.meta.url), 'utf8'));
const approval = (...stages) => ({ schemaVersion: 1, kind: 'execution', testOnly: true,
  userInstruction: 'SELF_TEST_ONLY: ' + stages.map(s => `${s} 단계 진행해줘.`).join(' '),
  approvedStages: stages, stageEvidence: stages.map(stage => ({ stage, quote: `${stage} 단계 진행해줘.` })),
  permissions: { deviceExecution: false, paidApi: null } });
const unit = (p, stage) => ({ completion: 'partial', instruction: 'SELF_TEST_ONLY: contract shape, not product work',
  paths: [p.stages[stage].examplePath], artifacts: [p.stages[stage].examplePath],
  requirements: [{ id: 'OBSERVE', text: 'SELF_TEST_ONLY: schema and authority isolation' }],
  checks: [{ id: 'UNIT', argv: ['node', '--version'], effect: 'local', execution: 'sandbox' }] });
const scope = (p, ...stages) => ({ schemaVersion: 1, units: Object.fromEntries(stages.map(s => [s, unit(p, s)])), evidence: [] });
const compile = (p, a, s) => makeSpec(p, collectSources(p, root), { workspace: root, approval: a, scope: s, allowTest: true });

test('configuration approval and empty stage sets authorize no execution', () => {
  const p = profile();
  assert.throws(() => validateApproval(p, { kind: 'configuration', approvedStages: [] }), /execution|approval/i);
  const a = approval('P0'); a.userInstruction = '프로젝트 전체 설정만 수정해. P0 실행은 아직 하지 마.';
  a.stageEvidence = [{ stage: 'P0', quote: 'P0 실행은 아직 하지 마.' }];
  assert.throws(() => validateApproval(p, a, { allowTest: true }), /configuration|negative|approval/i);
  assert.throws(() => validateApproval(p, approval(), { allowTest: true }), /stage/i);
});

test('project policy refuses parallelism, automatic prerequisite approval, or another project', () => {
  for (const edit of [p => { p.parallelism = 2; }, p => { p.policy.autoAuthorizePrerequisites = true; },
    p => { p.projectId = 'windroid-p4-p5'; }]) {
    const p = profile(); edit(p); assert.throws(() => validateProfile(p));
  }
});

test('prepare rejects fixture approvals before preparing any execution workspace', async () => {
  await assert.rejects(prepare({ profile: profile(), root, approval: approval('P0'),
    confirmation: 'APPROVED_STAGES_ONLY' }), /test.only/i);
  await assert.rejects(prepare({ profile: profile(), root, approval: undefined,
    confirmation: 'APPROVED_STAGES_ONLY' }), /approval/i);
});

test('P0 remains an optional bounded stage recipe inside the whole-project policy', () => {
  const p = profile(), a = approval('P0'); a.permissions.deviceExecution = true;
  const spec = compile(p, a, { schemaVersion: 1, units: { P0: p0Unit() }, evidence: [] });
  assert.deepEqual(spec.tasks.map(t => t.id), ['P0']);
  assert.equal(spec.checks[0].execution, 'caller');
  assert.equal(spec.goalContext.items.length, 13);
});

test('submission bundles and follow-up references each keep only the approved unit', () => {
  const p = profile();
  for (const stage of Object.keys(p.stages)) {
    const spec = compile(p, approval(stage), scope(p, stage));
    assert.equal(spec.parallelism, 1);
    assert.deepEqual(spec.tasks.map(t => t.id), [stage]);
    assert.equal(spec.goalContext.projectId, 'todaystrack');
    assert.equal(spec.goalContext.items.length, 13);
    assert(spec.tasks[0].instruction.includes('전체 단계 완료가 아니다'));
    for (const constraint of p.constraints) assert(spec.tasks[0].instruction.includes(constraint));
  }
});

test('approved submission bundles keep dependencies without adding old P phases', () => {
  const p = profile(), s = scope(p, 'S1', 'S2'); s.units.S2.dependsOn = ['S1'];
  const spec = compile(p, approval('S1', 'S2'), s);
  assert.deepEqual(spec.tasks.map(t => t.id), ['S1', 'S2']);
  assert.deepEqual(spec.tasks[1].dependsOn, ['S1']);
  for (const check of spec.checks) {
    const stage = check.id.split('-')[0];
    assert(check.requirementIds.every(id => id.startsWith(stage + '-')));
    assert(!check.requirementIds.includes('AUTHORITY'));
  }
});

test('unapproved units, broad scopes, and missing full-completion prerequisites fail closed', () => {
  const p = profile();
  assert.throws(() => compile(p, approval('P1'), scope(p, 'P1', 'P2')), /unapproved|scope/i);
  const broad = scope(p, 'P1'); broad.units.P1.paths = ['.'];
  assert.throws(() => compile(p, approval('P1'), broad), /scope|path/i);
  const full = scope(p, 'S3'); full.units.S3.completion = 'full';
  assert.throws(() => compile(p, approval('S3'), full), /prerequisite/i);
});

test('stage approval alone cannot enable paid or device checks', () => {
  const p = profile(), s = scope(p, 'P5'); s.units.P5.checks[0].effect = 'paid-api';
  assert.throws(() => compile(p, approval('P5'), s), /paid/i);
  const d = scope(p, 'P6'); d.units.P6.checks[0].effect = 'device';
  assert.throws(() => compile(p, approval('P6'), d), /device/i);
});

test('separate paid authorization is bounded and appears in the immutable contract', () => {
  const p = profile(), a = approval('S3'), s = scope(p, 'S3');
  a.userInstruction += ' SELF_TEST_ONLY: 추가 음악 API 최대 1회, 총액 USD 1 승인.';
  a.permissions.paidApi = { quote: '추가 음악 API 최대 1회, 총액 USD 1 승인.', maxRequests: 1, maxCost: 1, currency: 'USD' };
  s.units.S3.checks[0].effect = 'paid-api';
  s.units.S3.checks[0].execution = 'caller';
  const spec = compile(p, a, s);
  assert(spec.request.includes('maxRequests'));
  a.permissions.paidApi.maxRequests = 0;
  assert.throws(() => compile(p, a, s), /paid/i);
});

test('changed source cannot silently reuse the prepared contract', () => {
  const p = profile(); p.sourceHashes['docs/design/README.md'] = '0'.repeat(64);
  assert.throws(() => collectSources(p, root), /changed source/i);
});


test('deadline plan has exactly four submission bundles; Guard is off by default', () => {
  const p = profile();
  assert.deepEqual(p.submissionStages, ['S1', 'S2', 'S3', 'S4']);
  assert.equal(p.execution.defaultMode, 'inline');
  assert.equal(p.execution.guardEnabledByDefault, false);
  assert.equal(p.execution.guardPrepareRequiresOptIn, true);
  assert.equal(p.followUpStages.length, 9);
  const spec = compile(p, approval('S1'), scope(p, 'S1'));
  assert.deepEqual(spec.tasks.map(t => t.id), ['S1']);
  for (const id of p.followUpStages) assert(spec.goalContext.items.some(i => i.id === id && i.text.includes('미승인')));
  for (const edit of [p => { p.execution.guardEnabledByDefault = true; }, p => { p.submissionStages.pop(); }]) {
    const changed = profile(); edit(changed); assert.throws(() => validateProfile(changed));
  }
});

test('even a valid-shaped stage approval cannot prepare Guard without deliberate opt-in', async () => {
  // Negative configuration test only; no actual approval file/spec/run is created.
  const a = approval('S1'); delete a.testOnly; a.userInstruction = 'S1 단계 진행해줘.';
  await assert.rejects(prepare({ profile: profile(), root, approval: a,
    confirmation: 'APPROVED_STAGES_ONLY' }), /optional|off by default|enable-guard/i);
});

test('S1 probe recipe is partial and cannot stand for all submission collection work', () => {
  const p = profile(), a = approval('S1'); a.permissions.deviceExecution = true;
  const u = p0Unit({ withinSubmission: true });
  assert.equal(u.completion, 'partial');
  const spec = compile(p, a, { schemaVersion: 1, units: { S1: u }, evidence: [] });
  assert.deepEqual(spec.tasks.map(t => t.id), ['S1']);
  assert(spec.tasks[0].instruction.includes('전체 단계 완료가 아니다'));
});

test('S2 remains fixture-only even when a separate paid budget exists', () => {
  const p = profile(), a = approval('S2'), s = scope(p, 'S2');
  a.userInstruction += ' SELF_TEST_ONLY: 추가 음악 API 최대 1회, 총액 USD 1 승인.';
  a.permissions.paidApi = { quote: '추가 음악 API 최대 1회, 총액 USD 1 승인.', maxRequests: 1, maxCost: 1, currency: 'USD' };
  s.units.S2.checks[0].effect = 'paid-api'; s.units.S2.checks[0].execution = 'caller';
  assert.throws(() => compile(p, a, s), /paid/i);
});

test('S4 packaging does not grant product source edits or incomplete full completion', () => {
  const p = profile(), s = scope(p, 'S4');
  s.units.S4.paths = [p.stages.S1.examplePath];
  assert.throws(() => compile(p, approval('S4'), s), /scope/i);
  const full = scope(p, 'S4'); full.units.S4.completion = 'full';
  assert.throws(() => compile(p, approval('S4'), full), /prerequisite/i);
});

function fixture() {
  return {
    selfTest: true, runId: 'SELF_TEST', environment: 'SIMULATED', bootId: 'boot-1', servicePid: 123,
    packageName: 'ai.pleos.playground.sample.vehicle',
    target: { fingerprint: 'Pleos/example:14/test', serial: '127.0.0.1:5661', androidUser: 10 },
    sdk: { vehicle: '2.3.2', fusedLocation: '1.1.3' }, outcome: 'supported',
    visibility: { elapsedMs: 20, allActivitiesInvisible: true,
      systemState: 'ai.pleos.playground.sample.vehicle/.LifecycleProbeActivity state=STOPPED visible=false' },
    controls: [
      { id: 'FG', elapsedMs: 10, acknowledged: true, location: [1, 1], odometerKm: 100 },
      { id: 'BG1', elapsedMs: 30, acknowledged: true, location: [2, 2], odometerKm: 101 },
      { id: 'BG2', elapsedMs: 50, acknowledged: true, location: [3, 3], odometerKm: 102 }],
    events: [
      { seq: 1, kind: 'sdk', source: 'location', elapsedMs: 11, value: [1, 1], pid: 123, bootId: 'boot-1' },
      { seq: 2, kind: 'sdk', source: 'odometer', elapsedMs: 12, value: 100, pid: 123, bootId: 'boot-1' },
      { seq: 3, kind: 'sdk', source: 'location', elapsedMs: 31, value: [2, 2], pid: 123, bootId: 'boot-1' },
      { seq: 4, kind: 'sdk', source: 'odometer', elapsedMs: 32, value: 101, pid: 123, bootId: 'boot-1' },
      { seq: 5, kind: 'sdk', source: 'location', elapsedMs: 51, value: [3, 3], pid: 123, bootId: 'boot-1' },
      { seq: 6, kind: 'sdk', source: 'odometer', elapsedMs: 52, value: 102, pid: 123, bootId: 'boot-1' }],
    observationEndElapsedMs: 70, remainedInvisibleThroughObservation: true,
    shutdown: { elapsedMs: 80, listenersReleased: true, writerClosed: true, lastSeq: 6 },
    cleanup: { restored: true, details: 'fixture only' }
  };
}

test('supported requires both SDK sources after actual invisibility and new inputs', () => {
  const report = verifyEvidence(fixture(), { allowSelfTest: true });
  assert.equal(report.outcome, 'supported');
  for (const edit of [
    e => { e.visibility.systemState = 'activity PAUSED visible=true'; },
    e => { e.controls[1].elapsedMs = 15; },
    e => { e.events[2].value = [1, 1]; },
    e => { e.events[2].cached = true; },
    e => { e.events[4].pid = 456; },
    e => { e.shutdown.writerClosed = false; },
    e => { e.cleanup.restored = false; },
    e => { e.events.push({ ...e.events[5], seq: 7, elapsedMs: 81 }); }
  ]) { const e = fixture(); edit(e); assert.throws(() => verifyEvidence(e, { allowSelfTest: true })); }
});

test('UI returning during the observation window invalidates the background claim', () => {
  const e = fixture();
  e.events.splice(4, 0, { seq: 5, kind: 'activity', phase: 'resumed', elapsedMs: 40, pid: 123, bootId: 'boot-1' });
  e.events[5].seq = 6; e.events[6].seq = 7; e.shutdown.lastSeq = 7;
  assert.throws(() => verifyEvidence(e, { allowSelfTest: true }), /visible|activity/i);
});

test('a supported label cannot hide missing SDK data; inconclusive cannot pass', () => {
  const e = fixture(); e.events = e.events.filter(x => x.seq < 3 || x.source !== 'location');
  e.shutdown.lastSeq = e.events.at(-1).seq;
  assert.throws(() => verifyEvidence(e, { allowSelfTest: true }), /outcome/i);
  e.outcome = 'unsupported';
  assert.equal(verifyEvidence(e, { allowSelfTest: true }).perSource.location, 'missing');
  e.outcome = 'inconclusive';
  assert.throws(() => verifyEvidence(e, { allowSelfTest: true }), /inconclusive/i);
  assert.throws(() => verifyEvidence(fixture()), /self.test/i);
});
