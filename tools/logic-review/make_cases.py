"""Reproducible synthetic cases. Optional read-only import of preserved Room evidence.
Import output stays under .local; source records and frozen inputs are never written.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sqlite3

HERE=Path(__file__).resolve().parent

def event(seq,elapsed,source='LOCATION',quality=None,point='A',km=None,epoch='e1',boot='boot1',age=0):
    points={'A':(37.0,127.0),'B':(37.1,127.1),'C':(37.2,127.2),'J':(37.000001,127.000001)}
    row=dict(seq=seq,epochId=epoch,bootId=boot,source=source,receivedAtUtc=100000+elapsed,
        receivedElapsedMs=elapsed,quality=quality or ('FRESH' if source=='LOCATION' else 'VALID'))
    if source=='LOCATION':
        row.update(latitude=points[point][0],longitude=points[point][1],sourceTimestampMs=row['receivedAtUtc']-age)
    else:
        row['odometerKm']=km
    return row

def cases():
    return {'cases':[
        {'id':'roundtrip','title':'왕복 + 같은 위치 반복','origin':'합성 / 실제 주행 아님',
         'question':'C의 같은 좌표 반복은 관측 근거다. 실제 정차·방문이라고 확정할 것인가?',
         'events':[event(1,0),event(2,100,'ODOMETER',km=100),event(3,1000,point='B'),
                   event(4,1100,'ODOMETER',km=102),event(5,2000,point='C'),event(6,4000,point='C'),
                   event(7,4100,'ODOMETER',km=104),event(8,5000,point='B'),event(9,6000),event(10,6100,'ODOMETER',km=108)]},
        {'id':'legacy-stale','title':'기존 FRESH 중 지연된 원본','origin':'기존 ObservationFactsTest의 위험 조건 재구성',
         'events':[event(1,30000),event(2,30100,point='B',age=20000),event(3,31000)]},
        {'id':'location-gap','title':'위치만 공백 · 거리는 계속 관측','origin':'합성 / 소스별 공백 검토',
         'question':'위치 공백을 경로로 연결하면 안 된다. 정상 거리 관측은 유지해야 한다.',
         'events':[event(1,0),event(2,100,'ODOMETER',km=100),event(3,1000,'ODOMETER',km=102),event(4,2000)],
         'gaps':[dict(sourceMask='LOCATION',lastDurableSeq=1,firstAfterSeq=4,reason='LOCATION_INTERRUPTED')]},
        {'id':'odometer-gap','title':'거리 공백 양끝의 증가','origin':'합성 / 연속거리와 공백 차이 구분',
         'events':[event(1,0,'ODOMETER',km=100),event(2,100),event(3,2000,point='B'),event(4,2100,'ODOMETER',km=108)],
         'gaps':[dict(sourceMask='ODOMETER',lastDurableSeq=1,firstAfterSeq=4,reason='ODOMETER_INTERRUPTED')]},
        {'id':'clock-regression','title':'같은 boot · 단조 시각 역행','origin':'합성 / 구현 결함 재현',
         'events':[event(1,2000,'ODOMETER',km=100),event(2,1000,'ODOMETER',km=108),event(3,3000,'ODOMETER',km=109)]},
        {'id':'reset-boot','title':'계기 리셋과 부팅 변경','origin':'기존 JourneySummaryBuilderTest.resetAndUnknownBootDoNotInflateDistance 조건',
         'events':[event(1,0,'ODOMETER',km=200),event(2,100,'ODOMETER','RESET',km=1),
                   event(3,200,'ODOMETER',km=2),event(4,300,'ODOMETER',km=10,epoch='e2',boot='unknown'),
                   event(5,400,'ODOMETER',km=20,epoch='e2',boot='unknown')]},
        {'id':'gps-jitter','title':'출발점 근처로 돌아왔지만 좌표가 다름','origin':'합성 / 제품 판단용',
         'question':'현재 정확히 같은 좌표만 같은 위치다. 근처 귀환·머묾의 허용 범위는 새 임계값을 정하기 전 판단 필요.',
         'events':[event(1,0),event(2,1000,point='B'),event(3,2000,point='J')]},
        {'id':'existing-order-test','title':'기존 순서·공백 단위 검증 사례','origin':'JourneySummaryBuilderTest.observedOrderKeepsReturnAndUnknownGapWithoutInventingVisits의 Fact 입력',
         'events':[event(1,100),event(2,200,'ODOMETER',km=100),event(3,300,point='B'),
                   event(4,400,'ODOMETER',km=102),event(5,500,quality='PRE_EPOCH',point='C'),
                   event(6,900,epoch='e2'),event(7,950,'ODOMETER',km=110,epoch='e2'),event(8,1000,'ODOMETER',km=111,epoch='e2')],
         'gaps':[dict(sourceMask='LOCATION',lastDurableSeq=4,firstAfterSeq=6,reason='RECONSTRUCTED_TEST_BOUNDARY'),
                 dict(sourceMask='ODOMETER',lastDurableSeq=4,firstAfterSeq=7,reason='RECONSTRUCTED_TEST_BOUNDARY')]}]}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--evidence-db',type=Path)
    parser.add_argument('--output',type=Path,default=HERE / 'cases.json')
    args=parser.parse_args()
    data=cases()
    if args.evidence_db:
        path=args.evidence_db.resolve()
        before=hashlib.sha256(path.read_bytes()).hexdigest()
        with sqlite3.connect(path.as_uri()+'?mode=ro&immutable=1',uri=True) as db:
            db.row_factory=sqlite3.Row
            # Import sealed sessions only; never inject or rewrite original product records.
            for s in db.execute("SELECT * FROM sessions WHERE state='SEALED'"):
                events=[dict(e) for e in db.execute('SELECT * FROM events WHERE sessionId=? ORDER BY seq',(s['id'],))]
                gaps=[dict(g) for g in db.execute('SELECT * FROM gaps WHERE sessionId=?',(s['id'],))]
                epochs={e['id']:e['startedAtUtc'] for e in db.execute('SELECT * FROM epochs WHERE sessionId=?',(s['id'],))}
                frozen=[dict(i) for i in db.execute('SELECT id,inputHash,payload,state,mode,jobId FROM generation_intents WHERE sessionId=?',(s['id'],))]
                data['cases'].append(dict(id=s['id'],title='보존 SDK 기록 · '+s['id'][:8],
                    origin='기존 Room 증거의 읽기 전용 사본 / SDK + SIMULATED; 실제 도로 아님',
                    provenance=dict(path=str(path),sha256=before),session=dict(s),events=events,gaps=gaps,
                    epochStartsUtc=epochs,frozenInputs=frozen))
        assert hashlib.sha256(path.read_bytes()).hexdigest()==before
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'{len(data["cases"])} cases -> {args.output}')

if __name__=='__main__':
    main()
