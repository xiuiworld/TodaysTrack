import test from 'node:test';
import assert from 'node:assert/strict';
import { BASELINE, SCENARIOS, createState, controls, act, selectedRecord, tracks, normalizeConfig, positionFor, seekPlayback, advancePlayback, durationSeconds } from './model.mjs';

test('all requested fixtures are selectable and have review-only IDs', () => {
  for (const scenario of SCENARIOS) {
    const s = createState(scenario);
    assert.equal(s.scenario, scenario);
    assert.ok(s.records.every(r => r.id.startsWith('REVIEW-')));
    if (scenario === 'empty') assert.equal(s.records.length, 0);
    if (scenario === 'capturing') assert.equal(s.capturing, true);
    if (scenario === 'saved') assert.equal(tracks(s).length, 2);
  }
});
test('new settings leave existing records and frozen work untouched', () => {
  const s = createState('saved'), existing = structuredClone(s.records);
  s.defaults = { kind:'하루 기록', genre:'일렉트로닉', lyrics:'무가사' };
  act(s, 'start');
  assert.deepEqual(s.records.slice(1), existing);
  assert.equal(selectedRecord(s).genre, '일렉트로닉');
  act(s, 'finish'); act(s, 'generate');
  const frozen = { ...selectedRecord(s) };
  act(s, 'generate'); act(s, 'generate');
  assert.deepEqual(selectedRecord(s), frozen);
  act(s, 'resolve');
  assert.equal(selectedRecord(s).intentId, frozen.intentId);
  assert.equal(selectedRecord(s).input, frozen.input);
  assert.equal(selectedRecord(s).jobId, frozen.jobId);
  assert.deepEqual(s.records.slice(1), existing);
});
test('uncertain acceptance and errors only check the same frozen intent', () => {
  for (const scenario of ['uncertain','pending','error']) {
    const s = createState(scenario); const before = structuredClone(selectedRecord(s));
    assert.match(act(s,'generate'), /기존 작업 조회/);
    assert.deepEqual(selectedRecord(s), before);
    act(s,'resolve');
    assert.equal(selectedRecord(s).intentId, before.intentId);
    assert.equal(selectedRecord(s).input, before.input);
    if(before.jobId) assert.equal(selectedRecord(s).jobId, before.jobId);
  }
});
test('CarUx unknown/restricted blocks actions but preserves stop and pause', () => {
  for(const ux of ['unknown','restricted']) {
    for(const scenario of SCENARIOS) {
      const s = createState(scenario);s.ux=ux;
      const before = structuredClone(s);
      assert.equal(controls(s).canChangeSettings,false);
      for(const [action,value] of [['start'],['generate'],['select','REVIEW-record-002'],['play',tracks(s)[0]?.id]]) act(s,action,value);
      assert.deepEqual(s,before);
    }
    const s=createState('capturing');s.ux=ux;act(s,'finish');assert.equal(s.capturing,false);
    s.playingId='REVIEW-track';act(s,'pause');assert.equal(s.playingId,null);
  }
});
test('closing the mock screen preserves an OPEN record and its settings', () => {
  const s=createState('capturing');const record=structuredClone(selectedRecord(s));
  act(s,'close');assert.equal(s.capturing,true);assert.equal(s.screenClosed,true);
  act(s,'reopen');assert.deepEqual(selectedRecord(s),record);
});
test('presentation changes are isolated from session records and IDs', () => {
  const s=createState('uncertain'),before=structuredClone(s);
  const config=normalizeConfig({font:2,button:500,cover:180,layout:'focus'});
  assert.equal(config.font,16);assert.equal(config.button,64);assert.equal(config.layout,'focus');
  assert.deepEqual(s,before);assert.equal(BASELINE.font,20);
});
test('old design history stays on the original renderer; Astryx is explicit', () => {
  assert.equal(normalizeConfig({font:18}).renderer,'original');
  assert.equal(normalizeConfig({renderer:'astryx-home-v1'}).renderer,'astryx-home-v1');
  assert.equal(normalizeConfig({renderer:'astryx-home-v2'}).renderer,'astryx-home-v2');
  assert.equal(normalizeConfig({renderer:'astryx-app-v3'}).renderer,'astryx-app-v3');
  assert.equal(normalizeConfig({renderer:'astryx-app-v4'}).renderer,'astryx-app-v4');
  assert.equal(normalizeConfig({renderer:'astryx-app-v5'}).renderer,'astryx-app-v5');
  assert.equal(normalizeConfig({renderer:'astryx-app-v6'}).renderer,'astryx-app-v6');
  assert.equal(normalizeConfig({renderer:'astryx-app-v7'}).renderer,'astryx-app-v7');
  assert.equal(normalizeConfig({renderer:'astryx-app-v8'}).renderer,'astryx-app-v8');
  assert.equal(normalizeConfig({renderer:'astryx-app-v9'}).renderer,'astryx-app-v9');
  assert.equal(normalizeConfig({renderer:'unknown'}).renderer,'original');
  const s=createState('uncertain'),before=structuredClone(s);
  normalizeConfig({...BASELINE,renderer:'astryx-home-v1'});
  assert.deepEqual(s,before);
});


test('mock playback pauses, seeks and resumes the same position without changing records',()=>{
  const s=createState('saved'),recordSnapshot=structuredClone(s.records),a=tracks(s)[0];
  assert.equal(durationSeconds(a),90);act(s,'play',a.id);advancePlayback(s,12.5);act(s,'pause');
  assert.equal(positionFor(s,a.id),12.5);assert.equal(advancePlayback(s,10),false);
  seekPlayback(s,a.id,45);assert.equal(s.playingId,null);act(s,'play',a.id);advancePlayback(s,5);
  assert.equal(positionFor(s,a.id),50);assert.deepEqual(s.records,recordSnapshot);
});
test('mock playback ends at duration, restarts, and resets when another track is chosen',()=>{
  const s=createState('saved'),[a,b]=tracks(s);act(s,'play',a.id);advancePlayback(s,999);
  assert.equal(s.playingId,null);assert.equal(positionFor(s,a.id),90);act(s,'play',a.id);
  assert.equal(positionFor(s,a.id),0);seekPlayback(s,a.id,60);act(s,'play',b.id);
  assert.equal(positionFor(s,b.id),0);assert.equal(positionFor(s,a.id),0);
});
test('seek clamps time, rejects invalid input, and obeys CarUx without blocking pause',()=>{
  const s=createState('saved'),a=tracks(s)[0];act(s,'play',a.id);seekPlayback(s,a.id,25);
  for(const ux of ['restricted','unknown']){s.ux=ux;const before=structuredClone(s);assert.equal(seekPlayback(s,a.id,70),false);assert.deepEqual(s,before);}
  advancePlayback(s,2);act(s,'pause');assert.equal(positionFor(s,a.id),27);
  s.ux='parked';assert.equal(seekPlayback(s,a.id,Infinity),false);assert.equal(seekPlayback(s,'missing',3),false);
  seekPlayback(s,a.id,-5);assert.equal(positionFor(s,a.id),0);seekPlayback(s,a.id,999);assert.equal(positionFor(s,a.id),90);
});
