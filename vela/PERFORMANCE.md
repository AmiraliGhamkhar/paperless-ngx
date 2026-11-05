# Performance

## Honest statement

**No performance number in this document was measured**, because no Android device, emulator or
build was available. Everything below is design intent plus the exact measurement procedure needed
to replace claims with data. Do not quote targets from here as results.

## Performance targets (objectives, not results)

| Scenario | Target |
|---|---|
| Continuous stylus strokes, 2048² canvas, 3 layers | 60 fps sustained, no dropped samples |
| Same on a 120 Hz tablet | Input sampled at display rate; frame pacing not gated to 60 |
| Input-to-pixel latency | As low as the platform allows with buffered rendering (no prediction) |
| Large canvas (3508 × 2480), 8 layers | Usable at ≥ 30 fps; interaction stays responsive |
| Autosave during a long session | No visible hitch (runs off the main thread, skipped mid-stroke) |
| Undo/redo of a stroke | < 16 ms for strokes touching ≤ 120 tiles |

## Design choices that protect frame time

1. **No whole-layer re-upload during a stroke.** Paint dabs go into sparse 256 px tiles; the view
   draws those tiles above the layer, so the layer bitmap only changes at commit.
2. **Sparse snapshots.** Undo memory and work are proportional to the painted area, not the canvas.
3. **Unit-space dabs.** Each dab is drawn as a fixed-geometry circle/scatter/stamp under a canvas
   transform, so shaders are cached per (hardness, grain, scale) — dabs do not allocate shaders.
4. **Colour via `BlendModeColorFilter`** rather than baking colour into a per-dab shader.
5. **Fast-path compositing** for the common case (normal blend, opacity 1, no clipping).
6. **Input batching.** Every `ACTION_MOVE` consumes all historical samples in one pass; the view is
   invalidated once per frame via `postInvalidateOnAnimation()`.
7. **Autosave is debounced and stroke-aware**, so encoding never blocks or races painting.
8. **History bounded by bytes** (default 160 MB / 80 steps) so a long session cannot grow without
   limit.

## Known architectural limit

Layer bitmaps are full-canvas software bitmaps. On a hardware-accelerated `Canvas`, mutating a
bitmap invalidates its GPU texture upload wholesale, so any root-level paint (eraser, smudge, fill,
shape) causes a full-layer upload on the next frame. Paint strokes avoid this via the tile preview;
erasers do not. For canvases above ~4 MP this is the most likely frame-time cost. Tiled layer
storage (each layer = a grid of tiles, drawn tile-by-tile) is the designed next step and would be
contained to `Layer` + `LayerCompositor`.

## Measurement procedure (to run on hardware)

```bash
# Frame statistics for the current app
adb shell dumpsys gfxinfo studio.vela.art.debug reset
# …perform the scenario…
adb shell dumpsys gfxinfo studio.vela.art.debug

# Per-frame trace (Perfetto)
adb shell perfetto -o /data/misc/perfetto-traces/trace -t 10s \
  sched freq idle am wm gfx view binder_driver hal dalvik camera input res memory
adb pull /data/misc/perfetto-traces/trace

# Memory
adb shell dumpsys meminfo studio.vela.art.debug
```

Scenarios to record (each on a mid-range and a high-end device):

| # | Scenario | Metrics |
|---|---|---|
| 1 | 60 s continuous soft-brush stroke, 2048², 3 layers | mean/p95 frame time, jank count, dropped samples |
| 2 | Same with charcoal (textured, grain) | as above |
| 3 | Eraser stroke on 3508×2480, 8 layers | frame time, texture upload cost |
| 4 | Pinch-zoom + rotate continuously at 400 % | frame time |
| 5 | Fill on a 3508×2480 layer | wall-clock time for the op |
| 6 | Undo/redo 50 strokes | per-op latency, history bytes |
| 7 | Autosave 8 layers 3508×2480 | time to save, UI hitch (frame drop during save) |
| 8 | 10-minute session memory | PSS growth, bitmap count |

Expected-sensitive knobs once measured: tile size (256), history budget, `StrokeSession` split
threshold (120 tiles), checkerboard shader scale.

## Where to look first if a scenario misses target

1. `LayerCompositor.drawStack` — are we hitting the fast path for most layers?
2. `CanvasView.onDraw` — is the preview tile count large (many tiles) on long strokes?
3. `DabRenderer` — grain `ComposeShader` per size change (check shader cache hits).
4. `ProjectRepository.saveInternal` — PNG encode time; consider WebP-lossless layers later.
