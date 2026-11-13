"""Bounded non-visible SDK experiment. Dedicated emulator only; no AI API."""
import hashlib
import json
import math
from pathlib import Path
import subprocess
import time
import threading
import journey as j


def main():
    fingerprint = j.adb('shell', 'getprop', 'ro.build.fingerprint').strip()
    assert fingerprint.startswith('Pleos/'), 'Wrong device'
    assert j.adb('shell', 'am', 'get-current-user').strip() == '10', 'Wrong user'
    session = 'lifecycle-' + time.strftime('%Y%m%d-%H%M%S')
    run = j.ROOT / 'runs' / session
    run.mkdir()
    j.dump(j.ROOT / 'lifecycle-latest.json', {'run_path': str(run)})
    deadline = time.monotonic() + 180
    heartbeat_stop = threading.Event()
    def console_session():
        while not heartbeat_stop.is_set() and time.monotonic() < deadline:
            try:
                j.http('/browser/session?client=TodaysTrackLifecycle', 'POST')
            except Exception:
                pass
            heartbeat_stop.wait(.7)
    heartbeat = threading.Thread(target=console_session, daemon=True)
    def rows():
        raw = j.adb('shell', 'run-as', j.PACKAGE, '--user', '10', 'cat', 'files/lifecycle-' + session + '.jsonl')
        (run / 'raw-events.jsonl').write_text(raw, encoding='utf-8')
        return [json.loads(line) for line in raw.splitlines() if line]
    def wait(predicate):
        until = min(deadline, time.monotonic() + 35)
        while time.monotonic() < until:
            try:
                records = rows()
            except RuntimeError as e:
                if 'No such file' not in str(e):
                    raise
                records = []
            found = [r for r in records if predicate(r)]
            if found:
                return found[-1]
            if any(r['kind'] == 'startup_error' for r in records):
                raise RuntimeError('SDK service startup failed; inspect local evidence')
            time.sleep(1)
        raise TimeoutError('Expected event not observed; evidence retained')
    try:
        j.adb('forward', 'tcp:8088', 'tcp:8088')
        # Narrow console diagnostic proved a warmed, continuously active session accepts PUT.
        # This finite harness heartbeat ends in finally; it is not a product worker.
        heartbeat.start()
        time.sleep(5)
        # Each finite experiment starts with no inherited service/listeners.
        j.adb('shell', 'am', 'force-stop', '--user', '10', j.PACKAGE)
        for permission in ['pleos.car.permission.FUSED_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION']:
            j.adb('shell', 'pm', 'grant', '--user', '10', j.PACKAGE, permission)
        j.adb('shell', 'appops', 'set', '2000', 'android:mock_location', 'allow')
        j.adb('shell', 'cmd', 'location', 'providers', 'add-test-provider', 'gps', '--supportsAltitude', '--supportsBearing')
        j.adb('shell', 'cmd', 'location', 'providers', 'set-test-provider-enabled', 'gps', 'true')
        j.adb('shell', 'am', 'start', '--user', '10', '-n', j.PACKAGE + '/.LifecycleProbeActivity', '--es', 'session_id', session)
        ready = wait(lambda r: r['kind'] == 'sdk_ready')
        stopped = wait(lambda r: r['kind'] == 'activity_stopped')
        system = j.adb('shell', 'dumpsys', 'activity', 'activities')
        (run / 'activities-after-stop.txt').write_text(system, encoding='utf-8')
        (run / 'services-after-stop.txt').write_text(j.adb('shell', 'dumpsys', 'activity', 'services', j.PACKAGE), encoding='utf-8')
        pid = j.adb('shell', 'pidof', j.PACKAGE).strip()
        baseline = rows()[-1]['elapsed_ms']
        distance = math.ceil(j.http('/vehicle/state', 'GET')['odometer']['traveled_distance_km']) + 1
        # Virtual Console sessions expire after 3000 ms: refresh after slow ADB reads.
        j.http('/browser/session?client=TodaysTrackLifecycle', 'POST')
        response = j.http('/vehicle/odometer?traveled_distance_km=' + str(distance), 'PUT')
        assert response['traveled_distance_km'] == distance
        j.adb('shell', 'cmd', 'location', 'providers', 'set-test-provider-location', 'gps', '--location', '37.5359,127.0416', '--accuracy', '5')
        j.dump(run / 'controls.json', {'input_after_stopped': True, 'sdk_baseline_elapsed_ms': baseline, 'distance_km': distance,
                                     'latitude': 37.5359, 'longitude': 127.0416, 'pid': pid, 'fingerprint': fingerprint})
        location = wait(lambda r: r['kind'] == 'location' and r['elapsed_ms'] > baseline and (r['latitude'], r['longitude']) == (37.5359, 127.0416))
        odometer = wait(lambda r: r['kind'] == 'odometer' and r['elapsed_ms'] > baseline and r['distance_km'] == distance)
        assert ready['pid'] == stopped['pid'] == location['pid'] == odometer['pid'] == int(pid)
        j.adb('shell', 'am', 'start', '--user', '10', '-n', j.PACKAGE + '/.LifecycleProbeActivity', '--ez', 'stop_probe', 'true')
        wait(lambda r: r['kind'] == 'service_destroyed')
        assert any(r['kind'] == 'sdk_released' for r in rows())
        j.dump(run / 'result.json', {'background': 'supported_in_this_finite_experiment', 'simulation': True, 'activity_stopped_elapsed_ms': stopped['elapsed_ms'],
            'new_location_elapsed_ms': location['elapsed_ms'], 'new_odometer_elapsed_ms': odometer['elapsed_ms'], 'sdk_released': True,
            'raw_sha256': hashlib.sha256((run / 'raw-events.jsonl').read_bytes()).hexdigest(),
            'limitations': ['Not a product recorder', 'No real road, long duration, power or reboot validation']})
        print(json.dumps({'run': str(run), 'result': 'finite background SDK observations verified'}), flush=True)
    except Exception as e:
        j.dump(run / 'result.json', {'background': 'inconclusive', 'error_type': type(e).__name__})
        raise
    finally:
        heartbeat_stop.set()
        heartbeat.join(timeout=12)
        for args in [('shell', 'am', 'stopservice', '--user', '10', '-n', j.PACKAGE + '/.LifecycleProbeService'),
                     ('shell', 'cmd', 'location', 'providers', 'remove-test-provider', 'gps'),
                     ('shell', 'appops', 'set', '2000', 'android:mock_location', 'default')]:
            try:
                j.adb(*args)
            except Exception:
                pass
        try:
            rows()
        except Exception:
            pass


if __name__ == '__main__':
    main()
