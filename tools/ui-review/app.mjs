import { BASELINE, normalizeConfig, createState, selectedRecord, controls, statusText, tracks, act, seekPlayback, advancePlayback } from './model.mjs';

const $ = id => document.getElementById(id);
const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
const configKeys = Object.keys(BASELINE);
const storageKey = 'todaystrack-ui-review:design-history:v1';
let state = createState();
let view={detailId:null,trackId:null};
if(['home','records','library'].includes(location.hash.slice(1)))state.page=location.hash.slice(1);
let candidate = null;
let draft = { ...BASELINE };
let history = [{ id: 'baseline', label: '0.3 기준안', sourceRevision: 'baseline', config: { ...BASELINE } }];
let current = history[0];
const frozenApp={id:'app-004-frozen',label:'직전 전체 화면 · app-004',config:{layout:'split',font:20,gap:16,cover:120,button:48,tone:'porcelain',renderer:'astryx-app-v3'}};
const frozenJourney={id:'app-006-frozen',label:'수정 전 · 기록·상세·보관함',config:{...frozenApp.config,renderer:'astryx-app-v4'}};
const frozenConnection={id:'app-010-frozen',label:'수정 전 · 기록과 음악 연결',config:{...frozenApp.config,renderer:'astryx-app-v5'}};
const frozenPanels={id:'app-012-frozen',label:'수정 전 · 음악 상세/팝업',config:{...frozenApp.config,renderer:'astryx-app-v6'}};
const frozenPlayer={id:'app-013-frozen',label:'수정 전 · 재생 버튼/바',config:{...frozenApp.config,renderer:'astryx-app-v7'}};
const frozenFlow={id:'app-017-frozen',label:'수정 전 · 홈 전환/버튼 반응',config:{...frozenApp.config,renderer:'astryx-app-v9'}};
const frozenAlignment={id:'app-014-frozen',label:'수정 전 · 버튼 크기/목록',config:{...frozenApp.config,renderer:'astryx-app-v8'}};
try {
  const saved = JSON.parse(localStorage.getItem(storageKey));
  if (Array.isArray(saved) && saved.length) {
    history = [history[0], ...saved.filter(item => item.id !== 'baseline' && typeof item.id === 'string' && typeof item.label === 'string').map(item => ({ id:item.id, label:item.label, sourceRevision:typeof item.sourceRevision==='string'?item.sourceRevision:'legacy', config:normalizeConfig(item.config) })).slice(-20)];
    current = history.at(-1);
  }
} catch { /* Browser storage is optional; never read any product storage. */ }
draft = { ...current.config };
const modal = $('modal');
const icons = {
  home:'<path d="M3 10 12 3 21 10v11h-6v-7H9v7H3z"/>',
  records:'<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 8h8M8 12h8M8 16h8"/>',
  library:'<path d="M8 18V5l12-2v13"/><ellipse cx="5.5" cy="18.5" rx="2.5" ry="2.5"/><ellipse cx="17.5" cy="16.5" rx="2.5" ry="2.5"/>'
};
function hit(text, action, { disabled = false, primary = false, small = false, value = '', icon = false } = {}) {
  return `<button class="hit ${primary?'primary':''} ${small?'small':''} ${icon?'icon':''}" data-action="${action}" data-value="${escape(value)}" ${disabled?'disabled':''}><span class="hit-inner">${escape(text)}</span></button>`;
}
function art(id) {
  // Same Java String.hashCode bucket as SubmissionScreen.image().
  let hash = 0; for (let i=0;i<id.length;i++) hash=(Math.imul(31,hash)+id.charCodeAt(i))|0;
  return `/assets/cover_${['concrete','curve','planes'][((hash%3)+3)%3]}.png`;
}
function recordRow(r, c) {
  return `<button class="record-row ${r.id===state.selectedId?'selected':''}" data-action="select" data-value="${escape(r.id)}" ${c.canSelect?'':'disabled'} aria-pressed="${r.id===state.selectedId}">${escape(r.title)}<small>${escape(r.date)}${r.open?' · 진행 중':''}</small></button>`;
}
function renderApp(config, width, height, readonly = false) {
  const c = controls(state); const r = selectedRecord(state); const library = state.page === 'library';
  const narrow = width < 960 || config.layout === 'focus';
  const items = state.page === 'home' ? [...state.records.filter(r=>r.open), ...state.records.filter(r=>!r.open).slice(0,5)] : state.records;
  const allTracks = tracks(state); const visibleTracks = library ? allTracks : allTracks.filter(t=>t.recordId===r?.id);
  const settings = r || state.defaults;
  const nav = Object.entries({home:'홈',records:'기록',library:'보관함'}).map(([page,label]) => `<button class="nav-hit ${state.page===page?'active':''}" data-action="page" data-value="${page}" aria-label="${label}" aria-pressed="${state.page===page}"><span class="nav-inner"><svg viewBox="0 0 24 24" aria-hidden="true">${icons[page]}</svg>${label}</span></button>`).join('');
  const available = r?.state === 'AVAILABLE';
  const generateText = available ? '음악 듣기' : r?.state ? '기존 작업 확인' : '음악 만들기';
  const footer = `<div class="playback"><span>${state.playingId?'재생 중 · 0:12 / 1:30 (무음 모의)':'저장된 곡을 선택해 주세요'}</span>${hit('일시정지','pause',{small:true,disabled:!state.playingId})}</div><div class="privacy">정밀 위치는 차량에 보관됩니다 · 표지는 고정 기본 이미지입니다</div>`;
  const css = `--width:${width}px;--height:${height}px;--font:${config.font}px;--gap:${config.gap}px;--cover:${config.cover}px;--button:${config.button}px`;
  if (state.screenClosed) return `<div class="vehicle tone-${config.tone}" style="${css}"><div class="closed"><h3>화면을 닫았습니다 · 검토 모의</h3><p>수집 상태를 유지하고 있습니다. 실제 백그라운드 수집은 실행하지 않습니다.</p>${hit('화면 다시 열기','reopen',{primary:true})}</div></div>`;
  return `<div class="vehicle tone-${config.tone}" style="${css}" ${readonly?'data-comparison="true"':''}>
    <nav class="rail" aria-label="앱 탐색"><div class="app-name">오늘의<br>트랙</div>${nav}<button class="nav-hit info-hit" data-action="info"><span class="nav-inner">앱 정보</span></button></nav>
    ${!narrow&&!library?`<aside class="records-pane"><h3 class="records-heading">${state.page==='records'?'모든 기록':'최근 기록'}</h3><div class="record-list">${items.map(r=>recordRow(r,c)).join('')}</div></aside>`:''}
    <div class="panel-shell"><div class="panel">
      <div class="parked ${c.allowed?'':'restricted'}">${c.allowed?'정차 중':state.ux==='unknown'?'조작 제한 · 상태 확인 필요':'조작 제한 · 상태 확인 필요'}</div>
      ${narrow&&!library?`<div class="choose-row">${hit('저장된 기록 선택','choose',{disabled:!c.canSelect})}</div>`:''}
      ${!library?`<div class="hero"><img class="cover" src="${art(r?.id||'default')}" alt=""><div class="hero-copy"><h3 class="headline">${escape(r?.title||'기록을 모아보세요')}</h3><div class="record-date">${escape(r?.date||'사진·메모 없이 시작할 수 있어요')}</div><div class="distance">${r?`연속 관측 거리 ${escape(r.distance)} km`:'기록을 선택해 주세요'}</div><div class="settings-label">${escape(r?`${settings.genre} · ${settings.lyrics}`:`${settings.kind} · ${settings.genre} · ${settings.lyrics}`)}</div></div></div>
      <p class="state-copy">${r?.state&&state.provenance==='FIXTURE'?'대역 · 새 AI 생성 아님\n':''}${escape(statusText(state,r))}</p>
      <p class="notice">음악 만들기를 누르면 정확한 위치를 뺀 요약을 생성 서버·음악 AI로 전송합니다.</p>
      ${r&&!r.open?`<div class="action-row">${hit(generateText,available?'listen':'generate',{primary:true,disabled:available?!c.canPlay:!c.canGenerate})}${hit('기록 상세','details',{small:true})}</div>`:r?`<div class="action-row">${hit('기록 상세','details',{small:true})}</div>`:''}
      ${!state.capturing?`<div class="action-row">${hit(state.records.some(r=>r.open)?'이전 기록 이어서 수집':'새 기록 시작','start',{disabled:!c.canStart})}${hit('새 기록 설정','settings',{small:true,disabled:!c.canChangeSettings})}</div>`:''}
      `:''}
      ${state.capturing?`<div class="capture">수집 중 · SDK 이벤트·거리는 검토 예시</div><div class="action-row">${hit('기록 마무리','finish',{primary:true,disabled:!c.canStop})}${hit('화면 닫기','close',{small:true})}</div>`:''}
      <section class="songs"><h3>${library?'나의 보관함':'이 기록의 음악'}</h3>${visibleTracks.length?visibleTracks.map(t=>`<div class="song-row"><img src="${art(t.recordId)}" alt=""><div class="song-copy"><strong>${escape(t.title)}</strong><small>${t.duration} · ${t.provenance==='FIXTURE'?'대역 · 새 생성 아님':'새 AI 음악 · 검토 예시'}<br>${escape(state.records.find(r=>r.id===t.recordId)?.date)}</small></div>${hit('재생','play',{icon:true,disabled:!c.canPlay,value:t.id})}</div>`).join(''):'<div class="empty-songs">음악을 만들면 여기에 저장됩니다</div>'}</section>${footer}
    </div></div>
  </div>`;
}
let observer;
const homeChannel='todaystrack-home-v1';
const isAstryx = config => ['astryx-home-v1','astryx-home-v2','astryx-app-v3','astryx-app-v4','astryx-app-v5','astryx-app-v6','astryx-app-v7','astryx-app-v8','astryx-app-v9','astryx-app-v10'].includes(config.renderer);
const usesHome = config => ['astryx-app-v3','astryx-app-v4','astryx-app-v5','astryx-app-v6','astryx-app-v7','astryx-app-v8','astryx-app-v9','astryx-app-v10'].includes(config.renderer)||isAstryx(config)&&state.page==='home';
function viewportSize(){const width=Number($('viewport').value);return {width,height:width===1682?1210:605,density:width===1682?160:320};}
function updateFrame(frame) {
  frame.contentWindow?.postMessage({channel:homeChannel,type:'render',state,view,showDiagnostics:$('diagnostics').checked,isComparison:frame.title.startsWith('비교안'),config:JSON.parse(frame.dataset.config),hitboxes:$('hitboxes').checked},location.origin);
}
let playbackClock=null,playbackAt=0;
function syncPlaybackClock(){
  if(state.playingId&&!document.hidden){
    if(!playbackClock){playbackAt=performance.now();playbackClock=setInterval(()=>{
      const now=performance.now();advancePlayback(state,(now-playbackAt)/1000);playbackAt=now;
      if(!state.playingId){log('재생 완료 · 브라우저 모의');render();}
      else document.querySelectorAll('.home-frame').forEach(updateFrame);
    },250);}
  }else if(playbackClock){clearInterval(playbackClock);playbackClock=null;}
}
document.addEventListener('visibilitychange',syncPlaybackClock);
window.addEventListener('pagehide',()=>{if(playbackClock)clearInterval(playbackClock);playbackClock=null;});
function scaleWindows() {
  const {width,height,density}=viewportSize();
  const scales=[];
  const previews=$('previews');
  const sideBySide=getComputedStyle(previews).gridTemplateColumns.split(' ').length>1;
  const availableWidth=sideBySide?(previews.clientWidth-18)/2:previews.clientWidth;
  const availableHeight=Math.max(300,innerHeight-previews.getBoundingClientRect().top-42);
  document.querySelectorAll('.scaled-window').forEach(shell => {
    const scale=Math.min(1,(availableWidth-2)/width,availableHeight/height); scales.push(Math.round(scale*100));
    shell.closest('.preview-card').style.width=`${width*scale+2}px`;
    shell.closest('.preview-card').style.justifySelf='center';
    shell.style.height=`${height*scale}px`; shell.firstElementChild.style.transform=`scale(${scale})`;
  });
  $('scale-label').textContent=`${width} × ${height}dp 참고 · 보존된 ${density}dpi 창 기준 · 맞춤 ${scales[0]||100}% · 기기 현재 측정 아님`;
}
function render() {
  $('source-note').textContent=state.provenance==='FIXTURE'?'출처 모의: 대역 · 새 AI 생성 아님':'출처 모의: AI 표시 · 실제 생성 없음';
  const {width,height}=viewportSize();
  const selected=$('compare').value;
  const compared=selected==='baseline'?history[0]:selected===frozenApp.id?frozenApp:selected===frozenJourney.id?frozenJourney:selected===frozenConnection.id?frozenConnection:selected===frozenPanels.id?frozenPanels:selected===frozenPlayer.id?frozenPlayer:selected===frozenFlow.id?frozenFlow:selected===frozenAlignment.id?frozenAlignment:history.find(item=>item.id===selected);
  $('compare-toggle').textContent=compared?'비교 닫기':'기존 안 비교';
  $('compare-toggle').setAttribute('aria-pressed',String(!!compared));
  $('previews').classList.toggle('comparing',!!compared);
  const revisions=[current,...(compared?[compared]:[])];
  const existing=[...$('previews').children];
  revisions.forEach((revision,index)=>{
    let card=existing.find(el=>el.dataset.revision===revision.id);
    if(!card){card=document.createElement('article');card.className='preview-card';card.dataset.revision=revision.id;card.innerHTML='<div class="preview-label"><strong></strong><small></small></div><div class="scaled-window"><div class="screen-scaler"></div></div>';}
    card.querySelector('strong').textContent=`${index?'비교안':'현재안'} · ${revision.label}`;
    card.querySelector('small').textContent=isAstryx(revision.config)&&!['astryx-app-v3','astryx-app-v4','astryx-app-v5','astryx-app-v6','astryx-app-v7','astryx-app-v8','astryx-app-v9','astryx-app-v10'].includes(revision.config.renderer)&&state.page!=='home'?'홈 외 화면은 기준안':index?(view.trackId&&!['astryx-app-v6','astryx-app-v7','astryx-app-v8','astryx-app-v9','astryx-app-v10'].includes(revision.config.renderer)?'이전 안은 음악 상세 없이 목록 표시':'같은 상태로 비교'):'조작 가능';
    const scaler=card.querySelector('.screen-scaler');
    if(usesHome(revision.config)){
      let frame=scaler.querySelector('.home-frame');
      if(!frame){frame=document.createElement('iframe');frame.className='home-frame';frame.title=`${index?'비교안':'수정안'} · 오늘의 트랙`;frame.src=revision.config.renderer==='astryx-home-v1'?'/home-review/v1/index.html':revision.config.renderer==='astryx-home-v2'?'/home-review/v2/index.html':revision.config.renderer==='astryx-app-v3'?'/home-review/v3/index.html':revision.config.renderer==='astryx-app-v4'?'/home-review/v4/index.html':revision.config.renderer==='astryx-app-v5'?'/home-review/v5/index.html':revision.config.renderer==='astryx-app-v6'?'/home-review/v6/index.html':revision.config.renderer==='astryx-app-v7'?'/home-review/v7/index.html':revision.config.renderer==='astryx-app-v8'?'/home-review/v8/index.html':revision.config.renderer==='astryx-app-v9'?'/home-review/v9/index.html':'/home-review/index.html';scaler.replaceChildren(frame);frame.addEventListener('load',()=>updateFrame(frame));}
      frame.title=`${index?'비교안':'수정안'} · 오늘의 트랙`;
      frame.width=width;frame.height=height;frame.dataset.config=JSON.stringify({...revision.config,width,height});
      updateFrame(frame);
    }else{
      const panelY=scaler.querySelector('.panel')?.scrollTop||0;
      const focused=scaler.contains(document.activeElement)?{action:document.activeElement.dataset.action,value:document.activeElement.dataset.value}:null;
      scaler.innerHTML=renderApp(revision.config,width,height,!!index);
      const panel=scaler.querySelector('.panel');if(panel)panel.scrollTop=panelY;
      if(focused?.action)[...scaler.querySelectorAll('[data-action]')].find(b=>b.dataset.action===focused.action&&b.dataset.value===focused.value)?.focus({preventScroll:true});
    }
    if($('previews').children[index]!==card)$('previews').insertBefore(card,$('previews').children[index]||null);
  });
  existing.filter(card=>!revisions.some(r=>r.id===card.dataset.revision)).forEach(card=>card.remove());
  $('revision-title').textContent=current.label;
  $('page').value=state.page;
  if(location.hash.slice(1)!==state.page)window.history.replaceState(null,'','#'+state.page);
  $('revision-summary').textContent=current.config.renderer==='astryx-home-v2'?'홈 구조 재설계 · 새 기록 / 저장 음악의 두 진입점, 한 줄로 줄인 최근 기록, 하단 탐색. Pleos 앱 영역만 재현하며 시스템·차량 패널은 포함하지 않습니다.':current.config.renderer==='astryx-home-v1'?'첫 수정안 · 상세 중심 배치. 사용자 피드백 전 버전을 보존했습니다.':'';
  $('layout').disabled=isAstryx(draft);
  $('resolve').disabled=!selectedRecord(state)?.intentId||state.capturing||selectedRecord(state)?.state==='AVAILABLE';
  $('flow-simulation').hidden=$('resolve').disabled;
  syncPlaybackClock();
  observer?.disconnect(); observer=new ResizeObserver(scaleWindows); document.querySelectorAll('.scaled-window').forEach(el=>observer.observe(el)); scaleWindows();
}
function updateDraft() {
  draft=normalizeConfig(Object.fromEntries(configKeys.map(key=>[key,$(key).value])));
  for(const key of ['font','gap','cover','button']) $(key+'-value').textContent=`${draft[key]} px`;
  const changed=JSON.stringify(draft)!==JSON.stringify(current.config);
  $('layout').disabled=isAstryx(draft);
  $('draft-status').textContent=changed?'미적용 수정안이 있습니다. 적용을 누르면 화면이 바뀝니다.':'현재 적용값입니다. 대화 후 적용을 누르면 준비된 수정안도 확인합니다.';
}
function writeDraft(config) { configKeys.forEach(key=>$(key).value=config[key]); updateDraft(); }
function syncScenarioSelector() { if([...$('scenario').options].some(o=>o.value===state.scenario)) $('scenario').value=state.scenario; else $('scenario').selectedIndex=-1; $('scenario-top').value=$('scenario').value; }
function log(message) { $('event-log').dataset.detail=message; $('event-log').textContent=message.split(' · ')[0].split('。')[0]; }
function compareOptions() {
  const previous=$('compare').value;
  $('compare').innerHTML='<option value="none">현재안만</option><option value="baseline">0.3 기준안과 비교</option><option value="app-017-frozen">수정 전 · 홈 전환/버튼 반응</option><option value="app-014-frozen">수정 전 · 버튼 크기/목록</option><option value="app-013-frozen">수정 전 · 재생 버튼/바</option><option value="app-012-frozen">수정 전 · 음악 상세/팝업</option><option value="app-010-frozen">수정 전 · 기록과 음악 연결</option><option value="app-006-frozen">수정 전 · 기록·상세·보관함</option><option value="app-004-frozen">직전 전체 화면 · app-004</option>'+history.filter(item=>item.id!=='baseline'&&item.id!==current.id).map(item=>`<option value="${escape(item.id)}">${escape(item.label)}</option>`).reverse().join('');
  if([...$('compare').options].some(item=>item.value===previous)) $('compare').value=previous;
}
async function readCandidate() {
  const response=await fetch('./candidate.json',{cache:'no-store'}); if(!response.ok)throw Error('수정안 파일을 읽지 못했습니다.');
  const value=await response.json(); if(typeof value.revision!=='string'||typeof value.label!=='string'||!value.config)throw Error('수정안 형식이 올바르지 않습니다.');
  return {...value,config:normalizeConfig(value.config)};
}
async function apply() {
  $('apply').disabled=true; $('apply-top').disabled=true;
  try {
    const latest=await readCandidate();
    const serverChanged=latest.revision!==current.sourceRevision;
    const localChanged=JSON.stringify(draft)!==JSON.stringify(current.config);
    if(serverChanged&&localChanged) { $('draft-status').textContent='대화에서 준비한 새 수정안과 미적용 조정값이 함께 있습니다. 조정값을 먼저 적용한 뒤 다시 적용하면 새 수정안을 불러옵니다.'; }
    let next=localChanged?draft:serverChanged?latest.config:current.config;
    const changed=JSON.stringify(next)!==JSON.stringify(current.config);
    if(!changed&&!serverChanged) { $('draft-status').textContent='현재 최신 적용안입니다. 크기·간격을 바꾸거나 대화로 수정을 요청해 주세요.'; return; }
    const previous=current;
    current={id:`revision-${Date.now()}`,label:serverChanged&&!localChanged?latest.label:`조정안 ${history.length} · ${isAstryx(next)?'Astryx 화면':next.layout==='focus'?'상세 집중':'분할'}`,sourceRevision:serverChanged&&!localChanged?latest.revision:previous.sourceRevision,config:{...next}};
    history.push(current); if(history.length>21)history.splice(1,1);
    if(!serverChanged||!localChanged)candidate=latest;
    let persisted=true; try {localStorage.setItem(storageKey,JSON.stringify(history));}catch{persisted=false;}
    writeDraft(current.config); compareOptions(); $('compare').value=serverChanged&&previous.config.renderer===next.renderer?'app-017-frozen':previous.id;
    render(); $('draft-status').textContent=`검토 화면에 적용했습니다. 이전 안과 비교 중입니다.${persisted?'':' 브라우저 저장 불가: 이 탭에서만 유지됩니다.'}`;
    log('수정안 적용 · 검토 기록·설정·고정 입력·작업 ID는 유지했습니다. Android 반영은 별도입니다.');
  } catch(error) { $('draft-status').textContent=`적용하지 못했습니다. 현재안 유지 · ${error.message}`; }
  finally { $('apply').disabled=false; $('apply-top').disabled=false; }
}
function showDialog(title,content) {
  $('modal-content').innerHTML=`<h2>${escape(title)}</h2><p class="modal-note">브라우저 검토 모의 · 실제 제품 데이터와 연결되지 않습니다.</p>${content}<div class="modal-actions"><button class="modal-close" data-modal="close">닫기</button></div>`;
  modal.showModal();
}
function detailDialog() {
  const r=selectedRecord(state); if(!r)return;
  showDialog('기록 상세',`<p>${escape(r.date)}<br>${escape(r.kind)} · ${escape(r.genre)} · ${escape(r.lyrics)}</p><p>관측 순서 예시 A → B → [관측 공백] → A<br>연속 관측 거리 ${escape(r.distance)} km · 공백 1개 (모두 예시)</p><p>구간 소요시간·운전 시간을 측정한 값이 아닙니다. 관측 공백 속 경로·감정·방문 장소는 알 수 없습니다.</p><pre>기록: ${escape(r.id)}\n고정 입력: ${escape(r.input||'아직 없음')}\n생성 intent: ${escape(r.intentId||'아직 없음')}\n서버 작업: ${escape(r.jobId||'미확인')}\n상태: ${escape(r.state||r.open&&'OPEN'||'SEALED')}</pre><p class="modal-note">REVIEW 접두부는 검토용 ID입니다. 실제 ID·요약·해시를 복사하지 않았습니다.</p>`);
}
function settingsDialog() {
  if(!controls(state).canChangeSettings)return;
  const field=(key,title,options)=>`<label for="setting-${key}">${title}</label><select id="setting-${key}">${options.map(value=>`<option ${value===state.defaults[key]?'selected':''}>${value}</option>`).join('')}</select>`;
  showDialog('새 기록 설정',`${field('kind','기록 범위',['하루 기록','여행 기록'])}${field('genre','음악 장르',['잔잔한 팝','어쿠스틱','일렉트로닉'])}${field('lyrics','가사',['무가사','가사 있는 곡'])}<p class="modal-note">다음 새 기록에만 적용합니다. 기존 기록의 설정·고정 입력은 바꾸지 않습니다.</p><button class="modal-close primary" data-modal="save-settings">설정 저장 모의</button>`);
}
function handleAction(action,value) {
  if(['play','listen'].includes(action))playbackAt=performance.now();
  if(['page','start','select'].includes(action))view={detailId:null,trackId:null};
  if(action==='page'&&['home','records','library'].includes(value)&&state.page!==value)window.history.pushState(null,'','#'+value);
  if(action==='details'){detailDialog();return;}
  if(action==='settings'){settingsDialog();return;}
  if(action==='choose') { if(!controls(state).canSelect)return; const items=state.page==='home'?[...state.records.filter(r=>r.open),...state.records.filter(r=>!r.open).slice(0,5)]:state.records; showDialog('저장된 기록 선택',items.map(r=>`<button class="modal-record" data-modal="select" data-id="${escape(r.id)}">${escape(r.title)} · ${escape(r.date)}${r.open?' · 진행 중':''}</button>`).join('')||'<p>아직 기록이 없습니다.</p>'); return; }
  if(action==='info') { showDialog('오늘의 트랙 · 0.3 화면 재현','<p>합성 주행 환경·SDK 수집 안내와 전송 안내를 재현했습니다. 이 웹 화면은 SDK를 연결하지 않습니다.</p><p>실제 앱의 생성에는 로컬 음악 서버가 필요합니다. 이 검토실에는 정적 localhost 서버만 사용합니다.</p><p>저장곡 오프라인 감상·기기 등록·오디오 포커스는 웹에서 검증하지 않습니다.</p>');return; }
  if(action==='listen') { const t=tracks(state).find(t=>t.recordId===state.selectedId);log(act(state,'play',t?.id)); }
  else log(act(state,action,value));
  syncScenarioSelector();render();
}
$('previews').addEventListener('click',event=>{
  const button=event.target.closest('button[data-action]');if(!button||button.disabled)return;
  handleAction(button.dataset.action,button.dataset.value);
});
window.addEventListener('message',event=>{
  if(event.origin!==location.origin||event.data?.channel!==homeChannel)return;
  const frame=[...document.querySelectorAll('.home-frame')].find(f=>f.contentWindow===event.source);if(!frame)return;
  if(event.data.type==='ready'){updateFrame(frame);return;}
  if(event.data.type==='view'){
    const id=event.data.detailId;
    if(id===null){view={detailId:null,trackId:null};window.history.replaceState({detailId:null,trackId:null},'','#'+state.page);render();return;}
    if(typeof id!=='string'||!state.records.some(r=>r.id===id)||(!controls(state).canSelect&&state.selectedId!==id))return;
    if(view.detailId!==id||state.page!=='records')window.history.pushState({detailId:id,trackId:null},'','#records');
    if(state.selectedId!==id)act(state,'select',id);
    act(state,'page','records');view={detailId:id,trackId:null};render();return;
  }
  if(event.data.type==='music-view'){
    const id=event.data.trackId;
    if(id===null){view={detailId:null,trackId:null};window.history.replaceState({...view},'','#'+state.page);render();return;}
    const track=typeof id==='string'?tracks(state).find(t=>t.id===id):null;
    if(!track||(!controls(state).canSelect&&track.recordId!==state.selectedId))return;
    if(view.trackId!==id||state.page!=='library')window.history.pushState({detailId:null,trackId:id},'','#library');
    act(state,'page','library');view={detailId:null,trackId:id};render();return;
  }
  if(event.data.type==='seek'){
    if(typeof event.data.trackId==='string'&&typeof event.data.position==='number'&&seekPlayback(state,event.data.trackId,event.data.position)){playbackAt=performance.now();render();}
    return;
  }
  if(event.data.type==='settings-save'){
    const defaults=event.data.defaults;
    if(!controls(state).canChangeSettings||!defaults||!['하루 기록','여행 기록'].includes(defaults.kind)||!['잔잔한 팝','어쿠스틱','일렉트로닉'].includes(defaults.genre)||!['무가사','가사 있는 곡'].includes(defaults.lyrics))return;
    state.defaults={kind:defaults.kind,genre:defaults.genre,lyrics:defaults.lyrics};log('새 기록 설정을 저장했습니다 · 검토 모의');render();return;
  }
  if(event.data.type==='action'&&['page','select','start','finish','close','settings','generate','details','play','pause','info','listen','reopen'].includes(event.data.action))handleAction(event.data.action,typeof event.data.value==='string'?event.data.value:undefined);
});
modal.addEventListener('click',event=>{
  const button=event.target.closest('[data-modal]');if(!button)return;
  if(button.dataset.modal==='save-settings') { if(!controls(state).canChangeSettings)return;state.defaults=Object.fromEntries(['kind','genre','lyrics'].map(key=>[key,$('setting-'+key).value]));log('새 기록의 기본 설정만 저장했습니다 · 검토 모의. 기존 기록·곡·고정 입력은 유지합니다.'); }
  if(button.dataset.modal==='select')log(act(state,'select',button.dataset.id));
  modal.close();render();
});
$('scenario').addEventListener('change',()=>{const ux=state.ux,provenance=state.provenance,page=state.page;state=createState($('scenario').value,provenance);view={detailId:null,trackId:null};state.ux=ux;state.page=page;modal.close();syncScenarioSelector();render();log('검토 상태 변경');});
$('ux').addEventListener('change',()=>{state.ux=$('ux').value;$('ux-top').value=state.ux;if(state.ux!=='parked')modal.close();render();log('차량 제한 상태 변경 · 모의');});
$('provenance').addEventListener('change',()=>{state.provenance=$('provenance').value;render();log('출처 문구만 변경했습니다. 대역·새 AI 음악 모두 이 검토실에서는 가상 곡입니다.');});
const completeMockJob=()=>{log(act(state,'resolve'));syncScenarioSelector();render();};
$('resolve').addEventListener('click',completeMockJob);
$('resolve-top').addEventListener('click',completeMockJob);
for(const key of configKeys)$(key).addEventListener('input',updateDraft);
$('baseline').addEventListener('click',()=>{writeDraft(BASELINE);$('draft-status').textContent='기준값을 수정 도구로 불러왔습니다. 적용을 눌러 반영하세요.';});
$('apply').addEventListener('click',apply);
$('apply-top').addEventListener('click',apply);
$('expand').addEventListener('click',()=>{const expanded=document.body.classList.toggle('expanded');$('expand').textContent=expanded?'검토 도구':'검토 도구 닫기';scaleWindows();});
$('compare-toggle').addEventListener('click',()=>{$('compare').value=$('compare').value==='none'?'app-017-frozen':'none';render();});
$('page').addEventListener('change',()=>handleAction('page',$('page').value));
window.addEventListener('popstate',event=>{const page=location.hash.slice(1);if(['home','records','library'].includes(page)){state.page=page;view={detailId:page==='records'&&(controls(state).canSelect||state.selectedId===event.state?.detailId)&&state.records.some(r=>r.id===event.state?.detailId)?event.state.detailId:null,trackId:page==='library'&&tracks(state).some(t=>t.id===event.state?.trackId&&(controls(state).canSelect||t.recordId===state.selectedId))?event.state.trackId:null};render();}});
$('scenario-top').innerHTML=$('scenario').innerHTML;
$('ux-top').innerHTML=$('ux').innerHTML;
$('scenario-top').addEventListener('change',()=>{$('scenario').value=$('scenario-top').value;$('scenario').dispatchEvent(new Event('change'));});
$('ux-top').addEventListener('change',()=>{$('ux').value=$('ux-top').value;$('ux').dispatchEvent(new Event('change'));});
$('compare').addEventListener('change',render);
$('viewport').addEventListener('change',render);
$('hitboxes').addEventListener('change',()=>{document.body.classList.toggle('show-hitboxes',$('hitboxes').checked);document.querySelectorAll('.home-frame').forEach(updateFrame);});
window.addEventListener('resize',scaleWindows);
writeDraft(draft);compareOptions();render();
try {candidate=await readCandidate();if(history.length===1){current={id:candidate.revision,label:candidate.label,sourceRevision:candidate.revision,config:candidate.config};history.push(current);writeDraft(current.config);compareOptions();render();}else if(current.sourceRevision!==candidate.revision){$('draft-status').textContent='새 수정안이 준비됐습니다. 적용을 누르면 이전 안과 비교합니다.';}}catch{ $('draft-status').textContent='수정안 파일을 읽지 못했습니다. 기준안으로 검토할 수 있습니다.'; }

$('diagnostics').addEventListener('change',()=>document.querySelectorAll('.home-frame').forEach(updateFrame));
