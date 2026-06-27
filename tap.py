#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""抢票连点器 - 通过 ADB 直接点击。
推荐: python tap.py --nowait --turbo
详见 PROJECT.md
"""

import argparse
import os
import re
import subprocess
import sys
import threading
import time

def _find_adb() -> str:
    candidates = [
        os.environ.get("ADB"),
        os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"),
        os.path.expandvars(r"%ANDROID_HOME%\platform-tools\adb.exe"),
        os.path.expandvars(r"%ANDROID_SDK_ROOT%\platform-tools\adb.exe"),
        "adb",
    ]
    for c in candidates:
        if c and os.path.isfile(c):
            return c
    return "adb"

ADB = _find_adb()
PREFS_PATH = "/data/data/com.ticketsnap/shared_prefs/ticket_snap_prefs.xml"
BOX1_SIZE_DP = 72
BOX2_SIZE_DP = 144
UI_DUMP_PATH = "/sdcard/ticketsnap_ui.xml"
RETRY_CHECK_INTERVAL = 8
UI_POLL_SEC = 1.5  # 框2 智能检测间隔（uiautomator dump 较慢）

# 框2：屏幕出现「再XX」（再+任意字，如再试/再抢）即激活
RETRY_RE = re.compile(r"再.")


def adb(*args: str) -> str:
    try:
        result = subprocess.run(
            [ADB, *args], capture_output=True, text=True, timeout=15,
            encoding="utf-8", errors="replace"
        )
        return (result.stdout or "").strip()
    except Exception:
        return ""


def adb_shell(cmd: str) -> str:
    return adb("shell", cmd)


def read_prefs() -> dict:
    raw = adb_shell(f"run-as com.ticketsnap cat {PREFS_PATH}")
    if not raw:
        return {}
    data = {}
    for m in re.finditer(r'<(?:float|int|boolean) name="(\w+)" value="([^"]*)"', raw):
        name, val = m.groups()
        data[name] = float(val) if "." in val or name.endswith(("_x", "_y")) else val
    return data


class AdbTapSession:
    """Reuse one adb shell process to avoid per-tap startup overhead on Windows."""

    def __init__(self) -> None:
        self._proc = subprocess.Popen(
            [ADB, "shell"],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )

    def tap(self, x: int, y: int) -> None:
        if self._proc.stdin is None:
            return
        self._proc.stdin.write(f"input tap {x} {y}\n".encode())
        self._proc.stdin.flush()

    def close(self) -> None:
        if self._proc.stdin:
            try:
                self._proc.stdin.close()
            except OSError:
                pass
        try:
            self._proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            self._proc.kill()


def dump_ui() -> str:
    return adb_shell(f"uiautomator dump {UI_DUMP_PATH} 2>&1; cat {UI_DUMP_PATH} 2>&1")


def has_retry(ui_text: str) -> bool:
    return bool(RETRY_RE.search(ui_text))


def prefs_stop_monitor(stop_event: threading.Event, session_active: bool) -> None:
    """Poll is_running; stop only after session was active then turned off."""
    active = session_active
    while not stop_event.is_set():
        time.sleep(1.0)
        p = read_prefs()
        running = p.get("is_running", "false") == "true"
        if running:
            active = True
        elif active:
            stop_event.set()
            return


def run_turbo_tap(x: int, y: int, stop_events: list[threading.Event]) -> None:
    """Phone-side tight loop; stop when any stop_event is set."""
    proc = subprocess.Popen(
        [ADB, "shell", f"while true; do input tap {x} {y}; done"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        while proc.poll() is None:
            if any(e.is_set() for e in stop_events):
                break
            time.sleep(0.05)
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=2)
        except subprocess.TimeoutExpired:
            proc.kill()


def run_dual_turbo(
    x1: int, y1: int,
    x2: int | None, y2: int | None,
    b2: bool, smart: bool,
    stop_event: threading.Event,
) -> None:
    """Box1 always max speed; box2 turbo when retry UI detected (or always if not smart)."""
    box2_stop = threading.Event()
    box2_thread: threading.Thread | None = None
    box2_active = False

    def start_box2() -> threading.Thread:
        box2_stop.clear()
        t = threading.Thread(
            target=run_turbo_tap,
            args=(x2, y2, [stop_event, box2_stop]),
            daemon=True,
        )
        t.start()
        return t

    def stop_box2() -> None:
        nonlocal box2_thread, box2_active
        box2_stop.set()
        if box2_thread and box2_thread.is_alive():
            box2_thread.join(timeout=3)
        box2_thread = None
        box2_active = False

    box1_thread = threading.Thread(
        target=run_turbo_tap, args=(x1, y1, [stop_event]), daemon=True
    )
    box1_thread.start()

    if b2 and x2 and y2 and not smart:
        box2_thread = start_box2()
        box2_active = True
        print("[box2] turbo ON (always)")

    try:
        while not stop_event.is_set():
            if not (b2 and x2 and y2 and smart):
                time.sleep(0.2)
                continue
            ui = dump_ui()
            retry = has_retry(ui)
            state = "YES" if retry else "no"
            print(f"[box2] retry={state}" + (" turbo ON" if box2_active else ""))
            if retry and not box2_active:
                box2_thread = start_box2()
                box2_active = True
                print("[box2] turbo ON")
            elif not retry and box2_active:
                stop_box2()
                print("[box2] turbo OFF")
            time.sleep(UI_POLL_SEC)
    except KeyboardInterrupt:
        pass
    finally:
        box2_stop.set()
        if box2_thread and box2_thread.is_alive():
            box2_thread.join(timeout=3)
        if box1_thread.is_alive():
            box1_thread.join(timeout=3)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--x1", type=int); parser.add_argument("--y1", type=int)
    parser.add_argument("--x2", type=int); parser.add_argument("--y2", type=int)
    parser.add_argument("--interval", type=int, default=None,
                        help="点击间隔 ms，默认读取 App 设置（最低建议 20）")
    parser.add_argument("--no-box1", action="store_true")
    parser.add_argument("--no-box2", action="store_true")
    parser.add_argument("--force-box2", action="store_true", help="框2始终点击")
    parser.add_argument("--turbo", action="store_true",
                        help="框1极限连点；框2检测到重试按钮时同步极限连点")
    parser.add_argument("--nowait", action="store_true")
    parser.add_argument("-n", "--count", type=int, default=0)
    args = parser.parse_args()

    density = float(re.search(r"(\d+)", adb_shell("wm density")).group(1)) / 160.0
    half1 = int(BOX1_SIZE_DP / 2 * density)
    half2 = int(BOX2_SIZE_DP / 2 * density)

    prefs = read_prefs()
    if args.turbo:
        args.interval = 0
    elif args.interval is None:
        args.interval = int(prefs.get("click_interval", 50))
        args.interval = max(10, args.interval)
    interval = args.interval / 1000.0 if args.interval else 0

    x1, y1 = args.x1, args.y1
    x2, y2 = args.x2, args.y2
    if x1 is None and "box1_x" in prefs:
        x1, y1 = int(prefs["box1_x"] + half1), int(prefs["box1_y"] + half1)
    if x2 is None and "box2_x" in prefs:
        x2, y2 = int(prefs["box2_x"] + half2), int(prefs["box2_y"] + half2)
    if (x1 is None or y1 is None) and (x2 is None or y2 is None):
        print("Error: no click position set"); sys.exit(1)

    b1 = not args.no_box1 and prefs.get("box1_enabled", "true") == "true"
    b2 = not args.no_box2 and prefs.get("box2_enabled", "true") == "true"
    smart = not args.force_box2

    if args.turbo:
        print(f"density:{density:.1f} half1:{half1}px half2:{half2}px mode:TURBO")
    else:
        print(f"density:{density:.1f} half1:{half1}px half2:{half2}px interval:{args.interval}ms")
    if b1 and x1:
        print(f"[box1] always TURBO ({x1},{y1})" if args.turbo else f"[box1] always  ({x1},{y1})")
    if b2 and x2:
        if args.turbo:
            mode = "smart turbo" if smart else "always turbo"
            print(f"[box2] {mode} ({x2},{y2})")
        else:
            print(f"[box2] {'smart' if smart else 'always'} ({x2},{y2})")
    if not args.nowait:
        try:
            input("Enter to start, Ctrl+C to stop...")
        except EOFError:
            pass
    print("running... 手机点「停」或按音量键停止，Ctrl+C 也可停止\n")

    already_running = read_prefs().get("is_running", "false") == "true"
    if not already_running:
        print("提示: 请先在手机上点绿色「启」，再切到抢票页面")
    stop_event = threading.Event()
    threading.Thread(
        target=prefs_stop_monitor, args=(stop_event, already_running), daemon=True
    ).start()

    if args.turbo and b1 and x1:
        t0 = time.perf_counter()
        try:
            run_dual_turbo(x1, y1, x2, y2, b2, smart, stop_event)
        except KeyboardInterrupt:
            pass
        if stop_event.is_set():
            print("\n>> 手机触发停止")
        print(f"\nstopped. turbo ran {time.perf_counter() - t0:.1f}s")
        return

    count = 0; last_check = 0; retry_on = False
    tap_session = AdbTapSession()
    try:
        while args.count == 0 or count < args.count:
            if stop_event.is_set():
                print(f"\n>> 手机触发停止 ({count} clicks)")
                break
            t0 = time.perf_counter()
            if b1 and x1:
                tap_session.tap(x1, y1)
            if b2 and x2:
                if smart:
                    if count > 0 and count - last_check >= RETRY_CHECK_INTERVAL:
                        ui = dump_ui()
                        retry_on = has_retry(ui)
                        last_check = count
                        print(f"[{count}] retry={'YES' if retry_on else 'no'}")
                    if retry_on:
                        tap_session.tap(x2, y2)
                else:
                    tap_session.tap(x2, y2)
            count += 1
            delay = interval - (time.perf_counter() - t0)
            if delay > 0:
                time.sleep(delay)
    except KeyboardInterrupt:
        pass
    finally:
        tap_session.close()
    print(f"\nstopped. {count} clicks")


if __name__ == "__main__":
    main()
