"""Lyria API probe. Dry-run by default. No product implementation or real trip data."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent
ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
MODEL = "lyria-3.5"
MAX_ATTEMPTS = 4  # Previous 402 + two songs + one explicitly approved SDK-journey song.
MAX_COMPLETED_SONGS = 3
ESTIMATED_USD_PER_SONG = 0.08  # Official pricing checked 2026-11-12; taxes excluded.
JOURNEY = {
    "source": "SIMULATED_FOR_VALIDATION_NOT_SDK_CAPTURE",
    "scope": "one completed round trip",
    "vehicle_distance_km": 63,
    "vehicle_driving_minutes": 90,
    "segments": [
        {"label": "departure", "minutes": 20, "distance_km": 9},
        {"label": "main journey", "minutes": 40, "distance_km": 30},
        {"label": "return", "minutes": 30, "distance_km": 24},
    ],
    "confirmed_visits": ["cafe", "viewpoint"],
    "returned_to_origin": True,
    "time_of_day": "afternoon",
    "photos": [],
    "memo": None,
}
BASE_PROMPT = """Create a complete indie-pop song of about 90 seconds from a completed
journey, not a live driving soundtrack. Use the entire sequence: a restrained piano
departure, a fuller central section at the cafe and viewpoint, then a calm return
recalling the opening piano texture. Keep the music coherent and listenable.
Segment prominence should reflect the relative driving times, not literal real-time
playback. Vehicle distance is context for the scale of the journey, not a request for
higher tempo or risky driving. Do not invent names, relationships, weather, or exact
geographical locations. The record is synthetic validation data, not a real person's trip.
Journey record:
"""
def make_request(sample, journey):
    if sample == "sdk-journey":
        return make_sdk_request(journey)
    if sample == "vocal":
        direction = (
            "\nSing in Korean. Generate original Korean lyrics using the journey's "
            "departure, cafe, viewpoint and return as material. No named singer imitation. "
            "Use a clear verse, chorus and outro. Do not claim unknown facts."
        )
    else:
        direction = "\nInstrumental only. No singing, spoken words, humming or vocal samples."
    prompt = BASE_PROMPT + json.dumps(journey, ensure_ascii=False) + direction
    return {"model": MODEL, "input": prompt}

def make_sdk_request(journey):
    if (journey.get("source") != "PLEOS_SDK_CAPTURE_FROM_SIMULATED_DRIVE"
            or not journey.get("validation", {}).get("all_checkpoints_match")
            or not journey.get("returned_to_origin")):
        raise ValueError("Verified SDK round-trip capture required; no paid call prepared.")
    distance = journey["distance_km_from_sdk_odometer_delta"]
    lyrics = """[Verse]
출발한 자리에 첫 기록을 남겨
새로운 지점들을 차례로 지나
[Pre-Chorus]
같은 자리 같은 거리 잠시 이어지고
[Chorus]
지났던 지점을 다시 지나와
처음의 자리로 돌아온 기록
기록된 DISTANCE 킬로미터
한 곡 안에 차례로 담아
[Outro]
처음의 자리로 돌아온 기록
이 길의 마지막 줄을 남겨
""".replace("DISTANCE", f"{distance:g}")
    prompt = """Compose and sing an original Korean indie-pop song, about 90 seconds.
This is a post-drive song from SDK-observed records of an accelerated simulator test.
The car API readings below were collected through Pleos Vehicle and FusedLocation SDKs.
Treat numeric distance, relative point order, no-movement observations, return to origin,
and sampling gaps as factual. Observation span includes a process gap and is NOT driving time.
Do not infer people, weather, cafes, landmarks, feelings, road names or events during gaps.
Musical interpretation is creative, not a claim about driver emotion or driving quality.
Map departure to a sparse piano introduction; develop instrumentation through outward
segments; soften at the no-movement observation; recall the piano theme on the return.
Respect the relative order and overall arc. Do not map compressed test speed to BPM.
Use EXACTLY the supplied Korean lyrics, without adding or inventing any lyrical facts.
Do not imitate a named artist. Return the lyrics and song structure along with the audio.
SDK-derived journey summary (exact coordinates withheld):
"""
    prompt += json.dumps(journey, ensure_ascii=False)
    prompt += "\nUser-provided lyrics, sing verbatim:\n" + lyrics
    return {"model": MODEL, "input": prompt}


def store_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def run():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sample", choices=["vocal", "instrumental", "sdk-journey"], required=True)
    parser.add_argument("--generate", action="store_true")
    parser.add_argument("--journey-file", type=Path)
    args = parser.parse_args()
    if args.sample == "sdk-journey" and not args.journey_file:
        parser.error("--journey-file is required for SDK capture")
    journey = json.loads(args.journey_file.read_text(encoding="utf-8-sig")) if args.journey_file else JOURNEY
    sample_dir = ROOT / "results" / args.sample
    sample_dir.mkdir(parents=True, exist_ok=True)
    payload = make_request(args.sample, journey)
    store_json(sample_dir / "request.json", payload)
    store_json(sample_dir / "journey.json", journey)
    if not args.generate:
        print(json.dumps({
            "status": "DRY_RUN_NO_NETWORK_NO_CHARGE",
            "sample": args.sample, "model": MODEL,
            "input_source": journey["source"],
            "request_path": str(sample_dir / "request.json"),
        }))
        return 0
    key = os.environ.get("GEMINI_API_KEY")
    if not key:
        print("NOT_RUN: set the key locally with Set-MusicApiKey.ps1", file=sys.stderr)
        return 2
    local = ROOT / ".local"
    local.mkdir(exist_ok=True)
    ledger_path = local / "attempts.json"
    lock = local / "generation.lock"
    try:
        lock_fd = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    except FileExistsError:
        print("NOT_RUN: another generation is active or an interrupted lock needs review.", file=sys.stderr)
        return 3
    try:
        os.close(lock_fd)
        ledger = json.loads(ledger_path.read_text(encoding="utf-8")) if ledger_path.exists() else []
        completed = sum(item.get("status", "").startswith("AUDIO_RETURNED") for item in ledger)
        if completed >= MAX_COMPLETED_SONGS or len(ledger) >= MAX_ATTEMPTS:
            print("NOT_RUN: the authorized three-song / four-request cap has been reached.", file=sys.stderr)
            return 4
        if (sample_dir / "output.mp3").exists():
            print("NOT_RUN: a completed sample already exists; refusing an accidental paid regeneration.", file=sys.stderr)
            return 5
        attempt = {
            "sample": args.sample, "model": MODEL,
            "attempt": len(ledger) + 1, "status": "STARTED",
            "input_source": journey["source"], "raw_sha256": journey.get("raw_sha256"),
            "request_payload_sha256": hashlib.sha256(json.dumps(payload).encode("utf-8")).hexdigest(),
            "estimated_song_usd_excluding_tax": ESTIMATED_USD_PER_SONG,
        }
        ledger.append(attempt)
        store_json(ledger_path, ledger)  # Reserve before the request; no automatic retries.
        start = time.monotonic()
        try:
            request = urllib.request.Request(
                ENDPOINT, method="POST",
                data=json.dumps(payload).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "Api-Revision": "2026-05-20",
                    "x-goog-api-key": key,
                },
            )
            with urllib.request.urlopen(request, timeout=240) as response:
                result = json.load(response)
            text_parts, audio_blocks = [], []
            for step in result.get("steps", []):
                if step.get("type") != "model_output":
                    continue
                for block in step.get("content", []):
                    if block.get("type") == "audio" and block.get("data"):
                        audio_blocks.append(block)
                    elif block.get("type") == "text" and block.get("text"):
                        text_parts.append(block["text"])
            if len(audio_blocks) != 1:
                raise ValueError("Expected exactly one audio content block; no success claimed.")
            audio_block = audio_blocks[0]
            mime = audio_block.get("mime_type", "audio/mpeg")
            if mime not in ("audio/mpeg", "audio/mp3"):
                raise ValueError("Unexpected output format; no MP3 file written.")
            audio = base64.b64decode(audio_block["data"], validate=True)
            if not audio:
                raise ValueError("Empty audio.")
            # Save only completed data; never label a synthesized placeholder as a generated song.
            temp = sample_dir / "output.mp3.part"
            temp.write_bytes(audio)
            temp.replace(sample_dir / "output.mp3")
            (sample_dir / "generated-text.txt").write_text("\n\n".join(text_parts), encoding="utf-8")
            attempt.update({
                "status": "AUDIO_RETURNED_LISTENING_NOT_ASSESSED",
                "interaction_id": result.get("id"),
                "audio_bytes": len(audio),
                "audio_sha256": hashlib.sha256(audio).hexdigest(),
                "elapsed_seconds": round(time.monotonic() - start, 3),
            })
        except urllib.error.HTTPError as error:
            attempt.update({"status": "HTTP_ERROR", "http_status": error.code,
                            "elapsed_seconds": round(time.monotonic() - start, 3)})
            try:
                body = json.loads(error.read(65536).decode("utf-8"))
                entries = body if isinstance(body, list) else [body]
                details = next((entry.get("error", {}) for entry in entries if isinstance(entry, dict) and isinstance(entry.get("error"), dict)), {})
                attempt["provider_status"] = details.get("status")
                message = str(details.get("message", "")).replace(key, "[REDACTED]")
                attempt["provider_message"] = message[:2000]
            except (ValueError, UnicodeError, TypeError, AttributeError):
                pass
        except Exception as error:
            # Do not print a raw provider response, headers or credentials.
            attempt.update({"status": "ERROR", "error_type": type(error).__name__})
        store_json(ledger_path, ledger)
        store_json(sample_dir / "result.json", attempt)
        print(json.dumps(attempt, ensure_ascii=False))
        return 0 if attempt["status"].startswith("AUDIO_RETURNED") else 1
    finally:
        lock.unlink(missing_ok=True)

if __name__ == "__main__":
    raise SystemExit(run())


