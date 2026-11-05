# Feature matrix

Status legend: **Implemented** = real code path, no placeholder. **Partial** = works with stated
limits. **Not implemented** = absent from the UI entirely (no dead buttons). **Planned** =
designed for, not built.

> "Implemented" describes the source implementation. Runtime verification on a device is
> outstanding for everything (see `IMPLEMENTATION_STATUS.md`).

## Application shell

| Feature | Status | Notes |
|---|---|---|
| Creative home / gallery dashboard | Implemented | Bento layout, hero create card, statistics |
| Project grid with thumbnails | Implemented | Coil loads PNG thumbnails written at save time |
| Search / sort / favourites / collections | Implemented | Collections stored per project; filter chips |
| Rename / duplicate / delete project | Implemented | Duplicate deep-copies layer bitmaps |
| New-canvas workflow | Implemented | Presets, custom size, DPI, paper, memory estimate |
| Import image as project | Implemented | Sampled decode, clamped to 4096 px |
| Settings screen | Implemented | Theme, accent, corners, density, font scale, gestures, autosave, storage |
| Adaptive tablet / phone layout | Implemented | Inspector becomes a bottom panel under 760 dp |
| Navigation | Implemented | Navigation-Compose `NavHost` |
| Onboarding / tutorials | Not implemented | — |
| Asset library (textures, palettes, fonts) | Not implemented | Only brush library exists; textures are procedural |

## Stylus & input

| Feature | Status | Notes |
|---|---|---|
| Stylus detection & separation from touch | Implemented | `toolOf()` + `InputPolicy` |
| Pressure | Implemented | Drives dab size/flow/opacity via response curves |
| Tilt | Implemented | `AXIS_TILT`, widens dabs where configured |
| Orientation | Implemented | Captured per sample; used by chisel presets via jitter |
| Historical pointer samples | Implemented | `MotionEvent` history consumed per move event |
| Hover cursor | Implemented | Outlined brush-size ring |
| Stylus eraser tip | Implemented | Switches to eraser without changing tool (configurable) |
| Stylus buttons | Partial | Not mapped to actions |
| Palm rejection | Implemented | Finger ignored while stylus is down/hovering |
| Finger drawing toggle | Implemented | Off = fingers only navigate |
| Pinch zoom / two-finger pan / rotate | Implemented | Focal-point-correct viewport maths |
| Two-finger undo / three-finger redo taps | Implemented | Duration + movement thresholds, configurable |
| Stroke smoothing / stabilisation | Implemented | EMA smoothing, per-brush strength |
| Pressure curve editor (multi-point) | Partial | Gamma + output window per parameter, not a point editor |
| Latency optimisation (prediction) | Not implemented | No stroke prediction; samples consumed as delivered |

## Brush engine

| Feature | Status | Notes |
|---|---|---|
| Brush presets | Implemented | 30 presets, 17 categories, each tuned differently |
| Size / opacity / flow / spacing / hardness | Implemented | All affect rendering |
| Roundness / angle / scatter / jitter | Implemented | Nib transform per dab |
| Grain textures | Implemented | 6 procedural textures, cached, scale + strength |
| Spray / stamp / particle engines | Implemented | Deterministic per-dab seeds |
| Smudge | Partial | Samples colour under the pointer and drags it; no directional pickup buffer |
| Eraser (soft + hard) | Implemented | `DST_OUT` compositing |
| Taper | Partial | Taper-in implemented; tail taper not |
| Colour dynamics (hue/sat/value jitter) | Implemented | Applied per dab |
| Brush Studio with live preview | Implemented | Preview rendered by the real dab pipeline |
| Save / rename / delete custom brushes | Not implemented | Editing is session-local; presets are code-defined |
| Brush import / export (JSON) | Not implemented | Preset type is `@Serializable`, plumbing absent |
| Pressure → colour dynamics | Not implemented | Pressure affects size/flow/opacity only |

## Canvas & rendering

| Feature | Status | Notes |
|---|---|---|
| Zoom / pan / rotate | Implemented | Immutable `Viewport`, unit-tested |
| Fit / reset view | Implemented | Includes pan clamping |
| Transparency checkerboard | Implemented | Screen-constant square size |
| Real-time stroke preview | Implemented | Sparse tile buffers drawn above the active layer |
| Tile-based incremental updates | Implemented | 256 px tiles; only touched tiles are snapshotted/composited |
| Layer compositing | Implemented | Per-layer `saveLayer` for opacity/blend |
| 17 blend modes | Implemented | `android.graphics.BlendMode` (API 29+) |
| Alpha lock | Implemented | `SRC_ATOP` composite |
| Clipping masks | Implemented | Layer clipped by the visible layer below |
| Layer groups (folders) | Not implemented | — |
| Canvas rotation-aware coordinates | Implemented | Same matrix path for input and rendering |
| GPU/Rust engine | Not implemented | Deliberate; see ADR-001/002 |
| Tiled layer storage (memory) | Not implemented | Layer bitmaps are full-canvas; oversized canvases warn |

## Layers

| Feature | Status |
|---|---|
| Add / delete / duplicate / reorder / rename | Implemented |
| Visibility / lock / opacity | Implemented |
| Blend modes | Implemented |
| Alpha lock / clipping mask | Implemented |
| Merge down | Implemented (undoable) |
| Layer thumbnails | Implemented (cached, invalidated on paint) |
| Clear layer | Implemented |
| Masks, adjustment layers, layer effects | Not implemented |

## History

| Feature | Status | Notes |
|---|---|---|
| Undo/redo strokes, erasers | Implemented | Per-tile snapshots |
| Undo/redo fills, shapes, clear | Implemented | Whole-layer snapshot pairs |
| Undo/redo layer add/remove/reorder/props/merge | Implemented | Command objects |
| Memory-bounded history | Implemented | Byte budget + step cap, oldest evicted |
| Long-stroke splitting | Implemented | Sessions split at 120 tiles |
| Panels/zoom state undo | Not implemented | Viewport state is not part of history (intended) |

## Colour

| Feature | Status | Notes |
|---|---|---|
| HSV sliders, alpha, hex input | Implemented | In inspector and Color Studio |
| Interactive HSV wheel | Implemented | Sweep + radial gradients, drag picks hue/sat |
| Swatches + recent colours | Implemented | Session-local recents, fixed palette |
| Colour harmonies | Implemented | Complementary/triadic/analogous/split computed live |
| Eyedropper (layer stack or merged) | Implemented | Top-most non-transparent layer by default |
| Custom palettes / saved swatches | Partial | Palette is fixed; recents are session-only |
| Gradient editor | Not implemented | — |

## Tools

| Tool | Status |
|---|---|
| Brush / Pencil / Pen | Implemented (distinct presets + slots) |
| Eraser / Smudge | Implemented / Partial |
| Flood fill (contiguous + global, tolerance, alpha lock) | Implemented |
| Shapes (line, rectangle, ellipse; filled or stroked) | Implemented |
| Eyedropper | Implemented |
| Selection (rect, lasso, magic wand, feather, grow/shrink) | Not implemented |
| Transform (free, rotate, perspective, warp) | Not implemented |
| Liquify, symmetry, rulers, guides, perspective/isometric guides | Not implemented |
| Text / vector paths | Not implemented |
| Filters & adjustments (blur, sharpen, curves, levels, halftone…) | Not implemented |

## Documents, storage, export

| Feature | Status | Notes |
|---|---|---|
| Native editable format `.vela` | Implemented | ZIP: manifest + per-layer PNG + thumbnail |
| Atomic save + backup + temp recovery | Implemented | Rename-based commit, `document.vela.bak` |
| Corruption detection | Implemented | Manifest validation, ZIP CRC, dimensions |
| Format versioning + migration hook | Implemented | `formatVersion`, `migrate()` |
| Automatic saving (debounced + on pause/exit) | Implemented | Never during an active stroke |
| Room project catalogue | Implemented | Metadata only, pixel data on disk |
| Thumbnails | Implemented | Regenerated on each save |
| Storage reporting / temp cleanup | Implemented | Settings screen |
| PNG / JPEG / WebP export, scale, background, share sheet | Implemented | |
| PSD export | Not implemented | Interop limits documented in ARCHITECTURE.md |
| PDF / TIFF / SVG export | Not implemented | |
| Cloud sync / collaboration | Not implemented | Offline-first by design |

## Accessibility

| Feature | Status | Notes |
|---|---|---|
| Content descriptions on icon buttons | Implemented | Every `VelaIconButton` requires one |
| Minimum touch target | Implemented | 48 dp tokens |
| Configurable font scale | Implemented | Applied through the typography scale |
| Reduced-motion setting | Partial | Setting persists; motion is already minimal |
| Non-colour state cues | Implemented | Selection uses fill + border + icon state |
| Keyboard navigation | Not implemented | Canvas is pointer-first |
| Screen-reader support for canvas | Not implemented | A custom drawing surface has no semantic model |
