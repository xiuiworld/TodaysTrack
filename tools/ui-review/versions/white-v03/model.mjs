// Review-only state. No product modules, real records, audio or API clients are imported.
export const BASELINE = Object.freeze({ layout: 'split', font: 20, gap: 20, cover: 200, button: 52, tone: 'porcelain' });
export const SCENARIOS = ['empty', 'capturing', 'pending', 'uncertain', 'saved', 'error'];
export function normalizeConfig(input = {}) {
  const bounded = (key, min, max) => Number.isFinite(Number(input[key])) ? Math.max(min, Math.min(max, Number(input[key]))) : BASELINE[key];
  return { layout: input.layout === 'focus' ? 'focus' : 'split', font: bounded('font', 16, 24), gap: bounded('gap', 12, 32), cover: bounded('cover', 120, 220), button: bounded('button', 40, 64), tone: ['porcelain', 'warm', 'ink'].includes(input.tone) ? input.tone : 'porcelain' };
}
export function newRecord(id, overrides = {}) {
  return { id, title: '여행 기록', date: '예시 11월 12일 · 16:08:00', genre: '어쿠스틱', lyrics: '가사 있는 곡', kind: '여행 기록', distance: '6.00', open: false, state: null, intentId: null, jobId: null, input: null, ...overrides };
}
export function createState(scenario = 'saved', provenance = 'FIXTURE') {
  const record = newRecord('REVIEW-record-001');
  const previous = newRecord('REVIEW-record-002', { date: '예시 11월 11일 · 11:50:00', kind: '하루 기록', title: '하루 기록', distance: '8.00', genre: '잔잔한 팝', lyrics: '무가사', state: 'AVAILABLE', intentId: 'REVIEW-intent-002', jobId: 'REVIEW-job-002', input: 'REVIEW-fixed-input-002' });
  const state = { scenario, page: 'home', ux: 'parked', provenance, records: scenario === 'empty' ? [] : [record, previous], selectedId: scenario === 'empty' ? null : record.id, capturing: scenario === 'capturing', playingId: null, screenClosed: false, defaults: { kind: '하루 기록', genre: '잔잔한 팝', lyrics: '무가사' }, nextId: 3 };
  if (scenario === 'capturing') { record.open = true; record.distance = '2.00'; }
  if (['pending', 'uncertain', 'saved', 'error'].includes(scenario)) {
    record.state = { pending: 'LOCAL_PENDING', uncertain: 'ACCEPTANCE_UNCERTAIN', saved: 'AVAILABLE', error: 'RECONCILING' }[scenario];
    record.intentId = 'REVIEW-intent-001'; record.jobId = scenario === 'uncertain' ? null : 'REVIEW-job-001'; record.input = 'REVIEW-fixed-input-001';
  }
  return state;
}
export const selectedRecord = state => state.records.find(r => r.id === state.selectedId);
export function controls(state) {
  const allowed = state.ux === 'parked';
  const record = selectedRecord(state);
  const hasOpen = state.records.some(r => r.open);
  return { allowed, canStart: allowed && !state.capturing, canStop: state.capturing, canSelect: allowed && !state.capturing, canChangeSettings: allowed && !state.capturing && !hasOpen, canGenerate: allowed && !state.capturing && !!record && !record.open && record.state !== 'AVAILABLE', canPlay: allowed };
}
export function statusText(state, record) {
  if (!record) return '새 관측을 모아 음악으로 남겨보세요';
  if (record.open) return state.capturing ? '기록 수집 중 · 현재 설정으로 이어갑니다' : '이전 설정으로 기록을 이어갈 수 있습니다';
  return { AVAILABLE: '곡이 저장되었습니다 · 아래에서 감상하세요', LOCAL_PENDING: '생성 서버 연결 대기 · 기록과 입력은 보존됩니다', ACCEPTANCE_UNCERTAIN: '접수 여부 확인 중 · 원래 작업만 조회하며 새로 생성하지 않습니다', RECONCILING: state.scenario === 'error' ? '작업 확인 중 오류 · 기록과 기존 작업은 보존됩니다' : '접수·결과 확인 중 · 자동으로 다시 생성하지 않습니다' }[record.state] || '기록이 모였습니다 · 음악으로 남겨보세요';
}
export function tracks(state) { return state.records.filter(r => r.state === 'AVAILABLE').map(r => ({ id: `REVIEW-track-${r.id}`, recordId: r.id, title: `${r.title}의 음악 · 검토 예시`, duration: '1:30', provenance: state.provenance })); }
// Mutates only this in-browser mock. A result message makes every simulated action explicit.
export function act(state, action, value) {
  const c = controls(state); const r = selectedRecord(state);
  if (action === 'page' && ['home', 'records', 'library'].includes(value)) { state.page = value; return '화면을 이동했습니다.'; }
  if (action === 'select' && c.canSelect && state.records.some(item => item.id === value)) { state.selectedId = value; return '검토용 기록을 선택했습니다. 기존 설정·고정 입력은 그대로입니다.'; }
  if (action === 'start' && c.canStart) {
    let open = state.records.find(item => item.open);
    if (!open) { open = newRecord(`REVIEW-record-${state.nextId++}`, { ...state.defaults, title: state.defaults.kind, date: '예시 11월 13일 · 09:00:00', distance: '0.00', open: true }); state.records.unshift(open); }
    state.selectedId = open.id; state.capturing = true; state.scenario = 'capturing'; state.page = 'home';
    return '수집 시작 모의 · 센서나 SDK를 실행하지 않습니다. 이전 OPEN 기록은 원래 설정을 사용합니다.';
  }
  if (action === 'finish' && c.canStop) { const open = state.records.find(item => item.open); open.open = false; state.selectedId = open.id; state.capturing = false; state.scenario = 'sealed'; open.distance = '6.00'; return '마감 모의 · 거리 6.00 km는 예시입니다. 이제 음악 만들기를 눌러볼 수 있습니다.'; }
  if (action === 'generate' && c.canGenerate) {
    if (r.intentId) return '기존 작업 조회 모의 · 원래 작업 ID와 고정 입력 유지. 신규 생성·재송신 없음. 결과는 왼쪽 진행 모의로 선택하세요.';
    r.intentId = `REVIEW-intent-${r.id}`; r.jobId = `REVIEW-job-${r.id}`; r.input = `REVIEW-fixed-input-${r.id}`; r.state = 'LOCAL_PENDING'; state.scenario = 'pending';
    return '생성 대기 화면 모의 · 실제 요약 전송·API 호출·과금은 없습니다.';
  }
  if (action === 'resolve' && r?.intentId && !state.capturing) { r.state = 'AVAILABLE'; r.jobId ||= `REVIEW-recovered-job-${r.id}`; state.scenario = 'saved'; return '기존 작업의 저장 완료 결과 모의 · 동일한 intent와 고정 입력 유지. 실제 다운로드 없음.'; }
  if (action === 'play' && c.canPlay && tracks(state).some(t => t.id === value)) { state.playingId = value; return '재생 표시 모의 · 실제 음원·소리·오디오 포커스는 연결되지 않았습니다.'; }
  if (action === 'pause') { state.playingId = null; return '일시정지 모의 · 실제 플레이어는 실행하지 않습니다.'; }
  if (action === 'close' && state.capturing) { state.screenClosed = true; return '화면 닫기 모의 · 수집 상태와 기록은 유지합니다. 백그라운드 수집 검증이 아닙니다.'; }
  if (action === 'reopen') { state.screenClosed = false; return '같은 검토 기록으로 화면을 다시 열었습니다.'; }
  return '현재 조작 조건에서 사용할 수 없습니다.';
}
