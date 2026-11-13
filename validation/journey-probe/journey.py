"""Finite Pleos emulator SDK capture. No music API, credentials or product service."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import time
import urllib.request

ROOT = Path(__file__).resolve().parent
ADB = r'C:\Users\xiui\AppData\Local\Android\Sdk\platform-tools\adb.exe'
SERIAL = '127.0.0.1:5661'
PACKAGE = 'ai.pleos.playground.sample.vehicle'
ACTION = PACKAGE + '.VALIDATION'
BASE = 'http://127.0.0.1:8088'
POINTS = [
    ('P0', 20000, 37.4979, 127.0276),
    ('P1', 20001, 37.5109, 127.0276),
    ('P2', 20003, 37.5249, 127.0276),
    ('P3', 20003, 37.5249, 127.0276),
    ('P4', 20005, 37.5109, 127.0276),
    ('P5', 20006, 37.4979, 127.0276),
]

def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def adb(*args):
    result = subprocess.run([ADB, '-P', '5038', '-s', SERIAL, *args], capture_output=True, timeout=35)
    if result.returncode:
        raise RuntimeError('ADB command failed: ' + result.stderr.decode(errors='replace')[:500])
    return result.stdout.decode('utf-8', errors='strict').replace('\r\n', '\n')

def http(path, method):
    with urllib.request.urlopen(urllib.request.Request(BASE + path, method=method), timeout=10) as response:
        return json.load(response)

def rows(run):
    try:
        raw = adb('shell', 'run-as', PACKAGE, '--user', '10', 'cat', 'files/journey-' + run.name + '.jsonl')
    except RuntimeError as error:
        if 'No such file or directory' in str(error):
            return []  # Activity creation and first durable write are asynchronous.
        raise
    (run / 'raw-events.jsonl').write_text(raw, encoding='utf-8')
    parsed = []
    for line in raw.splitlines():
        try:
            parsed.append(json.loads(line))
        except json.JSONDecodeError:
            # A concurrent append can expose an incomplete final line; poll again.
            if line != raw.splitlines()[-1]:
                raise
    return parsed

def wait_for(run, predicate, description):
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        records = rows(run)
        matches = [r for r in records if predicate(r)]
        if matches:
            return matches[-1]
        if any(r['kind'] == 'capture_closed' and r.get('reason') != 'finished' for r in records):
            raise RuntimeError('SDK capture closed before ' + description)
        time.sleep(1)
    raise TimeoutError('No observed SDK response for ' + description + '; raw evidence retained.')

def broadcast(command, marker='none'):
    return adb('shell', 'am', 'broadcast', '--user', '10', '-a', ACTION, '-p', PACKAGE,
               '--es', 'command', command, '--es', 'marker', marker)

def launch(session):
    adb('shell', 'am', 'force-stop', '--user', '10', PACKAGE)
    adb('shell', 'am', 'start', '--user', '10', '-n', PACKAGE + '/.JourneyProbeActivity',
        '--es', 'session_id', session)

def summarize(run):
    raw_path = run / 'raw-events.jsonl'
    records = [json.loads(line) for line in raw_path.read_text(encoding='utf-8').splitlines() if line]
    controls = json.loads((run / 'controls.json').read_text(encoding='utf-8'))
    checkpoints = []
    for control in controls['checkpoints']:
        actual = [r for r in records if r['kind'] == 'odometer' and r['marker'] == control['marker']][-1]
        loc = actual.get('latest_location', {})
        if actual['source'] != 'VEHICLE_SDK' or actual['distance_km'] != control['distance_km']:
            raise ValueError('Odometer did not match emulator input: ' + control['marker'])
        if (loc.get('latitude'), loc.get('longitude')) != (control['latitude'], control['longitude']):
            raise ValueError('SDK location snapshot did not match input: ' + control['marker'])
        if not any(r['kind'] == 'location' and r.get('source') == 'FUSED_LOCATION_SDK'
                   and r['elapsed_ms'] > control['sdk_baseline_elapsed_ms']
                   and (r['latitude'], r['longitude']) == (loc['latitude'], loc['longitude']) for r in records):
            raise ValueError('Missing independent FusedLocation callback')
        checkpoints.append(actual)
    if len(checkpoints) != len(POINTS):
        raise ValueError('Incomplete journey: refusing to prepare paid generation')
    point_ids = {}
    path, segments = [], []
    for checkpoint in checkpoints:
        loc = checkpoint['latest_location']
        point = (loc['latitude'], loc['longitude'])
        if point not in point_ids:
            point_ids[point] = chr(ord('A') + len(point_ids))
        path.append(point_ids[point])
    for index, (a, b) in enumerate(zip(checkpoints, checkpoints[1:])):
        delta = b['distance_km'] - a['distance_km']
        if delta < 0:
            raise ValueError('Odometer reset needs separate handling')
        role = 'no_movement_observed' if delta == 0 and path[index] == path[index + 1] else (
            'return_to_previous_position' if path[index + 1] in path[:index + 1] else 'outward')
        segments.append({'from': path[index], 'to': path[index + 1], 'distance_km': delta,
                         'observed_interval_seconds': round((b['elapsed_ms'] - a['elapsed_ms']) / 1000, 3),
                         'observation': role})
    openings = [r for r in records if r['kind'] == 'capture_opened']
    gaps = []
    for opening in openings[1:]:
        previous = [r for r in records if r['elapsed_ms'] < opening['elapsed_ms']
                    and r['kind'] in ('odometer', 'location')]
        following = [r for r in records if r['elapsed_ms'] > opening['elapsed_ms']
                     and r['kind'] in ('odometer', 'location')]
        if previous and following:
            gaps.append({'reason': 'collector_process_stopped_and_reopened',
                         'unobserved_interval_seconds': round((following[0]['elapsed_ms'] - previous[-1]['elapsed_ms']) / 1000, 3)})
    p4 = next(r for r in checkpoints if r['marker'] == 'P4')
    background_seen = any(r['kind'] in ('activity_paused', 'activity_stopped')
                          and controls['home_baseline_elapsed_ms'] <= r['elapsed_ms'] <= p4['elapsed_ms'] for r in records)
    result = {
        'source': 'PLEOS_SDK_CAPTURE_FROM_SIMULATED_DRIVE', 'session_id': run.name,
        'raw_sha256': hashlib.sha256(raw_path.read_bytes()).hexdigest(),
        'simulation': True, 'accelerated_test_scenario': True,
        'vehicle_sdk': '2.3.2', 'fused_location_sdk': '1.1.3',
        'distance_km_from_sdk_odometer_delta': checkpoints[-1]['distance_km'] - checkpoints[0]['distance_km'],
        'capture_span_seconds': round((checkpoints[-1]['elapsed_ms'] - checkpoints[0]['elapsed_ms']) / 1000, 3),
        'duration_meaning': 'wall-clock observation span, including process gap; not vehicle driving time',
        'position_sequence': path, 'returned_to_origin': path[0] == path[-1], 'segments': segments,
        'observed_sampling_gaps': gaps,
        'validation': {'all_checkpoints_match': True, 'checkpoint_count': len(checkpoints),
                       'sdk_location_callback_count': sum(r['kind'] == 'location' for r in records),
                       'sdk_odometer_callback_count': sum(r['kind'] == 'odometer' for r in records),
                       'home_then_sdk_snapshot_observed': background_seen,
                       'process_reopened': len(openings) > 1,
                       'persisted_file_prefix_verified': controls['persisted_file_prefix_verified']},
        'unknown': ['place names', 'weather', 'companions', 'emotional state', 'events during sampling gap'],
        'privacy': 'Exact coordinates remain in local SDK evidence; music input uses relative position labels.',
    }
    dump(run / 'journey.json', result)
    print(json.dumps({'stage': 'SUMMARY_READY', 'journey': str(run / 'journey.json'),
                      'distance_km': result['distance_km_from_sdk_odometer_delta'],
                      'span_seconds': result['capture_span_seconds'], 'path': path,
                      'validation': result['validation'], 'gaps': gaps}, ensure_ascii=False), flush=True)
    return result

def capture():
    fingerprint = adb('shell', 'getprop', 'ro.build.fingerprint')
    if 'Pleos/' not in fingerprint:
        raise RuntimeError('Target is not the dedicated Pleos emulator')
    session = 'sdk-journey-' + time.strftime('%Y%m%d-%H%M%S')
    run = ROOT / 'runs' / session
    run.mkdir(parents=True, exist_ok=False)
    state = http('/vehicle/state', 'GET')
    distance_base = math.ceil(state['odometer']['traveled_distance_km']) + 1
    controls = {'session_id': session, 'simulation': True, 'checkpoints': [], 'console_starting_distance_km': distance_base}
    dump(ROOT / 'latest-run.json', {'run_path': str(run), 'session_id': session})
    adb('forward', 'tcp:8088', 'tcp:8088')
    adb('shell', 'pm', 'grant', '--user', '10', PACKAGE, 'pleos.car.permission.FUSED_LOCATION')
    adb('shell', 'appops', 'set', '2000', 'android:mock_location', 'allow')
    adb('shell', 'cmd', 'location', 'providers', 'add-test-provider', 'gps', '--supportsAltitude', '--supportsBearing')
    adb('shell', 'cmd', 'location', 'providers', 'set-test-provider-enabled', 'gps', 'true')
    launch(session)
    wait_for(run, lambda r: r['kind'] == 'collection_started', 'initial SDK readiness')
    for marker, initial_distance, latitude, longitude in POINTS:
        distance = distance_base + initial_distance - POINTS[0][1]
        if marker == 'P3':
            controls['home_requested_wall_ms'] = int(time.time() * 1000)
            controls['home_baseline_elapsed_ms'] = rows(run)[-1]['elapsed_ms']
            adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
            time.sleep(6)
        if marker == 'P5':
            before = (run / 'raw-events.jsonl').read_text(encoding='utf-8')
            adb('shell', 'am', 'force-stop', '--user', '10', PACKAGE)
            after = adb('shell', 'run-as', PACKAGE, '--user', '10', 'cat', 'files/journey-' + session + '.jsonl')
            controls['persisted_file_prefix_verified'] = after.startswith(before)
            (run / 'after-force-stop.jsonl').write_text(after, encoding='utf-8')
            if not controls['persisted_file_prefix_verified']:
                raise ValueError('Capture file prefix was lost after stopping the process')
            time.sleep(3)
            launch(session)
            wait_for(run, lambda r: r['kind'] == 'capture_opened' and r['previous_bytes'] > 0, 'durable reopen')
        http('/browser/session?client=TodaysTrackJourneyValidation', 'POST')
        response = http('/vehicle/odometer?traveled_distance_km=' + str(distance), 'PUT')
        if response.get('traveled_distance_km') != distance:
            raise ValueError('Console did not accept the requested distance')
        baseline = rows(run)[-1]['elapsed_ms']
        injected = int(time.time() * 1000)
        adb('shell', 'cmd', 'location', 'providers', 'set-test-provider-location', 'gps',
            '--location', str(latitude) + ',' + str(longitude), '--accuracy', '5')
        wait_for(run, lambda r: r['kind'] == 'location' and r['elapsed_ms'] > baseline
                 and (r['latitude'], r['longitude']) == (latitude, longitude), marker + ' FusedLocation')
        broadcast('snapshot', marker)
        reading = wait_for(run, lambda r: r['kind'] == 'odometer' and r['marker'] == marker, marker + ' Vehicle read')
        controls['checkpoints'].append({'marker': marker, 'distance_km': distance, 'latitude': latitude,
                                       'longitude': longitude, 'gps_input_wall_ms': injected, 'sdk_baseline_elapsed_ms': baseline,
                                       'sdk_distance_km': reading['distance_km']})
        dump(run / 'controls.json', controls)
        print(json.dumps({'stage': 'SDK_CHECKPOINT', 'marker': marker, 'distance_km': reading['distance_km'],
                          'latest_location': reading.get('latest_location')}), flush=True)
        time.sleep(3)
    broadcast('finish')
    wait_for(run, lambda r: r['kind'] == 'capture_closed' and r['reason'] == 'finished', 'capture closure')
    summarize(run)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['capture', 'summarize'])
    parser.add_argument('--run', type=Path)
    args = parser.parse_args()
    if args.command == 'capture':
        capture()
    else:
        summarize(args.run)


