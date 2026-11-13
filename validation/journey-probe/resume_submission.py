"""Finite DEBUG/FIXTURE acceptance-loss and saved-record-selector verification.

Run from the repository root only after the integrated APK and local server are
ready. This script never installs/builds, touches release data, clicks generation,
or calls a provider. It restores DEBUG Room/audio in finally. Real-road, live
response-loss and human listening claims are explicitly out of scope.
"""
import argparse
from contextlib import contextmanager
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import subprocess
import time
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import download_recovery as recovery
import product_capture as product
import submission_device as device


TABLES = ('sessions', 'epochs', 'events', 'gaps', 'generation_intents', 'tracks')
REPOSITORY = Path(__file__).resolve().parents[2]
DEBUG_PACKAGE = 'dev.todaystrack.vehicle'


class LaunchDiagnosticError(RuntimeError):
    """The app-start precondition was not confirmed; no product failure is inferred."""


def require(condition, message):
    # These checks remain active with python -O.
    if not condition:
        raise RuntimeError(message)


@contextmanager
def readonly_database(path):
    require(path.is_file(), 'Required existing database is absent: ' + str(path))
    connection = sqlite3.connect(path.resolve().as_uri() + '?mode=ro', uri=True)
    connection.row_factory = sqlite3.Row
    try:
        yield connection
    finally:
        connection.close()


def rows(database):
    return {table: [tuple(row) for row in database.execute('SELECT * FROM ' + table + ' ORDER BY rowid')]
            for table in TABLES}


def server_state():
    with readonly_database(REPOSITORY / 'server/.local/server.db') as database:
        return {
            'jobs': database.execute('SELECT count(*) FROM jobs').fetchone()[0],
            'attempts': database.execute('SELECT count(*) FROM attempts').fetchone()[0],
            'budget': [tuple(row) for row in database.execute('SELECT * FROM budget ORDER BY id')],
        }


def dispatch_disabled():
    config = json.loads((REPOSITORY / 'server/.local/dispatch.json').read_text(encoding='utf-8-sig'))
    require(config.get('enabled') is False, 'Music dispatch must remain explicitly disabled')


def stop_debug():
    device.adb('shell', 'am', 'force-stop', '--user', '10', DEBUG_PACKAGE)


def start_debug(run, label):
    dispatch_disabled()
    log_path = run / (label + '-launch.txt')

    def text(value):
        return value.decode('utf-8', errors='replace') if isinstance(value, bytes) else (value or '')

    # A -W observer timeout was followed by Displayed and a live PID in the saved
    # Pleos evidence. Issue exactly one start, then observe that same request's
    # process/activity even if -W or its ADB transport times out.
    return_code = None
    command_timed_out = False
    try:
        launch = recovery.command('shell', 'am', 'start', '-W', '--user', '10', '-n',
                                  DEBUG_PACKAGE + '/.MainActivity', check=False)
        output, stderr = text(launch.stdout), text(launch.stderr)
        return_code = launch.returncode
    except subprocess.TimeoutExpired as error:
        output, stderr = text(error.stdout), text(error.stderr)
        command_timed_out = True
    log_path.write_text(output + '\nSTDERR:\n' + stderr, encoding='utf-8')
    matched = re.search(r'^Status:\s*(\S+)', output, re.MULTILINE)
    status = matched.group(1).lower() if matched else None
    deadline = time.monotonic() + 60
    observations = []
    confirmed = False
    consecutive_command_errors = 0

    def observe(*arguments):
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError('Launch liveness observation window ended')
        return subprocess.run([device.ADB, '-P', '5038', '-s', device.SERIAL, *arguments],
                              capture_output=True, check=False, timeout=min(8, remaining))

    while time.monotonic() < deadline:
        observation = {'observedAtMonotonic': time.monotonic()}
        try:
            pid_result = observe('shell', 'pidof', DEBUG_PACKAGE)
            pid_text = text(pid_result.stdout).strip()
            # pidof returns 1 when the process is not running: pending state, not
            # a reason to issue another start or declare the product broken.
            require(pid_result.returncode in (0, 1), 'ADB pidof observation failed')
            pids = set(re.findall(r'\b\d+\b', pid_text))
            top_result = observe('shell', 'dumpsys', 'activity', 'top')
            require(top_result.returncode == 0, 'ADB activity observation failed')
            top = text(top_result.stdout)
            (run / (label + '-activity-top.txt')).write_text(top, encoding='utf-8')
            activity = re.search(r'^\s*ACTIVITY ' + re.escape(DEBUG_PACKAGE) +
                                 r'/\.MainActivity\s+[^\n]*\bpid=(\d+)\s+userId=10\b', top, re.MULTILINE)
            activity_pid = activity.group(1) if activity else None
            observation.update({'processPids': sorted(pids), 'activityPid': activity_pid})
            confirmed = activity_pid is not None and activity_pid in pids
            consecutive_command_errors = 0
        except (subprocess.TimeoutExpired, TimeoutError, RuntimeError) as error:
            observation.update({'observationError': type(error).__name__, 'message': str(error)})
            consecutive_command_errors += 1
        observations.append(observation)
        device.dump(run / (label + '-liveness.json'), observations)
        if confirmed or consecutive_command_errors >= 2:
            break
        remaining = deadline - time.monotonic()
        if remaining > 0:
            time.sleep(min(2, remaining))
    device.dump(run / (label + '-launch-diagnostic.json'), {
        'launchConfirmed': confirmed, 'status': status, 'returnCode': return_code,
        'startCommandTimedOut': command_timed_out, 'startRequestsIssued': 1,
        'confirmation': 'matching numeric debug PID and MainActivity PID',
        'observationWindowSeconds': 60,
        'productRecoveryFailureInferred': False, 'output': log_path.name,
    })
    if not confirmed:
        raise LaunchDiagnosticError('Same-request debug PID/MainActivity liveness was not confirmed; inspect ' + str(log_path))


def replace_room(content):
    for suffix in ('-wal', '-shm'):
        recovery.command('shell', 'run-as', DEBUG_PACKAGE, '--user', '10', 'rm', '-f',
                         'databases/todaystrack.db' + suffix)
    recovery.write_private('databases/todaystrack.db', content)


def save_ui(run, label, focus=True):
    # Pleos may give DrivingView the accessibility focus. The app's right padding
    # is safe even after scrolling; the former heading coordinate could hit a button.
    if focus:
        device.adb('shell', 'input', 'tap', '2515', '100')
    nodes = device.ui_nodes(timeout=30)
    # XML is local evidence; no screenshot upload or external publication.
    import xml.etree.ElementTree as xml
    root = xml.Element('nodes')
    for node in nodes:
        root.append(xml.Element('node', dict(node.attrib)))
    (run / (label + '.xml')).write_bytes(xml.tostring(root, encoding='utf-8'))
    return nodes


def click_node(node):
    require(node.get('enabled') == 'true', 'Record selector is not currently permitted')
    bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
    require(len(bounds) == 4, 'Selector has no observable screen bounds')
    left, top, right, bottom = bounds
    require(right > left and bottom > top, 'Selector has empty bounds')
    device.adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))


def record_label(session):
    try:
        zone = ZoneInfo(session['zoneId'])
    except ZoneInfoNotFoundError as error:
        raise RuntimeError('Session timezone data is unavailable; provide exact --earlier-label and --latest-label') from error
    started = datetime.fromtimestamp(session['startedAtUtc'] / 1000, zone).strftime('%m-%d %H:%M:%S')
    kind = {'DAY': '하루 기록', 'TRIP': '여행 기록'}[session['kind']]
    lyric = {'INSTRUMENTAL': '무가사', 'VOCAL': '가사 있는 곡'}[session['lyricMode']]
    return f"{kind} · {started} · {session['genre']} · {lyric}"


def choose_record(run, description, session, explicit_label, evidence_label):
    nodes = save_ui(run, evidence_label + '-before')
    selector = next((node for node in nodes if node.get('content-desc') == description), None)
    require(selector is not None, 'Saved-record Spinner is absent: ' + description)
    require('Spinner' in selector.get('class', ''), 'Accessible saved-record control is not a Spinner')
    click_node(selector)
    time.sleep(1)
    # Keep the open dropdown focused; a padding tap would dismiss its options.
    options = save_ui(run, evidence_label + '-options', focus=False)
    expected_label = explicit_label or record_label(session)
    candidates = [node for node in options if node.get('text') == expected_label]
    require(len(candidates) == 1,
            'Record option is not unique; supply --earlier-label/--latest-label matching the UI contract')
    selected_label = candidates[0].get('text', '')
    click_node(candidates[0])
    time.sleep(3)
    nodes = save_ui(run, evidence_label + '-selected')
    require(any(selected_label == node.get('text') for node in nodes), 'Chosen record label is not displayed')
    return '\n'.join(node.get('text', '') for node in nodes), selected_label


def verify_selector(run, database, args):
    sealed = [dict(row) for row in database.execute(
        "SELECT * FROM sessions WHERE state='SEALED' ORDER BY startedAtUtc DESC, rowid DESC")]
    if len(sealed) < 2:
        return {'status': 'not_exercised', 'reason': 'Existing DEBUG database has fewer than two SEALED records'}
    latest = sealed[0]
    # Prefer an earlier ungenerated record: this directly exercises the inaccessible-record defect.
    earlier = next((session for session in sealed[1:] if database.execute(
        'SELECT 1 FROM generation_intents WHERE sessionId=?', (session['id'],)).fetchone() is None), sealed[1])
    text, label = choose_record(run, args.selector_desc, earlier, args.earlier_label, 'earlier-record')
    require(args.summary_marker in text, 'Earlier record summary is not displayed')
    require(args.settings_marker in text, 'Selected record settings label is not displayed')
    require(earlier['genre'] in text, 'Earlier record genre is not displayed')
    lyric_labels = {'VOCAL': ('VOCAL', '가사 있는 곡'), 'INSTRUMENTAL': ('INSTRUMENTAL', '무가사')}
    kind_labels = {'DAY': ('DAY', '하루'), 'TRIP': ('TRIP', '여행')}
    require(any(value in text for value in lyric_labels[earlier['lyricMode']]), 'Earlier lyric setting is absent')
    require(any(value in text for value in kind_labels[earlier['kind']]), 'Earlier kind setting is absent')
    current_text, latest_label = choose_record(run, args.selector_desc, latest, args.latest_label, 'latest-record')
    require(args.settings_marker in current_text, 'Latest record settings are not displayed after switching back')
    latest_intent = database.execute('SELECT * FROM generation_intents WHERE sessionId=?', (latest['id'],)).fetchone()
    if latest_intent is not None and latest_intent['state'] == 'AVAILABLE':
        require('곡이 저장되었습니다' in current_text, 'Latest AVAILABLE status is not restored in the UI')
    else:
        require(args.summary_marker in current_text or '생성' in current_text or '접수' in current_text,
                'Latest record has no observable summary/job status')
    return {'status': 'verified', 'earlierSessionId': earlier['id'], 'latestSessionId': latest['id'],
            'earlierLabel': label, 'latestLabel': latest_label, 'generationButtonClicked': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--recovery-wait', type=int, default=35, choices=range(5, 76), metavar='5..75')
    parser.add_argument('--selector-desc', default='저장된 기록 선택')
    parser.add_argument('--summary-marker', default='관측 순서')
    parser.add_argument('--settings-marker', default='기록 설정')
    parser.add_argument('--earlier-label')
    parser.add_argument('--latest-label')
    parser.add_argument('--access-log', type=Path,
                        help='Optional existing server access log containing GET plus the exact by-intent route')
    args = parser.parse_args()
    require(product.PKG == DEBUG_PACKAGE and device.SERIAL == '127.0.0.1:5661', 'Dedicated helper contract changed')
    require(Path.cwd().resolve() == REPOSITORY, 'Run this script from the repository root')
    subprocess.run([device.ADB, '-P', '5038', 'connect', device.SERIAL], capture_output=True, check=True, timeout=15)
    require(device.adb('shell', 'getprop', 'sys.boot_completed').strip() == '1', 'Dedicated AVD must already be booted')
    require(device.adb('shell', 'getprop', 'ro.build.fingerprint').startswith('Pleos/'), 'Dedicated AVD is not Pleos')
    require(device.adb('shell', 'am', 'get-current-user').strip() == '10', 'Dedicated AVD user must be 10')
    package_info = device.adb('shell', 'dumpsys', 'package', DEBUG_PACKAGE)
    require(re.search(r'flags=\[[^\]]*\bDEBUGGABLE\b', package_info) is not None, 'Only the installed DEBUG package is allowed')
    dispatch_disabled()
    run = device.ROOT / 'runs' / ('resume-submission-' + time.strftime('%Y%m%d-%H%M%S'))
    run.mkdir()
    device.dump(device.ROOT / 'resume-submission-latest.json', {'run_path': str(run)})
    baseline_server = server_state()
    log_offset = args.access_log.stat().st_size if args.access_log else None
    stop_debug()
    original = recovery.db_copy(run, 'original')
    original_rows = rows(original)
    intent_row = original.execute("""SELECT i.* FROM generation_intents i JOIN tracks t ON t.jobId=i.jobId
        WHERE i.state='AVAILABLE' AND i.mode='FIXTURE' AND t.provenance='FIXTURE'
        ORDER BY i.rowid DESC LIMIT 1""").fetchone()
    require(intent_row is not None, 'A previously AVAILABLE DEBUG FIXTURE with a saved track is required')
    intent = dict(intent_row)
    with readonly_database(REPOSITORY / 'server/.local/server.db') as server:
        job = server.execute('SELECT * FROM jobs WHERE id=? AND intent=?', (intent['jobId'], intent['id'])).fetchone()
        require(job is not None and job['mode'] == 'FIXTURE' and job['state'] == 'READY'
                and job['hash'] == intent['inputHash'], 'Existing matching READY FIXTURE server job is required')
    require(not original.execute("SELECT 1 FROM sessions WHERE state='OPEN'").fetchone(),
            'Existing DEBUG capture must be normally stopped before fault injection')
    unexpected = original.execute("SELECT id FROM generation_intents WHERE id<>? AND state<>'AVAILABLE'", (intent['id'],)).fetchall()
    require(not unexpected, 'Other unresolved DEBUG intents must be isolated before this bounded experiment')
    audio = {}
    for track in original.execute('SELECT * FROM tracks'):
        require(track['path'].startswith('/data/user/10/' + DEBUG_PACKAGE + '/files/tracks/'), 'Unexpected DEBUG audio path')
        content = recovery.read_private(track['path'])
        require(hashlib.sha256(content).hexdigest() == track['sha256'], 'Original DEBUG audio hash mismatch')
        audio[track['path']] = content
        (run / ('original-' + track['id'] + '.mp3')).write_bytes(content)
        part = recovery.command('exec-out', 'run-as', DEBUG_PACKAGE, '--user', '10', 'cat', track['path'] + '.part', check=False)
        audio[track['path'] + '.part'] = part.stdout if part.returncode == 0 else None
    standalone = sqlite3.connect(run / 'original-standalone.db')
    original.backup(standalone)
    standalone.close()
    injected = sqlite3.connect(run / 'injected.db')
    original.backup(injected)
    injected.execute("UPDATE generation_intents SET jobId=NULL,state='ACCEPTANCE_UNCERTAIN' WHERE id=?", (intent['id'],))
    injected.commit()
    injected.close()
    original.close()
    changed = False
    try:
        changed = True
        replace_room((run / 'injected.db').read_bytes())
        device.dump(run / 'injection.json', {'package': DEBUG_PACKAGE, 'mode': 'FIXTURE', 'faultInjection': True,
                    'fault': 'persisted missing jobId and ACCEPTANCE_UNCERTAIN', 'intentId': intent['id'],
                    'existingJobId': intent['jobId'], 'inputHash': intent['inputHash'], 'releaseAppModified': False})
        # No generation click: startup/onStart recovery must be sufficient.
        start_debug(run, 'startup-recovery')
        time.sleep(args.recovery_wait)
        stop_debug()
        recovered = recovery.db_copy(run, 'recovered')
        resumed = recovered.execute('SELECT * FROM generation_intents WHERE id=?', (intent['id'],)).fetchone()
        require(resumed['state'] == 'AVAILABLE' and resumed['jobId'] == intent['jobId'], 'Startup did not recover the existing job to AVAILABLE')
        require(resumed['inputHash'] == intent['inputHash'] and resumed['payload'] == intent['payload'], 'Fixed input changed during recovery')
        require(rows(recovered) == original_rows, 'Original Room rows were changed beyond recovery to their original values')
        require(server_state() == baseline_server, 'Jobs, attempts or budget changed during recovery')
        dispatch_disabled()
        # Preserve independent recovery evidence before accessing the UI. An AX/UI
        # failure still fails the experiment, while this completed check remains local.
        device.dump(run / 'startup-recovery-result.json', {
            'package': DEBUG_PACKAGE, 'mode': 'FIXTURE', 'faultInjection': True,
            'startupRecoveredExistingJob': True, 'existingJobId': intent['jobId'],
            'sameInputHash': True, 'sdkOriginalRowsUnchanged': True,
            'allRoomRowsEqualOriginal': True, 'newJobs': 0, 'newAttempts': 0,
            'budgetReservationUnchanged': True, 'dispatchEnabled': False,
            'generationButtonClicked': False, 'releaseAppModified': False,
            'realRoadClaimed': False, 'liveResponseLossClaimed': False,
            'selectorVerified': False, 'byIntentGetIndependentlyObserved': False,
        })
        start_debug(run, 'selector-ui')
        time.sleep(4)
        save_ui(run, 'after-startup-recovery')
        selector_result = verify_selector(run, recovered, args)
        recovered.close()
        stop_debug()
        after_selector = recovery.db_copy(run, 'after-selector')
        require(rows(after_selector) == original_rows, 'Simple record selection changed stored records or jobs')
        after_selector.close()
        require(server_state() == baseline_server, 'Simple record selection created server work or reservations')
        dispatch_disabled()
        lookup_observed = False
        if args.access_log:
            with args.access_log.open('rb') as stream:
                stream.seek(log_offset)
                access = stream.read().decode('utf-8', errors='replace')
            route = '/v1/generation-jobs/by-intent/' + intent['id']
            matching = [line for line in access.splitlines() if 'GET' in line and route in line]
            (run / 'by-intent-access.txt').write_text('\n'.join(matching) + '\n', encoding='utf-8')
            require(matching, 'Configured access log did not independently observe the by-intent GET')
            lookup_observed = True
        result = {'package': DEBUG_PACKAGE, 'mode': 'FIXTURE', 'faultInjection': True, 'realRoadClaimed': False,
                  'liveResponseLossClaimed': False, 'releaseAppModified': False, 'generationButtonClicked': False,
                  'startupRecoveredExistingJob': True, 'sameInputHash': True, 'sdkOriginalRowsUnchanged': True,
                  'newJobs': 0, 'newAttempts': 0, 'budgetReservationUnchanged': True,
                  'byIntentGetIndependentlyObserved': lookup_observed, 'selector': selector_result,
                  'limitations': [] if lookup_observed else ['Exact GET method/route requires server access evidence; state recovery alone cannot distinguish idempotent POST.']}
        device.dump(run / 'result.json', result)
        print(json.dumps({'run': str(run), 'startupRecovery': 'verified', 'selector': selector_result['status'],
                          'byIntentGetIndependentlyObserved': lookup_observed}), flush=True)
    except Exception as error:
        device.dump(run / 'failure.json', {'type': type(error).__name__, 'message': str(error), 'automaticExperimentRetries': 0})
        raise
    finally:
        if changed:
            try:
                stop_debug()
                expected_db = (run / 'original-standalone.db').read_bytes()
                replace_room(expected_db)
                for path, content in audio.items():
                    if content is None:
                        recovery.command('shell', 'run-as', DEBUG_PACKAGE, '--user', '10', 'rm', '-f', path)
                    else:
                        recovery.write_private(path, content)
                        require(recovery.read_private(path) == content, 'Restored audio differs: ' + path)
                require(recovery.read_private('databases/todaystrack.db') == expected_db, 'Standalone Room backup was not restored exactly')
                restored = recovery.db_copy(run, 'restored')
                require(rows(restored) == original_rows, 'Restored Room rows differ from originals')
                restored.close()
                device.dump(run / 'restoration.json', {'originalRoomRowsRestored': True, 'originalAudioRestored': True,
                            'releaseUntouched': True, 'debugAppLeftStopped': True})
            except Exception as error:
                device.dump(run / 'restoration-failure.json', {'type': type(error).__name__, 'message': str(error),
                            'backup': str(run / 'original-standalone.db'), 'requiresInspectionBeforeRerun': True})
                raise RuntimeError('DEBUG restoration incomplete; inspect local backup before any rerun: ' + str(run)) from error


if __name__ == '__main__':
    main()
