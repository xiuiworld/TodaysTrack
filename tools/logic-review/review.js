// Presentation only. Every quality, distance, summary, payload and job result comes from Kotlin.
let report,initial,selected=0;
const $=id=>document.getElementById(id);
const pretty=value=>JSON.stringify(value,null,2);
function table(id,rows,columns){
  const el=$(id);el.replaceChildren();
  const t=document.createElement('table'),head=document.createElement('tr');
  for(const [key,label] of columns){const th=document.createElement('th');th.textContent=label;head.append(th);}
  t.append(head);
  for(const row of rows||[]){const tr=document.createElement('tr');for(const [key] of columns){const td=document.createElement('td');td.textContent=typeof row[key]==='object'?pretty(row[key]):String(row[key]??'—');tr.append(td);}t.append(tr);}
  el.append(t);
}
function compare(a,b,path='',rows=[]){
  if(JSON.stringify(a)===JSON.stringify(b))return rows;
  if(a&&b&&typeof a==='object'&&typeof b==='object'&&!Array.isArray(a)&&!Array.isArray(b)){
    for(const k of new Set([...Object.keys(a),...Object.keys(b)]))compare(a[k],b[k],path?path+'.'+k:k,rows);
  }else rows.push(path+'\n  전: '+JSON.stringify(a)+'\n  후: '+JSON.stringify(b));
  return rows;
}
function render(){
 const c=report.input.cases[selected],a=report.baseline.cases.find(x=>x.id===c.id),b=report.current.cases.find(x=>x.id===c.id);
 $('origin').textContent=c.origin||'사용자 편집 입력';$('question').textContent=c.question||'';
 $('input').value=pretty({cases:[c]});
 $('limits').textContent=report.limits.join(' · ');
 $('metrics').textContent=a.error||b.error?pretty({before:a.error,after:b.error}):`연속거리 ${a.summary.continuousDistanceKm} → ${b.summary.continuousDistanceKm} km / 공백 ${b.summary.gapCount}개 / 귀환 관측 ${b.summary.returnObserved} / 입력 ${c.events.length}개`;
 const obsCols=[['seq','seq'],['source','소스'],['storedQuality','원본'],['summaryQuality','요약 판정'],['positionIncluded','위치 포함'],['reason','근거'],['capturePolicyDiagnostic','수집 정책 진단']];
 const distCols=[['seq','seq'],['previousSeq','이전 seq'],['km','누적 km'],['elapsedMs','수신 단조 ms'],['elapsedDeltaMs','유효 시간차'],['quality','원본 판정'],['contributedContinuousKm','기여 km'],['crossingOdometerGaps','거리 공백']];
 table('before-observations',a.observations,obsCols);table('after-observations',b.observations,obsCols);
 table('before-distance',a.distanceSteps,distCols);table('after-distance',b.distanceSteps,distCols);
 for(const [prefix,item] of [['before',a],['after',b]]){
  for(const [id,key] of [['summary','summary'],['snapshot','snapshot'],['provider','providerRequest'],['job','jobExperiment']])$(prefix+'-'+id).textContent=pretty(item[key]??item.error);
 }
 $('sequence').textContent=b.summary?(b.summary.positionSegments||[]).map((s,i)=>`관측 구간 ${i+1}: ${s.positionSequence.join(' → ')} (seq ${s.positionEvidenceSeqs.join(', ')})`).join('\n공백·연속성 미확정 경계\n'):'요약 불가';
 $('frozen').textContent=pretty(c.frozenInputs||'이 사례에는 기존 frozen 입력 없음');
 $('recovery').textContent=pretty(b.vehicleRecoveryPolicy);
 const diffs=compare(a,b);$('diff').textContent=diffs.length?diffs.join('\n\n'):'동일 입력에서 변경 전후 결과가 같습니다.';
 $('diff').className=diffs.length?'changed':'';
 $('metadata').textContent=pretty({baseline:report.baselineMetadata,current:report.currentMetadata,limits:report.limits,serverValidation:b.serverValidation,provenance:c.provenance});
}
function setReport(data){report=data;selected=Math.max(0,report.input.cases.findIndex(c=>c.id===decodeURIComponent(location.hash.slice(1))));$('cases').replaceChildren();report.input.cases.forEach((c,i)=>{const o=document.createElement('option');o.value=i;o.textContent=c.title||c.id;$('cases').append(o);});$('cases').value=selected;render();}
$('cases').addEventListener('change',()=>{selected=Number($('cases').value);history.replaceState(null,'','#'+encodeURIComponent(report.input.cases[selected].id));render();});
window.addEventListener('hashchange',()=>{if(report)setReport(report);});
$('reset').addEventListener('click',()=>{setReport(structuredClone(initial));$('status').textContent='초기 Kotlin 실행 결과를 복원했습니다.';});
$('evaluate').addEventListener('click',async()=>{
 $('evaluate').disabled=true;$('status').textContent='변경 전·현재 Kotlin 실행 중…';
 try{const input=JSON.parse($('input').value);const response=await fetch('/evaluate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(input)});const data=await response.json();if(!response.ok)throw Error(data.error);setReport(data);$('status').textContent='완료 · 두 버전의 결과와 입력을 새 로컬 파일로 보존했습니다.';}
 catch(e){$('status').textContent='실행 실패: '+e.message;}finally{$('evaluate').disabled=false;}
});
fetch('/report.json').then(r=>r.json()).then(data=>{initial=structuredClone(data);setReport(data);}).catch(e=>{$('status').textContent=e.message;});
