# Technical decisions (ADR log)

Each record states the decision, the alternatives considered, and why. "Verified" columns are
absent on purpose: none of these could be benchmarked here (no toolchain), so the reasoning is
about design constraints, not measured wins.

---

## ADR-001 — Canvas is a classic `View`, Compose owns the chrome

**Decision.** The painting surface is a custom `android.view.View` (`CanvasView`) hosted through
`AndroidView`; every panel, bar and screen around it is Compose.

**Alternatives.**
- *Pure Compose `Canvas` + `pointerInput`*: recomposition and pointer-input layering sit in the
  drawing hot path; obtaining historical `MotionEvent` samples and tool types requires
  `pointerInteropFilter`/`awaitPointerEvent` gymnastics, and partial invalidation is not
  expressible.
- *AndroidX Ink API* (see ADR-002).
- *Custom GPU surface (`SurfaceView`/GL or Vulkan)*: the right long-term answer for very large
  canvases, but it makes text/UI integration, blend-mode parity with Skia and bitmap export far
  harder, and it cannot be validated here.

**Why.** `View.onTouchEvent` gives direct, synchronous access to `MotionEvent` history, pressure,
tilt, orientation and tool type; `onDraw` gives exact control of frame pacing and invalidation.
Compose stays where it is strong: layout, theming, state, animation.

**Consequence.** Stylus handling and rendering are testable only on a device; the CanvasView keeps
its state machine small and its maths (viewport) factored into a pure, unit-tested class.

---

## ADR-002 — No AndroidX Ink dependency

**Decision.** Do not depend on `androidx.ink:*`. Implement stylus ingestion directly.

**Alternatives.** Ink API provides stroke authoring + brush APIs, but at the time of writing its
authoring/brush artifacts are alpha releases, it targets `SurfaceControl`-backed canvases, and its
brush model does not expose the per-dab dynamics (spacing, scatter, grain, jitter, chisel nibs)
this brush engine needs.

**Why.** A production release must not depend on alpha artifacts for its core interaction, and
Ink's abstraction would have to be bypassed for the brush engine anyway. `MotionEvent` is stable
API since forever.

**Consequence.** Ink can later be introduced *inside* the rendering abstraction (e.g. for
low-latency front-buffered rendering) without changing tools, history or the document model.

---

## ADR-003 — Tile-based stroke buffer instead of direct pushing

**Decision.** Paint strokes accumulate dabs into sparse 256 px tile buffers and are composited
onto the layer at stroke opacity. Eraser and smudge strokes write directly to the layer, but still
record per-tile snapshots.

**Alternatives.**
- *Push every dab straight into the layer*: overlapping dabs at opacity < 1 darken each other,
  which makes soft brushes look wrong; also forces a full layer re-upload per frame.
- *A single full-canvas scratch bitmap*: correct, but doubles memory on large canvases.

**Why.** The tile buffer makes per-stroke opacity exact (dabs overlap inside the buffer, composited
once), keeps GPU uploads proportional to the painted area, and gives undo for free (see ADR-004).
It also bounds live-stroke memory: sessions split at 120 tiles into multiple undo steps.

**Consequence.** Paint strokes need a preview path (`previewTiles()`) so the layer bitmap is not
touched until commit; eraser/smudge need a direct path. Both are explicit in `StrokeSession`.

---

## ADR-004 — Undo as per-tile snapshots, not full-state copies

**Decision.** `StrokeEdit` keeps, per touched tile: the pre-stroke pixels plus the buffered stroke
coverage (paint) or the post-stroke pixels (eraser/smudge/fill). Redo replays the same composite
function used live.

**Alternatives.** Full-document snapshots (memory-prohibitive), command diffs of pixel runs
(complex, no benefit over tiles), branching history trees (unnecessary for v1).

**Why.** Memory scales with *painted area*, not canvas size; undo and redo share one code path with
live painting (`TilePainter`), so they cannot drift apart. `HistoryStack` enforces a byte budget,
evicting oldest steps.

**Consequence.** A destructive op with no tile granularity (merge down) stores one full layer copy
for revert; such ops are rare and counted against the budget.

---

## ADR-005 — `minSdk 29`

**Decision.** Support Android 10+ only.

**Why.** API 29 introduces `android.graphics.BlendMode`, which is what makes Multiply, Screen,
Overlay, Soft light, Hue/Saturation/Colour/Luminosity etc. correct and cheap. On API 26–28 the
same set would need a software compositor per blend mode — a large, slow, bug-prone layer — or an
honest downgrade of the feature list. 29 also unlocks `WEBP_LOSSY`, `Bitmap.Config` improvements
and avoids legacy storage paths.

**Consequence.** Devices on Android 8–9 are unsupported (small and shrinking share).

---

## ADR-006 — Project format: ZIP + JSON manifest + PNG layers

**Decision.** `.vela` is a ZIP: `manifest.json`, `layers/<uuid>.png`, `thumb.png`. Writes go to
`document.vela.tmp`, are fsynced, then renamed; the previous file is kept as `document.vela.bak`.

**Alternatives.** SQLite blob store (poor for large binary layers, harder to inspect/recover),
proprietary binary (no debuggability), a folder of files without a container (easy to desync).

**Why.** PNG layers are lossless, universally debuggable, and streamable; a JSON manifest is
trivially versionable and migratable; a ZIP gives CRC-based corruption detection for free. Atomic
rename plus a backup plus a temp candidate gives three recovery paths, all attempted in
`ProjectRepository.openProject`.

**Consequence.** Saving re-encodes every layer PNG (CPU + I/O), so autosave is debounced and never
runs during a stroke. A future format could add delta encoding without breaking readers thanks to
`formatVersion`.

---

## ADR-007 — Room for metadata, files for pixels

**Decision.** Room stores catalogue rows (id, name, dimensions, timestamps, favourite, collection,
paths). Pixels never enter the database.

**Why.** The gallery needs reactive queries (`Flow`) and sorting; Room does that well. Blobs in
SQLite would make atomic file recovery harder and bloat the DB.

**Consequence.** The DB and the files can disagree; recovery logic prefers the file system and the
DB row is advisory. `deleteProject` removes both.

---

## ADR-008 — Settings as one serialised JSON blob in DataStore

**Decision.** `AppSettings` is a `@Serializable` data class stored under one preferences key.

**Alternatives.** One preference key per field (~30 keys, verbose mapping code, easy to desync).

**Why.** Adding a setting is one field plus one default; migration is handled by
`ignoreUnknownKeys` + defaults; the whole object is easy to pass down the composition.

**Consequence.** Whole-blob writes on each change (fine at this size). A corrupt blob falls back to
defaults instead of crashing startup.

---

## ADR-009 — Manual dependency wiring, no DI framework

**Decision.** `AppContainer` builds the five-object graph; ViewModels take explicit constructor
parameters and small `Factory` classes.

**Why.** Hilt/Koin would add a compiler plugin (build risk) and indirection for a graph that fits
on one screen. Explicit constructors also make test injection trivial.

**Consequence.** Screen-level ViewModels are created by `viewModel(factory = …)`; adding a
dependency is a two-line change.

---

## ADR-010 — Procedural grain instead of bundled textures

**Decision.** Six grain textures are generated at runtime (value noise, weave, chalk, speckle…),
cached per kind at 256 px with `REPEAT`.

**Why.** Avoids licensing questions for third-party texture assets, keeps APK size flat, and lets
grain scale/strength be tuned per dab. Shaders are cached per (hardness, grain, grain scale) and
colour is applied with a `BlendModeColorFilter`, so a dab never allocates a shader.

**Consequence.** Grain is dab-space, not document-space: texture stretches with brush size. Document-
space paper grain would require a stroke-wide mask pass (planned, not built).

---

## ADR-011 — What v1 deliberately does *not* ship

**Decision.** No selection, transform/warp, text, gradients, filters/adjustment layers, layer
groups, vector layers, animation or 3D. None of these appear as disabled UI.

**Why.** The brief's rule — no fake functionality — is better served by a smaller app where every
control changes real output. These features also change the document model (masks, vector nodes,
frame timelines) and would destabilise the raster core if rushed.

**Consequence.** The seams exist: `EditCommand` for new undoable ops, `Layer` for new layer kinds,
`BrushEngineKind` for new dab engines, `formatVersion` for new persisted data. `FEATURE_MATRIX.md`
marks each as not implemented.

---

## ADR-012 — Version pins chosen for internal consistency

**Decision.** AGP 8.7.3, Gradle 8.9, Kotlin 2.1.0 (with the Compose and serialization plugins at
the same version), KSP 2.1.0-1.0.29, Compose BOM 2024.12.01, Room 2.6.1, Navigation 2.8.5,
DataStore 1.1.1, Coil 2.7.0, kotlinx-serialization 1.7.3, compileSdk/targetSdk 35.

**Why.** These versions are known to be mutually compatible. Picking "latest of everything" without
any ability to resolve artifacts would be guessing; a coherent older set compiles, and upgrading a
pinned set is mechanical.

**Consequence.** A newer Android Studio will offer upgrades. Upgrade AGP + Kotlin + KSP + Compose
*together*, then re-run tests.
