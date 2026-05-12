#!/usr/bin/env python3
"""
YogaFlow 3D — ADB integration test.

Runs on the currently connected ADB device.

Usage:
    python3 scripts/integration_test.py            # build + install + test
    python3 scripts/integration_test.py --skip-install  # test only (APK already installed)
    python3 scripts/integration_test.py --screenshots   # save screenshots per step
"""

import subprocess, sys, time, argparse, os, re, xml.etree.ElementTree as ET
from typing import Optional
from pathlib import Path

PKG = "com.yogaflow"
ACTIVITY = f"{PKG}/.MainActivity"
ARTIFACTS = Path("test-artifacts")


# ── helpers ──────────────────────────────────────────────────────────────────

def adb(*args, check=True, capture=True) -> str:
    cmd = ["adb", *args]
    r = subprocess.run(cmd, capture_output=capture, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed:\n{r.stderr}")
    return r.stdout.strip()


def shell(cmd: str, check=True) -> str:
    return adb("shell", cmd, check=check)


def tap(x: int, y: int, label: str = ""):
    log(f"  tap ({x},{y}) {label}")
    shell(f"input tap {x} {y}")
    time.sleep(1.5)


def screenshot(name: str):
    if not SCREENSHOTS:
        return
    ARTIFACTS.mkdir(exist_ok=True)
    path = ARTIFACTS / f"{name}.png"
    shell("screencap /sdcard/_test_shot.png")
    adb("pull", "/sdcard/_test_shot.png", str(path))
    log(f"  screenshot → {path}")


def logcat_since(tag_filters: list[str]) -> str:
    """Dump all buffered logcat (since last clear) filtered by tags (all levels)."""
    # Append :V to each tag so Verbose-level messages are not silently dropped
    tagged = [f"{t}:V" for t in tag_filters]
    args = ["logcat", "-d"] + sum([["-s", t] for t in tagged], [])
    return adb(*args)


def screen_size() -> tuple[int, int]:
    out = shell("wm size")
    m = re.search(r"(\d+)x(\d+)", out)
    if m:
        return int(m.group(1)), int(m.group(2))
    return 1080, 1920


def ui_dump() -> "Optional[ET.Element]":
    try:
        shell("uiautomator dump /sdcard/_ui.xml", check=False)
        adb("pull", "/sdcard/_ui.xml", "/tmp/_yogaflow_ui.xml")
        return ET.parse("/tmp/_yogaflow_ui.xml").getroot()
    except Exception:
        return None


def find_bounds(root: ET.Element, resource_id: str) -> "Optional[tuple[int, int]]":
    """Return center (x, y) of a node by resource-id."""
    for node in root.iter("node"):
        if node.get("resource-id", "").endswith(resource_id):
            b = node.get("bounds", "")
            m = re.findall(r"\d+", b)
            if len(m) == 4:
                x = (int(m[0]) + int(m[2])) // 2
                y = (int(m[1]) + int(m[3])) // 2
                return x, y
    return None


# ── test runner ───────────────────────────────────────────────────────────────

PASS, FAIL = "PASS", "FAIL"
results: list[tuple[str, str, str]] = []
SCREENSHOTS = False


def log(msg: str):
    print(msg, flush=True)


def test(name: str):
    def decorator(fn):
        def wrapper():
            log(f"\n{'─'*50}")
            log(f"TEST: {name}")
            try:
                fn()
                results.append((name, PASS, ""))
                log(f"  → PASS")
            except AssertionError as e:
                results.append((name, FAIL, str(e)))
                log(f"  → FAIL: {e}")
            except Exception as e:
                results.append((name, FAIL, f"exception: {e}"))
                log(f"  → FAIL (exception): {e}")
        return wrapper
    return decorator


# ── tests ─────────────────────────────────────────────────────────────────────

@test("device connected")
def t_device():
    out = adb("devices")
    assert "device" in out, "No ADB device connected"


@test("app installed")
def t_installed():
    out = shell(f"pm list packages {PKG}", check=False)
    assert PKG in out, f"Package {PKG} not installed"


@test("app launches — home screen visible")
def t_launch():
    shell(f"am force-stop {PKG}", check=False)
    time.sleep(1)
    adb("logcat", "-c")  # clear buffer — all subsequent logcat_since calls see from here

    # Disable camera setup so tests go straight to the session screen
    shell(f"am start -n {ACTIVITY} --ez devDisableCameraSetup true")
    time.sleep(3)
    shell(f"am force-stop {PKG}", check=False)
    time.sleep(1)

    shell(f"am start -n {ACTIVITY}")
    time.sleep(4)
    screenshot("01_home")

    # Verify process is running
    pid = shell(f"pidof {PKG}", check=False)
    assert pid.strip(), "App process not running after launch"

    # Verify home screen element exists
    root = ui_dump()
    if root is not None:
        ids = [n.get("resource-id", "") for n in root.iter("node")]
        assert any("startClassButton" in i for i in ids), \
            "startClassButton not found — home screen did not render"
    else:
        log("  (uiautomator unavailable, using logcat check)")
        logs = logcat_since(["ActivityTaskManager"])
        assert "com.yogaflow" in logs, "App did not start per ActivityTaskManager"


@test("start beginner class — session starts immediately (camera setup disabled)")
def t_start_class():
    root = ui_dump()
    if root is not None:
        pos = find_bounds(root, "startClassButton")
        assert pos, "startClassButton not found"
        tap(*pos, "startClassButton")
    else:
        # Fallback: use previously observed coordinates
        w, h = screen_size()
        tap(int(w * 0.32), int(h * 0.88), "startClassButton (fallback)")

    time.sleep(4)
    screenshot("02_session_started")

    # Godot should have initialized by now
    logs = logcat_since(["Godot", "GodotFragment"])
    assert "OnResume" in logs or "native layer" in logs, \
        "Godot engine did not initialize"
    assert "FATAL" not in logs, "FATAL error in Godot logs"


@test("no crash — FATAL EXCEPTION absent")
def t_no_crash():
    logs = logcat_since(["AndroidRuntime", "FATAL"])
    assert "FATAL EXCEPTION" not in logs, f"App crashed: {logs[:300]}"


@test("Godot lifecycle — onResume forwarded")
def t_godot_lifecycle():
    logs = logcat_since(["Godot"])
    assert "OnResume:" in logs, \
        "Godot onResume not received — lifecycle forwarding broken"
    assert "native layer initialization completed: true" in logs, \
        "Godot native layer did not complete initialization"


@test("session start — tap Start button")
def t_session_start():
    w, h = screen_size()
    # startButton is the 1st of 6 equal-weight buttons at the bottom bar
    btn_w = w // 6
    btn_y = int(h * 0.96)
    tap(btn_w // 2, btn_y, "startButton")
    time.sleep(3)
    screenshot("03_session_running")

    logs = logcat_since(["Godot", "AndroidRuntime"])
    assert "FATAL EXCEPTION" not in logs, "Crash on session start"


@test("pause and resume — no crash")
def t_pause_resume():
    w, h = screen_size()
    btn_w = w // 6
    btn_y = int(h * 0.96)

    # Tap PAUSE (2nd button)
    tap(btn_w + btn_w // 2, btn_y, "pauseButton")
    time.sleep(2)
    screenshot("04_paused")

    # Tap START again to resume (1st button)
    tap(btn_w // 2, btn_y, "startButton (resume)")
    time.sleep(2)
    screenshot("05_resumed")

    logs = logcat_since(["AndroidRuntime"])
    assert "FATAL EXCEPTION" not in logs, "Crash during pause/resume"


@test("Godot bridge — connect attempted after session start")
def t_bridge_connect():
    logs = logcat_since(["GodotAvatarBridge", "OkHttp"])
    # Bridge connect is triggered in startSession(); a connection refused is fine —
    # it still proves GodotAvatarBridge.connect() ran. Match only specific markers.
    connected = any(kw in logs for kw in [
        "ws://127.0.0.1", "GodotAvatarBridge", "onOpen", "onFailure",
    ])
    assert connected, \
        "No GodotAvatarBridge WebSocket activity — connect() may not have been called"


@test("avatar position — LEFT→RIGHT→CENTER sweep verified via logcat")
def t_avatar_position():
    position_py = Path(__file__).parent / "test-avatar-position.py"

    # Re-launch with avatarSelfTest so the bridge connects and avatar is visible
    shell(f"am force-stop {PKG}", check=False)
    time.sleep(1)
    shell(f"am start -n {ACTIVITY} --ez devDisableCameraSetup true --ez avatarSelfTest true")
    log("  Waiting 8s for Godot bridge to connect...")
    time.sleep(8)

    adb("logcat", "-c")  # isolate: only see commands from this sweep

    log("  Sweeping LEFT (-1.5)...")
    shell(f"am start -n {ACTIVITY} --ef avatarTargetX -1.5 --ef avatarTargetY 0.0")
    time.sleep(2)

    log("  Sweeping RIGHT (+1.5)...")
    shell(f"am start -n {ACTIVITY} --ef avatarTargetX 1.5 --ef avatarTargetY 0.0")
    time.sleep(2)

    log("  Sweeping CENTER (0.0)...")
    shell(f"am start -n {ACTIVITY} --ef avatarTargetX 0.0 --ef avatarTargetY 0.0")
    time.sleep(2)

    screenshot("07_avatar_center")

    r = subprocess.run(
        [sys.executable, str(position_py), "--logcat"],
        capture_output=True, text=True,
    )
    if r.stdout:
        log(r.stdout.rstrip())
    if r.stderr:
        log(r.stderr.rstrip())
    assert r.returncode == 0, \
        "test-avatar-position.py --logcat failed: expected LEFT before RIGHT in logcat"


@test("restart — app survives restart")
def t_restart():
    w, h = screen_size()
    btn_w = w // 6
    btn_y = int(h * 0.96)

    # Tap RESTART (3rd button)
    tap(btn_w * 2 + btn_w // 2, btn_y, "restartButton")
    time.sleep(3)
    screenshot("06_restarted")

    pid = shell(f"pidof {PKG}", check=False)
    assert pid.strip(), "App process died after restart"

    logs = logcat_since(["AndroidRuntime"])
    assert "FATAL EXCEPTION" not in logs, "Crash on restart"


# ── main ─────────────────────────────────────────────────────────────────────

def build_and_install():
    log("Building and installing APK...")
    r = subprocess.run(["./gradlew", "installDebug"], capture_output=False)
    if r.returncode != 0:
        log("BUILD FAILED — aborting tests")
        sys.exit(1)


def print_summary():
    log(f"\n{'═'*50}")
    log("RESULTS")
    log(f"{'═'*50}")
    passed = sum(1 for _, s, _ in results if s == PASS)
    failed = sum(1 for _, s, _ in results if s == FAIL)
    for name, status, msg in results:
        icon = "✓" if status == PASS else "✗"
        log(f"  {icon} {name}" + (f"\n      {msg}" if msg else ""))
    log(f"\n  {passed} passed, {failed} failed")
    return failed == 0


def main():
    global SCREENSHOTS
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--screenshots", action="store_true")
    args = parser.parse_args()
    SCREENSHOTS = args.screenshots

    if not args.skip_install:
        build_and_install()

    # Run all tests in order
    t_device()
    t_installed()
    t_launch()
    t_start_class()
    t_no_crash()
    t_godot_lifecycle()
    t_session_start()
    t_pause_resume()
    t_bridge_connect()
    t_avatar_position()
    t_restart()

    ok = print_summary()
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
