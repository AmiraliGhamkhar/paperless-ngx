# Implementation status

Last updated: 2026-11-05 (session date). This file is the single source of truth for what is
built, what is verified, and what is not.

## Environment reality (why some rows say "not executed")

The sandbox used to write this code had:

- no JDK, no Android SDK, no Gradle, no Kotlin compiler;
- blocked egress to `dl.google.com`, `services.gradle.org`, `repo1.maven.org` (only GitHub,
  PyPI and npm were reachable);
- an empty Android toolchain cache.

Consequently: **no Gradle sync, no compilation, no unit-test execution, no APK was produced.**
The repository this work lives in (`paperless-ngx`) is a Django application and contained no
Android code to inherit.

Everything else below was done explicitly rather than assumed.

## Milestones

| # | Milestone | State | Notes |
|---|---|---|---|
| 0 | Repository & engineering foundation | **Done (unbuilt)** | Gradle KTS setup, AGP/Kotlin/KSP pins, manifest, resources, launcher icon, ProGuard rules, package architecture, design tokens, verification tooling |
| 1 | Visual foundation & navigation | **Done (unbuilt)** | Theme engine (dark/light/AMOLED/custom accent), Bento card system, gallery dashboard, new-canvas flow, settings, NavHost, adaptive layouts |
| 2 | Stylus drawing prototype | **Done (unbuilt)** | Custom `CanvasView`, pressure/tilt/orientation/historical samples, palm rejection, gestures (pan/zoom/rotate/tap-undo/redo), eraser, undo/redo |
| 3 | Painting engine | **Done (unbuilt)** | Brush presets + dynamics, dab generator, 6 dab engines, procedural grain, tile stroke buffer, layer compositing, blend modes, alpha lock, clipping, history |
| 4 | Professional workflow | **Partial** | Layers inspector, colour studio, brush studio with live engine preview, shapes, flood fill, eyedropper, import, export. Selection/transform/warp/text/gradients **not implemented** |
| 5 | Reliability | **Partial** | Atomic saves, backup + temp recovery, corruption detection, format versioning, bounded history, autosave on pause/exit. Large-document profiling and crash testing **not performed** (no device) |
| 6 | Advanced tools | **Partial** | Blend modes and grain/texture fully implemented; masks beyond clipping, filters/adjustments, PSD, gradients **not implemented** |
| 7 | Release engineering | **Partial** | Release variant, signing config plumbing, shrinker rules, icon, backup rules, docs. **No AAB/APK produced, no install test, no physical-device stylus test** |
| 8 | Extended studio (vector/animation/comic/3D/collab) | **Not started** | Deliberately out of scope; architecture keeps the door open (see ADR-011) |

## Verified functionality (executed in this session)

| Check | Command | Result |
|---|---|---|
| XML well-formedness (manifest + 10 resources) | `python3 tools/verify_project.py` | pass |
| Bracket/paren/brace balance, 60 Kotlin files | same | pass |
| Every `studio.vela.art.*` import resolves to a declared symbol (213 imports) | same | pass |
| Member accesses on project objects (`VelaIcons.*`, `TileGeometry.*`, `DocumentManifest.*`, enum constants…) | same | pass, 0 unresolved |
| Android resource references (`@style`, `@color`, `@drawable`, `R.string.*`) | same | pass, 16 references |
| Manifest `android:name` classes exist on disk (with package resolution) | same | pass |
| Source directory ↔ package declaration consistency | same | pass |
| No `TODO()`/`NotImplementedError`/`TODO`/`FIXME` markers in main sources | same | pass |
| No unused imports | same | pass, 0 |

## Not verified (requires the Android toolchain)

- Compilation of any Kotlin/Java source.
- Gradle configuration, dependency resolution, KSP/Room code generation.
- Execution of the 7 JVM unit test classes (`ViewportTest`, `DabGeneratorTest`,
  `TileGeometryTest`, `DocumentManifestTest`, `CanvasSpecTest`, `MultiTapRecognizerTest`, plus the
  instrumented `DocumentPersistenceInstrumentedTest`).
- Any runtime behaviour: drawing quality, frame rate, memory use, export correctness.
- Physical stylus behaviour (pressure curve feel, palm rejection, hover, eraser tip, latency).

These are listed again as remaining tasks below.

## Known risks to the first build

Ranked by likelihood; each has a one-line fix path.

1. **Version drift.** AGP 8.7.3 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29 / Compose BOM 2024.12.01 are
   pinned to a known-compatible set, but they predate the current date. A newer Studio may suggest
   upgrades; accept them together (AGP + Kotlin + KSP + Compose) or not at all.
2. **Room/KSP.** If KSP fails to match Kotlin, bump the two together or switch the DAO to the
   file-backed catalogue (the repository is already the only consumer).
3. **API details in Compose M3.** Some M3 components changed signatures across 1.2→1.3. If a
   composable fails to resolve, the fix is local to that call site.
4. **`WEBP_LOSSY`** is guarded by a `SDK_INT >= 30` check, so API 29 will take the deprecated
   `WEBP` path rather than crash.

## Remaining tasks (ordered)

1. Run `gradle wrapper`, sync, and fix any compile errors (expected: a handful, mechanical).
2. Run `:app:testDebugUnitTest`; fix failures. The pure-logic tests are the fastest signal on
   engine correctness.
3. Run `:app:assembleDebug`, install on a tablet with a stylus, and validate:
   latency, pressure response, palm rejection, hover cursor, two-finger undo, rotation continuity.
4. Run `:app:connectedDebugAndroidTest` for the archive and flood-fill tests.
5. Profile with Macrobenchmark/`adb shell dumpsys gfxinfo` on 2048² and 3508² canvases with 8 layers;
   record numbers in `PERFORMANCE.md` (currently design-only, no measurements).
6. Only then: implement selection + transform (highest remaining creative value), then adjustment
   layers, then vector layers.
