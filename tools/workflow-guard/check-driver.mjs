// Trusted caller-check entry point. No service, model, scheduler or paid API.
// Merely importing this module does not run Gradle, adb or the P0 runner.
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { loadEvidence, verifyEvidence } from './check-evidence.mjs';

const sha = data => createHash('sha256').update(data).digest('hex');
const fail = message => { throw new Error(message); };

export function integration(workspace) {
  const session = JSON.parse(readFileSync(join(workspace, 'guard/session.json'), 'utf8'));
  if (session.verified !== true || !session.sessionNonce || !session.runId ||
      !/^(?:127\.0\.0\.1:\d+|emulator-\d+)$/.test(session.serial) ||
      !Number.isInteger(session.androidUser) || !session.fingerprint?.includes('Pleos/')) fail('Invalid dedicated session');
  const dir = join(workspace, 'validation/journey-probe/runs/workflow-guard-p0');
  const privateLogs = join(workspace, '.workflow-guard/private-logs');
  mkdirSync(dir, { recursive: true }); mkdirSync(privateLogs, { recursive: true });
  const deadline = Date.now() + 280000, workDeadline = deadline - 40000, records = [];
  let restoring = false;
  const run = (label, exe, args, timeoutMs, { allowedExit = [0], env = process.env } = {}) => {
    const remaining = (restoring ? deadline : workDeadline) - Date.now();
    if (remaining < 1) fail('Caller check time budget exhausted; cleanup reserve preserved');
    const started = new Date().toISOString();
    const out = spawnSync(exe, args, { cwd: workspace, env, encoding: 'utf8', shell: false, windowsHide: true,
      timeout: Math.min(timeoutMs, remaining), maxBuffer: 4 * 1024 * 1024 });
    const log = String(out.stdout ?? '') + '\n' + String(out.stderr ?? '') + (out.error ? '\n' + out.error.message : '');
    writeFileSync(join(privateLogs, label + '.log'), log);
    const exitCode = Number.isInteger(out.status) ? out.status : -1;
    records.push({ label, exe, args, started, exitCode, logSha256: sha(log) });
    if (!allowedExit.includes(exitCode)) fail(label + ' failed; inspect local private log, not an SDK support verdict');
    return String(out.stdout ?? '').trim();
  };
  const adb = (label, args, allowedExit) => run(label, session.adbPath,
    ['-P', String(session.adbServerPort), '-s', session.serial, ...args], 10000, { allowedExit });
  const runner = 'validation/journey-probe/lifecycle.py', pkg = 'ai.pleos.playground.sample.vehicle';
  let liveStarted = false, caught;
  try {
    const fingerprint = adb('target', ['shell', 'getprop', 'ro.build.fingerprint']);
    if (fingerprint !== session.fingerprint) fail('Target changed since preflight');
    const user = adb('android-user', ['shell', 'am', 'get-current-user']);
    if (Number(user) !== session.androidUser) fail('Android user changed since preflight');
    if (adb('existing-process', ['shell', 'pidof', pkg], [0, 1])) fail('Sample already running; inspect ownership before changing it');
    const env = { ...process.env, JAVA_HOME: session.javaHome,
      GRADLE_USER_HOME: join(workspace, '.workflow-guard/gradle-home') };
    const version = run('java-version', join(session.javaHome, 'bin/java.exe'), ['-version'], 10000, { env });
    // java -version normally uses stderr; inspect the private log as well.
    const javaLog = readFileSync(join(privateLogs, 'java-version.log'), 'utf8');
    if (!/version\s+"17(?:\.|\")/.test(version + javaLog)) fail('JDK17 required by this P0 contract');
    run('build', process.env.ComSpec ?? 'C:/Windows/System32/cmd.exe', ['/d', '/s', '/c',
      'validation\\vehicle-sample\\gradlew.bat --no-daemon -p validation\\vehicle-sample :app:assembleDebug'], 160000, { env });
    if (!existsSync(join(workspace, runner))) fail('New lifecycle runner is missing');
    adb('install', ['install', '--user', String(session.androidUser), '-r',
      join(workspace, 'validation/vehicle-sample/app/build/outputs/apk/debug/app-debug.apk')]);
    liveStarted = true;
    run('lifecycle', session.python ?? 'python', [runner,
      '--adb-path', session.adbPath, '--adb-server-port', String(session.adbServerPort),
      '--serial', session.serial, '--android-user', String(session.androidUser),
      '--run-id', session.runId, '--session-nonce', session.sessionNonce,
      '--output-dir', 'validation/journey-probe/runs/workflow-guard-p0',
      '--phase-timeout-seconds', '20', '--session-timeout-seconds', '80'], 90000);
  } catch (error) { caught = error; }
  finally {
    restoring = true;
    if (liveStarted) {
      try {
        const state = join(dir, 'runtime-state.json');
        if (!existsSync(state)) fail('Runner did not preserve pre-mutation state; cleanup must be inspected');
        run('restore', session.python ?? 'python', [runner, '--cleanup-only', '--state-file', state], 20000);
      } catch (error) { caught ??= error; }
      try { adb('stop-sample', ['shell', 'am', 'force-stop', '--user', String(session.androidUser), pkg]); }
      catch (error) { caught ??= error; }
    }
    writeFileSync(join(dir, 'execution.json'), JSON.stringify({ runId: session.runId,
      sessionNonce: session.sessionNonce, records, successful: !caught }, null, 2) + '\n');
  }
  if (caught) throw caught;
  const e = loadEvidence(dir, { nonce: session.sessionNonce });
  if (e.runId !== session.runId || e.target.serial !== session.serial || e.target.androidUser !== session.androidUser ||
      e.target.fingerprint !== session.fingerprint) fail('Evidence belongs to another run/target');
  const report = verifyEvidence(e);
  writeFileSync(join(dir, 'result.md'), '# P0 experiment result\n\n' +
    `- Run: ${report.runId}\n- Outcome in tested conditions: ${report.outcome}\n` +
    `- Location: ${report.perSource.location}\n- Odometer: ${report.perSource.odometer}\n` +
    '- Guard completion is not product completion. Review SDK journal, actual UI state, and source code.\n' +
    '- Long-duration, power recovery, and P1 onward remain outside this run.\n');
  return report;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv[2] !== 'integration') fail('Only the pinned integration check is supported');
    process.stdout.write(JSON.stringify(integration(process.cwd())) + '\n');
  } catch (error) { process.stderr.write(error.message + '\n'); process.exitCode = 1; }
}
