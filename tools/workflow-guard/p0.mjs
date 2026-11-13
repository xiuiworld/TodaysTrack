// Optional P0 adapter. Project-wide policy and preparation live in project.mjs.
import { readFileSync, existsSync } from 'node:fs';
import { isAbsolute, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export function p0Unit({ withinSubmission = false } = {}) {
  const recipe = JSON.parse(readFileSync(new URL('./p0.profile.json', import.meta.url), 'utf8'));
  if (recipe.recipeVersion !== 1 || recipe.role !== 'stage-recipe' || recipe.stage !== 'P0') throw new Error('Invalid P0 recipe');
  return {
    completion: withinSubmission ? 'partial' : 'full',
    instruction: (withinSubmission ? 'S1의 작은 probe 부분 작업이다. 제품 수집/저장 등 S1 전체 완료가 아니다.\n' : '') + recipe.constraints.join('\n') + '\n' +
      'context/p0-run-contract.md에 따라 새 LifecycleProbe Activity/Service/Journal과 lifecycle.py를 작성한다. ' +
      '기존 JourneyProbeActivity와 journey.py는 보존한다. 실제 SDK 수집은 서비스가 소유한다. ' +
      '유한 실험 뒤 근거 있는 지원/미지원 판정과 한계를 보고하고 이 작업 단위를 끝낸다.',
    paths: recipe.writePaths,
    artifacts: recipe.artifacts,
    requirements: recipe.requirements.map(r => ({ ...r, id: r.id.replace(/^P0-/, '') })),
    checks: [{ id: 'INTEGRATION', argv: [process.execPath, 'guard/check-driver.mjs', 'integration'],
      effect: 'device', execution: 'caller', timeoutMs: 300000 }],
    runtimeContextPaths: ['guard/check-driver.mjs', 'guard/check-evidence.mjs', 'guard/session.json', 'context/p0-run-contract.md']
  };
}

export function validateP0Session(s) {
  if (!s || s.verified !== true || !isAbsolute(s.adbPath ?? '') || !isAbsolute(s.javaHome ?? '') || !isAbsolute(s.sdkRoot ?? '') ||
      !/^(?:127\.0\.0\.1:\d+|emulator-\d+)$/.test(s.serial ?? '') || !Number.isInteger(s.androidUser) || s.androidUser < 0 ||
      !Number.isInteger(s.adbServerPort) || s.adbServerPort < 1024 || s.adbServerPort > 65535) throw new Error('Verified dedicated AVD session required');
  for (const value of [s.adbPath, s.javaHome, s.sdkRoot]) {
    if (/[\r\n\0]/.test(value) || !existsSync(value)) throw new Error('Invalid installed tool path');
  }
  if (!s.fingerprint?.includes('Pleos/')) throw new Error('Confirmed Pleos fingerprint required');
  return s;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.stderr.write('P0 adapter only. Use node tools/workflow-guard/project.mjs check or project.mjs prepare with actual stage approval. Nothing started.\n');
  process.exitCode = 1;
}
