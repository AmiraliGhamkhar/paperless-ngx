# Vela Studio — Android digital painting studio

A native Android painting application for illustrators and digital painters: pressure-sensitive
stylus painting, a custom raster brush engine, layers with blend modes, undo/redo, an editable
project format and PNG/JPEG/WebP export. Fully offline — the app requests **no network
permission**.

```
Kotlin · Jetpack Compose (UI chrome) · custom Canvas/View rendering engine · Room · DataStore
```

---

## ⚠️ Build status — read this first

This project was written in a sandbox **without a JDK, Android SDK, Gradle or Kotlin compiler**,
and with network access blocked to `dl.google.com`, `services.gradle.org` and Maven Central. The
Android toolchain therefore could not resolve, and **no `assembleDebug` has been executed**.
Nothing below claims a build that did not happen.

What *was* executed in this environment:

| Verification | Command | Result |
|---|---|---|
| Static project verification (XML, syntax balance, import/symbol resolution, resource refs, manifest, package layout, leftover markers) | `python3 tools/verify_project.py` | **PASSED** — 60 Kotlin files, 213 project imports, 16 resource references, 0 errors, 0 warnings |

What still must be run on a machine with the Android SDK:

```bash
cd vela
gradle wrapper --gradle-version 8.9   # once: the wrapper JAR is a binary and is not committed
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:connectedDebugAndroidTest  # on-device tests (needs a connected device)
```

The wrapper JAR (`gradle/wrapper/gradle-wrapper.jar`) is a binary artifact and could not be
generated here. Either run `gradle wrapper --gradle-version 8.9` once (any local Gradle 8.x), or
open the folder in Android Studio, which will offer to provision the wrapper from
`gradle/wrapper/gradle-wrapper.properties` (already pinned to Gradle 8.9).

See `IMPLEMENTATION_STATUS.md` for the exact, unembellished state of every milestone.

---

## Requirements

- Android Studio Ladybug or newer (AGP 8.7.3)
- JDK 17
- Android SDK Platform 35, Build-Tools 35.x
- Android device or emulator, **API 29+** (Android 10+); a tablet with an active stylus
  (S Pen / USI / MPP) is strongly recommended for the real experience

`minSdk 29` is a deliberate choice: 29 is the first release with the unified
`android.graphics.BlendMode` API, which every non-trivial layer blend mode depends on
(see `TECHNICAL_DECISIONS.md`, ADR-005).

## Build and install

```bash
cd vela

# 1. Debug build
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# 2. Install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Release bundle (unsigned unless keystore.properties exists)
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Release signing is opt-in. Create `vela/keystore.properties` (never committed):

```properties
storeFile=/absolute/path/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without that file the release variant builds unsigned; debug builds are unaffected.

## Tests

```bash
./gradlew :app:testDebugUnitTest            # 7 JVM test classes, pure logic
./gradlew :app:connectedDebugAndroidTest    # on-device: project archive, flood fill
python3 tools/verify_project.py             # structural verification (no SDK needed)
```

## What the app does

**Gallery** — Bento dashboard: new canvas, recent work, favourites, collections, search, sorting,
duplicate/rename/delete, storage usage.

**New canvas** — presets (A4, US Letter, square, screen, ratios, webtoon), custom width/height/DPI,
paper colour or transparency, live megapixel + memory estimate with a warning for oversized
canvases.

**Editor** — tablet-first workspace: central canvas, left tool rail, top action bar, right
inspector (Brush / Colour / Layers), floating size–opacity bar, full-screen mode, adaptive layout
for phones.

**Painting** — 30 tuned brush presets across 17 categories; size, opacity, flow, spacing,
hardness, roundness, angle, scatter, stabilizer, taper, size/angle jitter, grain, pressure
response curves, tilt and velocity response, hue/saturation/value dynamics; six rendering engines
(round, textured, chisel, spray, stamp, smudge) plus erasers.

**Layers** — add, delete, duplicate, reorder, rename, visibility, lock, opacity, 17 blend modes,
alpha lock, clipping masks, merge down, thumbnails.

**History** — memory-bounded undo/redo covering strokes, erasers, fills, shapes, layer structure
and layer properties. Two-finger tap = undo, three-finger tap = redo (configurable).

**Persistence** — versioned `.vela` archive (ZIP: JSON manifest + one PNG per layer + thumbnail),
atomic write, previous-version backup, recovery from backup/temp on corruption, debounced autosave,
save on background/exit.

**Export** — PNG, JPEG, WebP at 100/50/25 % scale with optional background, via system share sheet
or app-private exports folder.

**Stylus** — real pressure/tilt/orientation consumed from `MotionEvent` (including historical
samples), palm rejection, stylus-eraser tip, finger drawing toggle, pinch zoom, two-finger pan,
two-finger rotate, hover brush-size cursor.

## Project layout

```
vela/
├── app/src/main/java/studio/vela/art/
│   ├── app/                     Application, MainActivity, DI container, NavHost
│   ├── core/                    geometry (viewport maths), input policy, export, common maths
│   ├── domain/model/            brush presets, blend kinds, canvas specs (pure, serialisable)
│   ├── drawing/                 brush engine, tile stroke buffer, layers, compositor, history, tools
│   ├── data/                    Room catalogue, .vela codec, repository, settings, storage layout
│   ├── presentation/            gallery, new canvas, editor, brush studio, colour studio, settings
│   └── ui/                      design tokens/theme, shared components, icon set
├── app/src/test/                7 JVM test classes (viewport, dabs, tiles, manifest, presets, input)
├── app/src/androidTest/         on-device archive round-trip + flood fill tests
└── tools/verify_project.py      structural verification (runs without the Android SDK)
```

## Documentation

- `IMPLEMENTATION_STATUS.md` — milestone-by-milestone state, verified vs. unverified
- `FEATURE_MATRIX.md` — implemented / partial / not implemented / planned
- `TECHNICAL_DECISIONS.md` — architecture decision records with rationale
- `ARCHITECTURE.md` — package responsibilities, rendering and data flow, threading model
- `TEST_RESULTS.md` — what was executed, what was not, and why
- `PERFORMANCE.md` — rendering/performance design and the benchmark procedure (no invented numbers)
- `DEPENDENCIES.md` — dependency and license inventory

## Known limitations

Honest summary; the full list is in `FEATURE_MATRIX.md`.

- No selection tools, transform/warp, text, gradients, filters, or adjustment layers.
- Layer **groups** (folders) are not implemented; clipping masks are.
- Strokes are rasterised immediately; there is no vector stroke editing.
- PSD/PDF/TIFF/SVG export is not implemented.
- The brush preview/pen cursor does not show brush texture, only size.
- Stroke opacity is applied when the stroke buffer is composited, so overlapping strokes still
  darken as separate strokes do — that is intended, but note that a *single* stroke is uniform.
