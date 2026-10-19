"""Emulator end-to-end test.

1. Launch the app, download the smallest model (leaving the app midway: it must keep going).
2. Share two Italian clips at once, like WhatsApp would, and wait for both transcripts.
3. Fail on any crash, printing the stack trace.

Drives the Compose UI through the accessibility tree (uiautomator dump), so it needs no test code
inside the app.
"""
import re
import subprocess
import sys
import time

PKG = "com.tiktok7daniel.voceitaliana"


def adb(*args, check=True):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check).stdout


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
    xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        text, x1, y1, x2, y2 = m.group(1), *map(int, m.groups()[1:])
        yield text, ((x1 + x2) // 2, (y1 + y2) // 2)


def screen_text():
    return " | ".join(t for t, _ in nodes() if t)


def tap(fragment, swipes=4):
    for _ in range(swipes + 1):
        for text, (x, y) in nodes():
            if fragment in text:
                print(f"tap '{text}' at {x},{y}")
                adb("shell", "input", "tap", str(x), str(y))
                return
        adb("shell", "input", "swipe", "500", "1500", "500", "700", "300")
        time.sleep(1)
    fail(f"'{fragment}' not found on screen")


def check_crash():
    log = adb("logcat", "-d", "-b", "crash", check=False)
    if PKG in log or not adb("shell", "pidof", PKG, check=False).strip():
        print("::error::App crashed")
        print(log or adb("logcat", "-d", "-t", "400", check=False))
        sys.exit(1)


def fail(msg):
    print(f"::error::{msg}")
    print("screen:", screen_text())
    print(adb("logcat", "-d", "-t", "200", "VoceItaliana:V", "AndroidRuntime:E", "*:S", check=False))
    sys.exit(1)


def wait_for(pattern, seconds, step=5, leave_at=None):
    for i in range(0, seconds, step):
        time.sleep(step)
        check_crash()
        if leave_at is not None and i == leave_at:
            print("pressing HOME: work must continue in the background")
            adb("shell", "input", "keyevent", "KEYCODE_HOME")
            time.sleep(20)
            check_crash()
            adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
            time.sleep(3)
        text = screen_text()
        if re.search(pattern, text):
            return text
    fail(f"timed out waiting for /{pattern}/")


apk, clips = sys.argv[1], sys.argv[2:]
adb("install", "-r", apk)
adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)
adb("logcat", "-c", "-b", "all", check=False)
adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
time.sleep(6)
check_crash()
print("screen:", screen_text())

# 1. Model download (keeps going while the app is in the background).
tap("Whisper Small")
time.sleep(1)
tap("تحميل")
wait_for(r"Whisper Small[^|]*\| متحمّل|Whisper Small · متحمّل", 600, leave_at=10)
print("model downloaded")

# 2. Two clips shared at the same time.
for clip in clips:
    remote = f"/sdcard/Music/{clip.split('/')[-1]}"
    adb("push", clip, remote)
    adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", f"file://{remote}", check=False)
time.sleep(5)
rows = adb("shell", "content", "query", "--uri", "content://media/external/audio/media", "--projection", "_id:_display_name", check=False)
print(rows)
ids = re.findall(r"_id=(\d+), _display_name=(it\d\.wav)", rows)
if len(ids) < len(clips):
    fail("clips not visible in MediaStore")
for media_id, name in ids:
    adb("shell", "am", "start", "-a", "android.intent.action.SEND", "-t", "audio/wav",
        "--eu", "android.intent.extra.STREAM", f"content://media/external/audio/media/{media_id}",
        "--grant-read-uri-permission", "-n", f"{PKG}/.MainActivity")
    time.sleep(2)
text = wait_for(r"(جملة.*){%d}" % len(clips), 600, leave_at=10)
print("screen:", text)
print("End-to-end test passed: model downloaded, both clips transcribed in parallel, no crash")
