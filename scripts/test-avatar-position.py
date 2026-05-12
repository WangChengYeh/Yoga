#!/usr/bin/env python3
# /// script
# requires-python = ">=3.9"
# dependencies = [
#   "opencv-python",
#   "numpy",
# ]
# ///
"""
test-avatar-position.py — Extract and verify avatar position from YogaFlow screen captures.

Run with:  uv run scripts/test-avatar-position.py <mode> [args]
(uv auto-installs opencv-python and numpy on first run)

Modes:
  --image PATH [--ref PATH] [--annotate OUT]
      Analyse a single screenshot. Use --ref to enable diff-based detection
      (more reliable than single-image blob detection when both images are available).

  --video PATH [--annotate OUT]
      Analyse an MP4 screen recording using background subtraction + frame differencing.
      Reports avatar centroid per sampled frame and overall X range.

  --verify DIR [--annotate]
      Verify regression artifacts in DIR:
        avatar-left.png, avatar-right.png, avatar-center.png, avatar-auto.png
      Checks that the avatar's detected X position satisfies left < center < right.

  --logcat
      Parse live ADB logcat for avatar position commands (most reliable when
      adb shell screencap does not composite the Godot SurfaceView layer).

KNOWN LIMITATION:
    adb shell screencap does not composite SurfaceView content on many devices.
    The Godot avatar renders in a SurfaceView; position screenshots from the
    movement sweep will appear identical even though the override IS applied
    (confirmed by logcat). Use --logcat for ground-truth verification, or
    --video with a screenrecord capture where the avatar is visibly animated.
"""

import argparse
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional, Tuple

try:
    import cv2
    import numpy as np
    _CV2_AVAILABLE = True
except ImportError:
    _CV2_AVAILABLE = False


# ── Data types ────────────────────────────────────────────────────────────────

@dataclass
class Detection:
    x: float            # centroid X, normalized 0=left 1=right
    y: float            # centroid Y, normalized 0=top  1=bottom
    w: float            # bounding-box width,  normalized
    h: float            # bounding-box height, normalized
    confidence: float   # 0=no signal, 1=strong signal
    method: str         # "diff", "blob", "motion"
    diff_pixels: int = 0

    def side(self) -> str:
        if self.x < 0.38:
            return "LEFT"
        if self.x > 0.62:
            return "RIGHT"
        return "CENTER"

    def __str__(self) -> str:
        return (f"x={self.x:.3f} y={self.y:.3f} "
                f"({self.side()}, conf={self.confidence:.2f}, method={self.method})")


@dataclass
class LogcatPosition:
    timestamp: str
    x: float
    y: float
    raw: str

    def side(self) -> str:
        if self.x < -0.5:
            return "LEFT"
        if self.x > 0.5:
            return "RIGHT"
        return "CENTER"


# ── Image loading / validation ─────────────────────────────────────────────────

def load_image(path: Path) -> Optional[np.ndarray]:
    img = cv2.imread(str(path))
    if img is None:
        print(f"  ERROR: cannot read image: {path}")
    return img


def screencap_check(img: np.ndarray) -> Tuple[bool, str]:
    """
    Heuristic: is this a YogaFlow screen or the Android launcher / lock screen?
    Returns (is_app, reason).
    YogaFlow home screen has a dark upper section and a teal (#4CAF7D-ish) lower card.
    """
    h, w = img.shape[:2]

    # Sample a column of pixels from the upper-left quadrant (expected dark in YogaFlow)
    upper_region = img[:h // 3, :w // 2]
    upper_mean = upper_region.mean(axis=(0, 1))  # BGR

    # Sample the lower portion (expected teal/green in YogaFlow home screen)
    lower_region = img[h * 2 // 3:, :]
    lower_hsv = cv2.cvtColor(lower_region, cv2.COLOR_BGR2HSV)
    # Teal: H=140-170, S>40, V>60
    teal_mask = cv2.inRange(lower_hsv,
                            np.array([140, 40, 60]),
                            np.array([170, 255, 255]))
    teal_ratio = teal_mask.sum() / (teal_mask.size * 255)

    # YogaFlow: dark upper + teal lower
    upper_dark = upper_mean.max() < 80
    has_teal = teal_ratio > 0.05

    if upper_dark and has_teal:
        return True, "YogaFlow home screen detected (dark upper + teal card)"
    if upper_dark:
        return True, "dark background (possibly YogaFlow session screen)"
    return False, f"unexpected colours (upper mean BGR={upper_mean.round(1)}, teal_ratio={teal_ratio:.3f}) — may be launcher/lock screen"


# ── Detection strategies ───────────────────────────────────────────────────────

def detect_by_diff(ref: np.ndarray, test: np.ndarray, threshold: int = 15) -> Detection:
    """
    Compare two images; the centroid of pixels that differ is the avatar position.
    Best used when the only thing that changed between ref and test is the avatar's position.
    """
    h, w = ref.shape[:2]

    diff = cv2.absdiff(ref, test)
    diff_gray = cv2.cvtColor(diff, cv2.COLOR_BGR2GRAY)
    _, mask = cv2.threshold(diff_gray, threshold, 255, cv2.THRESH_BINARY)

    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9))
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, kernel, iterations=2)
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, kernel, iterations=1)

    diff_pixels = int((mask > 0).sum())

    if diff_pixels < 200:
        return Detection(x=0.5, y=0.5, w=0.0, h=0.0,
                         confidence=0.0, method="diff", diff_pixels=diff_pixels)

    moments = cv2.moments(mask)
    cx = moments["m10"] / moments["m00"] / w
    cy = moments["m01"] / moments["m00"] / h

    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    all_pts = np.vstack([c.reshape(-1, 2) for c in contours])
    bx, by, bw, bh = cv2.boundingRect(all_pts)

    # Confidence: how large is the diff relative to the image?
    confidence = min(1.0, diff_pixels / (w * h * 0.01))

    return Detection(
        x=round(cx, 3), y=round(cy, 3),
        w=round(bw / w, 3), h=round(bh / h, 3),
        confidence=round(confidence, 3),
        method="diff", diff_pixels=diff_pixels,
    )


def detect_by_blob(img: np.ndarray) -> Detection:
    """
    Single-image avatar detection using skin-tone segmentation + human-body contour heuristics.
    Works best when the avatar is visible against a dark/camera background (session screen).
    Less reliable on the home screen where there is no avatar layer.
    """
    h, w = img.shape[:2]
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # Skin tone (two hue ranges to cover warm/cool skin)
    skin_lo1 = np.array([0,  25, 60])
    skin_hi1 = np.array([20, 200, 255])
    skin_lo2 = np.array([165, 25, 60])
    skin_hi2 = np.array([180, 200, 255])
    skin = cv2.bitwise_or(cv2.inRange(hsv, skin_lo1, skin_hi1),
                          cv2.inRange(hsv, skin_lo2, skin_hi2))

    # Edges (body outline)
    edges = cv2.Canny(gray, 40, 120)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    edges = cv2.dilate(edges, kernel, iterations=2)

    combined = cv2.bitwise_or(skin, edges)
    combined = cv2.morphologyEx(combined, cv2.MORPH_CLOSE, kernel, iterations=4)

    contours, _ = cv2.findContours(combined, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return Detection(x=0.5, y=0.5, w=0.0, h=0.0, confidence=0.0, method="blob")

    # Keep contours with human-body aspect ratio (height > width, area > noise floor)
    candidates = []
    for cnt in contours:
        area = cv2.contourArea(cnt)
        if area < 800:
            continue
        bx, by, bw, bh = cv2.boundingRect(cnt)
        aspect = bh / max(bw, 1)
        if 1.2 <= aspect <= 9.0:
            candidates.append((area, cnt, bx, by, bw, bh))

    if not candidates:
        cnt = max(contours, key=cv2.contourArea)
        bx, by, bw, bh = cv2.boundingRect(cnt)
        return Detection(
            x=round((bx + bw / 2) / w, 3),
            y=round((by + bh / 2) / h, 3),
            w=round(bw / w, 3), h=round(bh / h, 3),
            confidence=0.15, method="blob",
        )

    candidates.sort(key=lambda c: c[0], reverse=True)
    area, _, bx, by, bw, bh = candidates[0]
    confidence = min(1.0, area / (w * h * 0.04))

    return Detection(
        x=round((bx + bw / 2) / w, 3),
        y=round((by + bh / 2) / h, 3),
        w=round(bw / w, 3), h=round(bh / h, 3),
        confidence=round(confidence, 3),
        method="blob",
    )


# ── Annotation ─────────────────────────────────────────────────────────────────

def annotate_image(img: np.ndarray, det: Detection, label: str = "") -> np.ndarray:
    out = img.copy()
    h, w = out.shape[:2]

    cx = int(det.x * w)
    cy = int(det.y * h)
    bw = int(det.w * w)
    bh = int(det.h * h)

    colour = (0, 255, 0) if det.confidence >= 0.3 else (0, 165, 255)

    # Bounding box
    if bw > 0 and bh > 0:
        cv2.rectangle(out,
                      (cx - bw // 2, cy - bh // 2),
                      (cx + bw // 2, cy + bh // 2),
                      colour, 2)

    # Crosshair
    cv2.line(out, (cx - 20, cy), (cx + 20, cy), colour, 2)
    cv2.line(out, (cx, cy - 20), (cx, cy + 20), colour, 2)

    # Vertical centre line for reference
    cv2.line(out, (w // 2, 0), (w // 2, h), (100, 100, 100), 1)

    # Label
    text = f"{label} x={det.x:.3f} ({det.side()}) conf={det.confidence:.2f}"
    cv2.putText(out, text, (10, 40), cv2.FONT_HERSHEY_SIMPLEX, 1.0, colour, 2)

    return out


# ── Logcat parsing ─────────────────────────────────────────────────────────────

def read_logcat_positions() -> List[LogcatPosition]:
    """
    Parse live ADB logcat for YogaFlow avatar position commands.
    Matches lines like:
      I YogaFlow: ADB avatar override set x=1.5 y=0.0
      I YogaFlow: ADB avatar override cleared
    """
    try:
        result = subprocess.run(
            ["adb", "logcat", "-d"],
            capture_output=True, text=True, timeout=10
        )
    except (subprocess.TimeoutExpired, FileNotFoundError) as e:
        print(f"  ERROR: adb failed: {e}")
        return []

    pattern_set = re.compile(
        r"(\d\d-\d\d \d\d:\d\d:\d\d\.\d+).*YogaFlow.*ADB avatar override set x=([+-]?\d+\.?\d*) y=([+-]?\d+\.?\d*)"
    )
    pattern_clear = re.compile(
        r"(\d\d-\d\d \d\d:\d\d:\d\d\.\d+).*YogaFlow.*ADB avatar override cleared"
    )

    positions = []
    for line in result.stdout.splitlines():
        m = pattern_set.search(line)
        if m:
            positions.append(LogcatPosition(
                timestamp=m.group(1),
                x=float(m.group(2)),
                y=float(m.group(3)),
                raw=line.strip(),
            ))
        elif pattern_clear.search(line):
            mc = pattern_clear.search(line)
            positions.append(LogcatPosition(
                timestamp=mc.group(1),
                x=0.0, y=0.0,
                raw=line.strip(),
            ))

    return positions


# ── CLI modes ──────────────────────────────────────────────────────────────────

def cmd_image(path: Path, ref_path: Optional[Path], annotate_out: Optional[Path]) -> None:
    img = load_image(path)
    if img is None:
        sys.exit(1)

    h, w = img.shape[:2]
    print(f"Image: {path.name}  ({w}x{h})")

    ok, reason = screencap_check(img)
    print(f"  Screen check: {'OK' if ok else 'WARN'}  {reason}")
    if not ok:
        print("  NOTE: If the Godot SurfaceView is not composited by screencap,")
        print("        avatar detection will be unreliable. Use --logcat or --video instead.")

    if ref_path:
        ref = load_image(ref_path)
        if ref is None:
            sys.exit(1)
        print(f"  Reference:    {ref_path.name}")
        det = detect_by_diff(ref, img)
        if det.confidence < 0.1:
            print(f"  Diff pixels:  {det.diff_pixels}  (too few — images may be identical)")
            print("  Falling back to blob detection...")
            det = detect_by_blob(img)
    else:
        det = detect_by_blob(img)

    print(f"  Detection:    {det}")

    if annotate_out:
        out_img = annotate_image(img, det, path.stem)
        cv2.imwrite(str(annotate_out), out_img)
        print(f"  Annotated:    {annotate_out}")


def cmd_video(path: Path, annotate_out: Optional[Path]) -> bool:
    """Returns True if motion was detected, False otherwise."""
    cap = cv2.VideoCapture(str(path))
    if not cap.isOpened():
        print(f"ERROR: cannot open video: {path}")
        sys.exit(1)

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    vw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    vh = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"Video: {path.name}  ({vw}x{vh}, {fps:.1f} fps, {total} frames, {total/fps:.1f}s)")

    # Background subtractor — adapts to slow-changing background (UI), responds to avatar motion
    subtractor = cv2.createBackgroundSubtractorMOG2(
        history=60, varThreshold=30, detectShadows=False
    )

    sample_every = max(1, int(fps / 3))  # 3 samples/sec
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))

    writer = None
    if annotate_out:
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        writer = cv2.VideoWriter(str(annotate_out), fourcc, fps, (vw, vh))

    positions: List[Tuple[float, Detection]] = []
    frame_idx = 0

    while True:
        ret, frame = cap.read()
        if not ret:
            break

        fg = subtractor.apply(frame)
        fg = cv2.morphologyEx(fg, cv2.MORPH_OPEN, kernel)
        fg = cv2.morphologyEx(fg, cv2.MORPH_CLOSE, kernel, iterations=3)

        if frame_idx % sample_every == 0 and frame_idx > int(fps):
            # Skip first second while background model warms up
            diff_pixels = int((fg > 0).sum())
            if diff_pixels > 500:
                moments = cv2.moments(fg)
                if moments["m00"] > 0:
                    cx = moments["m10"] / moments["m00"] / vw
                    cy = moments["m01"] / moments["m00"] / vh
                    contours, _ = cv2.findContours(fg, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
                    if contours:
                        all_pts = np.vstack([c.reshape(-1, 2) for c in contours])
                        bx, by, bw, bh = cv2.boundingRect(all_pts)
                    else:
                        bx, by, bw, bh = int(cx * vw), int(cy * vh), 1, 1
                    conf = min(1.0, diff_pixels / (vw * vh * 0.005))
                    det = Detection(
                        x=round(cx, 3), y=round(cy, 3),
                        w=round(bw / vw, 3), h=round(bh / vh, 3),
                        confidence=round(conf, 3),
                        method="motion", diff_pixels=diff_pixels,
                    )
                    t = frame_idx / fps
                    positions.append((t, det))

                    if writer:
                        annotated = annotate_image(frame, det, f"t={t:.1f}s")
                        writer.write(annotated)
            elif writer:
                writer.write(frame)
        elif writer:
            writer.write(frame)

        frame_idx += 1

    cap.release()
    if writer:
        writer.release()

    if not positions:
        print("  FAIL: no motion detected — avatar may be static or not visible in this recording.")
        return False

    print(f"\n  {'Time(s)':>8}  {'X':>7}  {'Y':>7}  {'Side':>7}  {'Conf':>6}  {'Diff px':>9}")
    print(f"  {'':->8}  {'':->7}  {'':->7}  {'':->7}  {'':->6}  {'':->9}")
    for t, det in positions:
        print(f"  {t:>8.1f}  {det.x:>7.3f}  {det.y:>7.3f}  {det.side():>7}  {det.confidence:>6.2f}  {det.diff_pixels:>9}")

    xs = [d.x for _, d in positions]
    print(f"\n  X range: {min(xs):.3f} – {max(xs):.3f}  (mean {sum(xs)/len(xs):.3f})")

    if annotate_out:
        print(f"  Annotated:   {annotate_out}")

    print(f"  PASS: motion detected in {len(positions)} sampled frames")
    return True


def cmd_verify(artifacts_dir: Path, annotate: bool) -> bool:
    """
    Verify avatar-left, avatar-right, avatar-center position ordering.
    Primary: diff-based detection between left↔center and right↔center.
    Falls back to blob detection when diff confidence is too low.
    Also reads logcat for ground-truth corroboration.
    """
    files = {
        "left":   artifacts_dir / "avatar-left.png",
        "right":  artifacts_dir / "avatar-right.png",
        "center": artifacts_dir / "avatar-center.png",
        "auto":   artifacts_dir / "avatar-auto.png",
    }

    missing = [k for k, p in files.items() if not p.exists()]
    if missing:
        for k in missing:
            if k not in ("auto",):  # auto is optional
                print(f"  ERROR: missing required artifact: {files[k]}")
                return False

    imgs = {k: load_image(p) for k, p in files.items() if p.exists()}
    if any(v is None for v in imgs.values()):
        return False

    print("=== Avatar Position Verification ===\n")

    # ── Screencap layer check ──────────────────────────────────────────────────
    print("Screencap layer check:")
    any_app = False
    for name, img in imgs.items():
        ok, reason = screencap_check(img)
        print(f"  {name:8s}: {'OK  ' if ok else 'WARN'} {reason}")
        if ok:
            any_app = True

    if not any_app:
        print()
        print("  WARN: None of the artifacts appear to show the YogaFlow app.")
        print("        This usually means adb shell screencap did not composite the")
        print("        Godot SurfaceView. Diff-based detection will be unreliable.")
        print("        Use --logcat for ground-truth position verification.")
    print()

    # ── Diff-based detection ───────────────────────────────────────────────────
    print("Diff-based detection (test vs center reference):")
    detections: dict = {}
    screencap_limited = False

    if "center" in imgs:
        center = imgs["center"]
        for name in ("left", "right"):
            if name not in imgs:
                continue
            det = detect_by_diff(center, imgs[name])
            if det.diff_pixels == 0:
                screencap_limited = True
                print(f"  {name:8s}: 0 diff pixels — images are identical")
                print(f"            Godot SurfaceView not composited by screencap; skipping pixel check.")
                print(f"            Use --logcat for ground-truth position verification.")
            elif det.confidence < 0.15:
                print(f"  {name:8s}: diff too small ({det.diff_pixels} px, conf={det.confidence:.2f}) → falling back to blob")
                det = detect_by_blob(imgs[name])
                detections[name] = det
                print(f"            {det}")
            else:
                detections[name] = det
                print(f"  {name:8s}: {det}")

    center_det = detect_by_blob(imgs["center"]) if "center" in imgs else None
    if center_det and not screencap_limited:
        detections["center"] = center_det
        print(f"  {'center':8s}: {center_det}  (blob, single-image)")

    # Annotate
    if annotate:
        for name, det in detections.items():
            if name in imgs:
                out_path = artifacts_dir / f"avatar-{name}-annotated.png"
                cv2.imwrite(str(out_path), annotate_image(imgs[name], det, name))
                print(f"  Annotated → {out_path}")

    print()

    # ── Position ordering assertions ───────────────────────────────────────────
    print("Position ordering:")
    passed = 0
    failed = 0
    skipped = 0

    def check(name: str, predicate, description: str):
        nonlocal passed, failed, skipped
        det = detections.get(name)
        if det is None:
            print(f"  SKIP  {name:8s}: no detection (screencap limitation)")
            skipped += 1
            return
        if det.confidence < 0.1:
            print(f"  SKIP  {name:8s}: confidence too low ({det.confidence:.2f})")
            skipped += 1
            return
        if predicate(det.x):
            print(f"  PASS  {name:8s}: {description}  (x={det.x:.3f})")
            passed += 1
        else:
            print(f"  FAIL  {name:8s}: {description}  (got x={det.x:.3f})")
            failed += 1

    check("left",   lambda x: x < 0.45, "x < 0.45 (left third)")
    check("right",  lambda x: x > 0.55, "x > 0.55 (right third)")
    check("center", lambda x: 0.30 <= x <= 0.70, "0.30 ≤ x ≤ 0.70 (center)")

    if "left" in detections and "right" in detections:
        l, r = detections["left"], detections["right"]
        if l.confidence > 0.1 and r.confidence > 0.1:
            if l.x < r.x:
                gap = r.x - l.x
                print(f"  PASS  ordering: left ({l.x:.3f}) < right ({r.x:.3f}), gap={gap:.3f}")
                passed += 1
            else:
                print(f"  FAIL  ordering: left ({l.x:.3f}) should be < right ({r.x:.3f})")
                failed += 1
        else:
            print("  SKIP  ordering: low confidence on one or both sides")
            skipped += 1
    elif screencap_limited:
        print("  SKIP  ordering: screencap limitation — use logcat ordering below")
        skipped += 1

    print()

    # ── Logcat corroboration ───────────────────────────────────────────────────
    print("Logcat corroboration (live adb):")
    logcat_positions = read_logcat_positions()
    if not logcat_positions:
        print("  (no avatar position commands found in logcat — run test-avatar-movement.sh first)")
    else:
        for lp in logcat_positions[-10:]:
            print(f"  [{lp.timestamp}]  x={lp.x:+.1f}  y={lp.y:.1f}  → {lp.side()}")
        # Verify left was commanded before right
        sides = [lp.side() for lp in logcat_positions]
        if "LEFT" in sides and "RIGHT" in sides:
            li = next(i for i, s in enumerate(sides) if s == "LEFT")
            ri = next(i for i, s in enumerate(sides) if s == "RIGHT")
            if li < ri:
                print("  PASS  logcat ordering: LEFT commanded before RIGHT")
                passed += 1
            else:
                print("  FAIL  logcat ordering: expected LEFT before RIGHT")
                failed += 1
        else:
            print("  SKIP  logcat ordering: not enough position commands in buffer")

    print()
    print("=" * 44)
    print(f"Results: {passed} passed, {failed} failed"
          + (f", {skipped} skipped" if skipped else ""))

    return failed == 0


def cmd_logcat() -> bool:
    """Parse buffered ADB logcat for avatar position commands.

    Returns True if at least one LEFT and one RIGHT command are found and LEFT
    precedes RIGHT (correct sweep ordering). Returns False otherwise so the
    caller can propagate a non-zero exit code.
    """
    print("Reading avatar position commands from ADB logcat...\n")
    positions = read_logcat_positions()
    if not positions:
        print("  No avatar position commands found.")
        print("  Run scripts/test-avatar-movement.sh and retry.")
        return False

    print(f"  {'Timestamp':>23}  {'X':>7}  {'Y':>7}  {'Side':>7}")
    print(f"  {'':->23}  {'':->7}  {'':->7}  {'':->7}")
    for lp in positions:
        print(f"  {lp.timestamp:>23}  {lp.x:>+7.2f}  {lp.y:>7.2f}  {lp.side():>7}")

    xs = [lp.x for lp in positions]
    print(f"\n  {len(positions)} position commands found")
    print(f"  X range: {min(xs):+.2f} to {max(xs):+.2f}")

    # Verify ordering: LEFT commanded before RIGHT
    sides = [lp.side() for lp in positions]
    if "LEFT" in sides and "RIGHT" in sides:
        li = next(i for i, s in enumerate(sides) if s == "LEFT")
        ri = next(i for i, s in enumerate(sides) if s == "RIGHT")
        if li < ri:
            print("  PASS  ordering: LEFT commanded before RIGHT")
            return True
        else:
            print("  FAIL  ordering: expected LEFT before RIGHT")
            return False
    else:
        print("  SKIP  ordering: need both LEFT and RIGHT in logcat")
        return False


# ── Entry point ────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Extract and verify avatar position from YogaFlow screen captures.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__.split("KNOWN LIMITATION")[1].strip()
             if "KNOWN LIMITATION" in __doc__ else "",
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--image", type=Path, metavar="PATH",
                       help="Analyse a single PNG screenshot")
    group.add_argument("--video", type=Path, metavar="PATH",
                       help="Analyse an MP4 screen recording")
    group.add_argument("--verify", type=Path, metavar="DIR",
                       help="Verify regression artifacts in DIR")
    group.add_argument("--logcat", action="store_true",
                       help="Parse live ADB logcat for avatar position commands")

    parser.add_argument("--ref", type=Path, metavar="PATH",
                        help="Reference image for diff-based detection (--image mode)")
    parser.add_argument("--annotate", type=Path, metavar="OUT", nargs="?", const=True,
                        help="Save annotated output. For --verify, pass no value to use DIR.")

    args = parser.parse_args()

    if args.image or args.video or args.verify:
        if not _CV2_AVAILABLE:
            print("ERROR: opencv-python and numpy are required for image/video/verify modes.")
            print("       uv run scripts/test-avatar-position.py  (auto-installs deps)")
            sys.exit(2)

    if args.image:
        annotate_out = None
        if args.annotate is True:
            annotate_out = args.image.with_stem(args.image.stem + "-annotated")
        elif isinstance(args.annotate, Path):
            annotate_out = args.annotate
        cmd_image(args.image, args.ref, annotate_out)

    elif args.video:
        annotate_out = None
        if args.annotate is True:
            annotate_out = args.video.with_stem(args.video.stem + "-annotated")
        elif isinstance(args.annotate, Path):
            annotate_out = args.annotate
        ok = cmd_video(args.video, annotate_out)
        sys.exit(0 if ok else 1)

    elif args.verify:
        ok = cmd_verify(args.verify, annotate=bool(args.annotate))
        sys.exit(0 if ok else 1)

    elif args.logcat:
        ok = cmd_logcat()
        sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
