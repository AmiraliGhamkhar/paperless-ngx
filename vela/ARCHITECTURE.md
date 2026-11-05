# Architecture

## Layering

```
presentation/         Compose screens + ViewModels (state, user intent)
   │  (reads UI state, calls controller/use-cases)
drawing/              EditorController · strokes · layers · compositor · history · tools
   │  (owns ArtDocument, mutates bitmaps)
domain/model/         Pure data: brush presets, blend kinds, canvas specs, dabs
core/                 Geometry (viewport), input policy, export, maths — framework-light
data/                 .vela codec, Room catalogue, repository, settings, storage layout
ui/                   Design tokens + shared components (no business logic)
app/                  Application, Activity, DI container, navigation host
```

Dependencies point **inwards**: `presentation → drawing → domain/core`, `data` is used by
`presentation` through the repository and by `drawing` only for the codec. `domain/model` and
`core/geometry` have no Android types, which is what makes them JVM-testable.

There is exactly one module (`:app`). A second module would only be justified today for a
standalone rendering library; the package boundaries already prevent circular dependencies.

## Editor data flow

```
MotionEvent ─► CanvasView ─► InputPolicy (stylus/finger/palm/gesture)
                     │                 │
                     │                 └─► Viewport (zoom/pan/rotate maths)
                     ▼
              EditorController ──► DabGenerator ──► Dab ──► StrokeSession ──► tiles
                     │                                                │
                     │                                                ▼
                     │                                        Layer bitmaps
                     ▼
                ArtDocument (layers, history, dirty flag)
                     │                       │
        onStateChanged │                      │ onContentChanged
                       ▼                      ▼
                EditorViewModel        autosave (debounced)
                       │
                       ▼
                 EditorUiState (StateFlow) ─► Compose panels / bars
```

**Why a controller separate from the ViewModel?** Pointer input needs synchronous, allocation-light
access to brush state, layers and history at up to 240 Hz. Routing each dab through StateFlow and
recomposition would be both slow and fragile. `EditorController` is the single source of truth for
interactive editing state, always touched on the main thread; `EditorViewModel` mirrors it into an
immutable `EditorUiState` for Compose and owns autosave/persistence coroutines.

## Drawing pipeline

1. **Sample** — `CanvasView` reads `MotionEvent` including *historical* samples, converts screen →
   document coordinates through `Viewport`, and normalises pressure/tilt/orientation.
2. **Generate dabs** — `DabGenerator` smooths the cursor (stabiliser EMA), walks the segment at a
   constant spacing with carried-over remainder, and applies pressure/tilt/velocity response,
   taper-in, scatter, size/angle jitter and colour dynamics.
3. **Stamp** — `DabRenderer` draws each dab in a *unit space* (a 64 px radius circle) under canvas
   transform, so shaders are cached per (hardness, grain, grain scale) and colour is applied with a
   `BlendModeColorFilter` instead of a per-dab shader.
4. **Accumulate** — `StrokeSession` writes buffered dabs into sparse 256 px tiles
   (`TileGeometry.key`), recording a pre-stroke snapshot of every touched tile.
5. **Preview** — the view draws the stroke tiles above the active layer, so the layer bitmap is not
   re-uploaded during the stroke.
6. **Commit** — on lift, the tiles are composited onto the layer at stroke opacity (or `DST_OUT` for
   erasers, `SRC_ATOP` for alpha-locked layers) and a `StrokeEdit` command enters history.

Eraser and smudge strokes bypass the buffer (they must read/modify existing pixels) and paint
directly, but still snapshot tiles for undo.

## Layer compositing

`LayerCompositor` draws bottom→top in document space:

- **Fast path** — normal blend, opacity ≥ 0.999, no clipping: a plain `drawBitmap`.
- **Isolated path** — otherwise `saveLayer(bounds, paint(alpha, blendMode))`, draw the layer, then
  restore so opacity and blend apply to the layer as a whole rather than per pixel.
- **Clipping** — inside the saved layer, the base layer is drawn with `BlendMode.DST_IN`, keeping
  the clipped layer only where the layer below has alpha.

## Memory model

| Allocation | Size | Lifetime |
|---|---|---|
| Layer bitmap | `w × h × 4` | document |
| Stroke tile (before + coverage) | up to 2 × 256×256×4 = 512 KB | stroke, then history |
| Gamut/grain textures | 6 × 256 KB | app |
| Shader cache | ≤ 24 shaders | app |
| History | bounded by `HistoryStack` byte budget (default 160 MB, 80 steps) | session |

Live strokes are bounded by splitting sessions at 120 tiles (≈ 30 MB worst case) into separate undo
steps. Canvases above 16 MP raise a warning in the new-canvas sheet because full-canvas layer
bitmaps multiply quickly; tiled layer storage is a known future improvement, not a claim.

## Threading

- **Main thread**: input, dab generation, bitmap mutation, compositing, undo/redo, UI state.
- **IO dispatcher**: `.vela` write/read, PNG/JPEG/WebP encoding, thumbnails, DB queries.
- Autosave is debounced (`AppSettings.autosaveSeconds`) and **defers while a stroke is active**, so
  bitmaps are never compressed while being painted. Additional saves fire on `ON_PAUSE`/`ON_STOP`
  and on editor disposal.

## Persistence & recovery

1. `ProjectRepository.save()` → `DocumentCodec.write()`.
2. Write to `document.vela.tmp`, `fd.sync()`, then rename over `document.vela`; the previous file is
   first copied to `document.vela.bak`.
3. `openProject()` tries, in order: the primary archive → the backup → the temporary file, each
   validated by `DocumentManifest.validate()` (version, dimensions, layer ids, file presence) and
   ZIP CRC verification while streaming.
4. Failures are returned as `LoadResult.Failure`/`OpenResult.Failure` with a human-readable reason;
   the UI shows a retry affordance instead of crashing.

## Format compatibility statement

`.vela` is the editable format. PNG/JPEG/WebP are flat exports. **PSD, PDF, TIFF and SVG are not
implemented**, and nothing in this repository should be read as claiming PSD interoperability. When
PSD export is added, it must be validated file-by-file (layers, blend modes, opacity, clipping) and
documented with its exact limitations — a file that merely opens is not evidence of compatibility.

## Error handling policy

- No silent `catch` blocks: recoverable failures surface as typed results and user-visible messages.
- `OutOfMemoryError` during export is caught and reported (export allocates a flat copy).
- Corrupt settings fall back to defaults; corrupt projects fall back to backup/temp, then to an
  explicit error screen with a path back to the library.
