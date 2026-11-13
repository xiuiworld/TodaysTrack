import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const fail = message => { throw new Error(message); };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const finite = n => Number.isFinite(n) && n >= 0;
const sha = bytes => createHash('sha256').update(bytes).digest('hex');

// This checks consistency, not authenticity. Independent Guard review must also
// compare the actual caller-check execution and the probe/runner sources.
export function verifyEvidence(e, { allowSelfTest = false } = {}) {
  if (e.selfTest && !allowSelfTest) fail('Self-test evidence cannot certify P0');
  if (e.environment !== 'SIMULATED' || !e.runId || !e.bootId || !Number.isInteger(e.servicePid)) fail('Invalid run identity');
  if (e.packageName !== 'ai.pleos.playground.sample.vehicle' || !e.target?.fingerprint?.includes('Pleos/')) fail('Wrong target');
  if (e.sdk?.vehicle !== '2.3.2' || e.sdk?.fusedLocation !== '1.1.3') fail('SDK mismatch');
  const v = e.visibility;
  if (!v?.allActivitiesInvisible || !finite(v.elapsedMs) ||
      !v.systemState?.includes(e.packageName) || !/\b(?:state=STOPPED|mState=STOPPED)\b/.test(v.systemState) ||
      !/\b(?:visible=false|mVisibleRequested=false)\b/.test(v.systemState)) fail('Actual invisible/STOPPED evidence required');
  const controls = e.controls;
  if (!Array.isArray(controls) || controls.length !== 3 || controls.map(c => c.id).join(',') !== 'FG,BG1,BG2') fail('FG/BG1/BG2 controls required');
  for (const c of controls) {
    if (!finite(c.elapsedMs) || !c.acknowledged || !Array.isArray(c.location) || c.location.length !== 2 ||
        !c.location.every(Number.isFinite) || !finite(c.odometerKm)) fail('Invalid acknowledged control');
  }
  if (!(controls[0].elapsedMs < v.elapsedMs && v.elapsedMs < controls[1].elapsedMs && controls[1].elapsedMs < controls[2].elapsedMs)) fail('Inputs must follow actual invisibility');
  if (new Set(controls.map(c => JSON.stringify(c.location))).size !== 3 ||
      !(controls[0].odometerKm < controls[1].odometerKm && controls[1].odometerKm < controls[2].odometerKm)) fail('New distinct inputs required');
  if (!finite(e.observationEndElapsedMs) || e.observationEndElapsedMs <= controls[2].elapsedMs ||
      !e.remainedInvisibleThroughObservation) fail('Bounded invisible observation window required');
  if (!Array.isArray(e.events) || !e.events.length) fail('SDK journal required');
  let seq = -1, elapsed = -1;
  for (const event of e.events) {
    if (!Number.isInteger(event.seq) || event.seq <= seq || !finite(event.elapsedMs) || event.elapsedMs < elapsed ||
        event.bootId !== e.bootId || event.pid !== e.servicePid) fail('Journal seq/clock/PID mismatch');
    seq = event.seq; elapsed = event.elapsedMs;
    if (event.kind === 'activity' && ['started', 'resumed'].includes(event.phase) &&
        event.elapsedMs > v.elapsedMs && event.elapsedMs <= e.observationEndElapsedMs) fail('Activity became visible during observation');
  }
  const matches = (c, source) => e.events.some(event => event.kind === 'sdk' && event.source === source &&
    event.cached !== true && event.freshness !== 'stale' && event.quality !== 'stale' &&
    (event.sourceTimestampKind !== 'elapsed' || (finite(event.sourceTimestampMs) && event.sourceTimestampMs >= c.elapsedMs)) &&
    event.elapsedMs > c.elapsedMs && event.elapsedMs <= e.observationEndElapsedMs &&
    same(event.value, source === 'location' ? c.location : c.odometerKm));
  if (!matches(controls[0], 'location') || !matches(controls[0], 'odometer')) fail('Foreground SDK baseline missing');
  const perSource = Object.fromEntries(['location', 'odometer'].map(source => [source,
    controls.slice(1).every(c => matches(c, source)) ? 'observed' : 'missing']));
  const outcome = Object.values(perSource).every(value => value === 'observed') ? 'supported' : 'unsupported';
  if (e.outcome === 'inconclusive') fail('Inconclusive is not accepted completion');
  if (e.outcome !== outcome) fail('Declared outcome does not match SDK observations');
  const stop = e.shutdown;
  if (!stop?.listenersReleased || !stop.writerClosed || !finite(stop.elapsedMs) || stop.elapsedMs < e.observationEndElapsedMs ||
      stop.lastSeq !== seq || e.events.some(event => event.elapsedMs > stop.elapsedMs)) fail('Final shutdown/seq evidence invalid');
  if (!e.cleanup?.restored || typeof e.cleanup.details !== 'string' || !e.cleanup.details.trim()) fail('Cleanup/restore evidence required');
  return { runId: e.runId, outcome, perSource, experimentCompleted: true, productCompleted: false };
}

export function loadEvidence(dir, { nonce } = {}) {
  const e = JSON.parse(readFileSync(resolve(dir, 'evidence.json'), 'utf8'));
  const raw = Object.fromEntries(['sdk-events.jsonl', 'controls.json', 'system-state.txt'].map(name => [name, readFileSync(resolve(dir, name))]));
  for (const [name, bytes] of Object.entries(raw)) if (e.sourceHashes?.[name] !== sha(bytes)) fail('Source evidence hash mismatch: ' + name);
  if (nonce && e.sessionNonce !== nonce) fail('Old or mismatched evidence nonce');
  e.events = raw['sdk-events.jsonl'].toString('utf8').trim().split(/\r?\n/).map(line => JSON.parse(line));
  const controls = JSON.parse(raw['controls.json'].toString('utf8'));
  if (controls.runId !== e.runId) fail('Controls run mismatch');
  e.controls = controls.controls;
  e.visibility.systemState = raw['system-state.txt'].toString('utf8');
  return e;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const dir = process.argv[2]; if (!dir) fail('Usage: node check-evidence.mjs EVIDENCE_DIRECTORY');
    process.stdout.write(JSON.stringify(verifyEvidence(loadEvidence(dir))) + '\n');
  } catch (error) { process.stderr.write(error.message + '\n'); process.exitCode = 1; }
}
