#!/usr/bin/env python3
"""Generate the app's original, image-free soft-tile loader (144x72, 60fps, 1.5s).

Only position, scale and opacity change. Periodic cubic Hermite timing keeps
velocity continuous at keyframes and across the loop; no bounce, masks or blur.
"""
import json
import math
from pathlib import Path

DURATION = 90
TIMES = list(range(0, DURATION + 1, 15))


def wave(t, phase):
    angle = 2 * math.pi * (t - phase) / DURATION
    return (1 + math.cos(angle)) / 2, -math.pi / DURATION * math.sin(angle)


def animated(phase, value):
    frames = []
    for index, t in enumerate(TIMES):
        w, slope = wave(t, phase)
        frame = {"t": t, "s": value(w)}
        if index < len(TIMES) - 1:
            end = TIMES[index + 1]
            next_w, next_slope = wave(end, phase)
            delta = next_w - w
            frame.update({
                "e": value(next_w),
                "o": {"x": [1 / 3], "y": [slope * (end - t) / (3 * delta)]},
                "i": {"x": [2 / 3], "y": [1 - next_slope * (end - t) / (3 * delta)]},
            })
        frames.append(frame)
    return {"a": 1, "k": frames}


def fixed(value):
    return {"a": 0, "k": value}


layers = []
for index, x in enumerate((36, 72, 108)):
    phase = 15 + index * 30
    layers.append({
        "ddd": 0, "ind": index + 1, "ty": 4, "nm": f"Soft blue tile {index + 1}",
        "sr": 1, "ip": 0, "op": DURATION, "st": 0, "bm": 0,
        "ks": {
            "a": fixed([0, 0, 0]), "r": fixed(0),
            "p": animated(phase, lambda w: [x, 44 - 14 * w, 0]),
            "s": animated(phase, lambda w: [86 + 14 * w, 86 + 14 * w, 100]),
            "o": animated(phase, lambda w: [55 + 45 * w]),
        },
        "shapes": [
            {"ty": "rc", "d": 1, "nm": "Rounded tile", "p": fixed([0, 0]), "s": fixed([24, 24]), "r": fixed(7)},
            {"ty": "fl", "nm": "App blue", "c": fixed([13 / 255, 96 / 255, 241 / 255, 1]), "o": fixed(100), "r": 1, "bm": 0},
        ],
    })

animation = {
    "v": "5.7.4", "fr": 60, "ip": 0, "op": DURATION, "w": 144, "h": 72,
    "nm": "AI Clean - soft tiles loading", "ddd": 0, "assets": [], "layers": layers,
    "markers": [{"tm": 0, "cm": "reduced motion", "dr": 1}],
}
# Round exported values to keep the packaged asset small without affecting visible motion.
animation = json.loads(json.dumps(animation), parse_float=lambda number: round(float(number), 5))
output = Path(__file__).resolve().parents[1] / "app/src/main/res/raw/ad_loading_tiles.json"
output.write_text(json.dumps(animation, separators=(",", ":")) + "\n")
print(f"{output.name}: {output.stat().st_size} bytes")
