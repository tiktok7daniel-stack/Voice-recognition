"""Emulator smoke test: launch the app, start a model download, and fail on any crash.

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


def swipe_up():
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size", check=False))
    w, h = (int(m.group(1)), int(m.group(2))) if m else (320, 640)
    adb("shell", "input", "swipe", str(w // 2), str(h * 3 // 4), str(w // 2), str(h // 4), "400")


def screen_height():
    m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size", check=False))
    return int(m.group(2)) if m else 640


def tap(fragment, swipes=6):
    h = screen_height()
    for _ in range(swipes + 1):
        for text, (x, y) in nodes():
            # The floating bottom bar covers the lowest part of the screen: scroll instead.
            if fragment in text and y < h * 0.8:
                print(f"tap '{text}' at {x},{y}")
                adb("shell", "input", "tap", str(x), str(y))
                return
        swipe_up()
        time.sleep(1)
    raise SystemExit(f"'{fragment}' not found on screen:\n" + "\n".join(t for t, _ in nodes()))


def shot(name):
    """Screenshot for design review; published next to the APK."""
    with open(name, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)


def crashed():
    log = adb("logcat", "-d", "-b", "crash", check=False)
    return log if PKG in log else ""


def alive():
    return adb("shell", "pidof", PKG, check=False).strip() != ""


def screen_text():
    return " | ".join(t for t, _ in nodes() if t)


adb("install", "-r", sys.argv[1])
adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)
adb("logcat", "-c", "-b", "all", check=False)
adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
time.sleep(6)
print("screen:", screen_text())
shot("screen-home.png")

tap("Whisper Small")
time.sleep(1)
tap("تحميل Whisper Small")

for i in range(12):
    time.sleep(5)
    report = crashed()
    if report or not alive():
        print("::error::App crashed after starting the download")
        print(report or adb("logcat", "-d", "-t", "300", check=False))
        sys.exit(1)
    if i == 1:
        # Leave the app: the download must keep going in the background.
        adb("shell", "input", "keyevent", "KEYCODE_HOME")
print("notifications:", re.findall(r"android\.title=String \(([^)]*)\)", adb("shell", "dumpsys", "notification", "--noredact", check=False)))
adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
time.sleep(3)
print("screen after 60s:", screen_text())
shot("screen-downloading.png")
print("Smoke test passed: no crash, process alive")
