# Test results

## What was actually executed

Environment: Linux sandbox, no JDK / Android SDK / Gradle / Kotlin compiler; egress blocked to
`dl.google.com`, `services.gradle.org`, `repo1.maven.org`. A JDK could be installed from PyPI, but
the Kotlin compiler archive redirects to a blocked host, so **no Kotlin code could be compiled or
executed**. No test numbers below are invented.

### Executed: static structural verification

```
$ cd vela && python3 tools/verify_project.py

[xml] parsed 10 resource/manifest files
[balance] checked 60 Kotlin files
[kotlin] loaded 60 files in 28 packages
[imports] verified 213 project imports
[layout] package directories verified
[members] verified object member accesses (0 problems)
[resources] checked 16 resource references
[manifest] application/activity classes resolved
[imports] unused imports: 0

RESULT: PASSED (static structural verification only — not a compile)
```

The checker verifies:

| Check | What it catches | Result |
|---|---|---|
| XML well-formedness | Broken resource/manifest XML | 10/10 pass |
| Bracket balance (comment/string aware) | Truncated or malformed Kotlin blocks | 60/60 pass |
| Project import resolution | References to classes/functions that were never written | 213/213 resolve |
| Object member access | `VelaIcons.x`, `TileGeometry.x`, `DocumentManifest.x`, enum constants… | 0 unresolved |
| Resource references | `@style/…`, `@color/…`, `R.string.…` that do not exist | 16/16 resolve |
| Manifest classes | `android:name` targets missing on disk | pass |
| Package ↔ directory consistency | Sources in the wrong source-set path | pass |
| Leftover markers | `TODO()`, `TODO`, `FIXME`, `NotImplementedError` in shipped code | 0 found |
| Unused imports | Dead code smells | 0 |

**This is a structural check, not a compiler.** It cannot type-check expressions, verify Compose
signatures, or catch a wrong argument order. It does catch the mistakes that were actually present
during development (a missing `VelaIcons.merge`, a non-existent `boundControllerId()`, a mangled
`Modifier.padding()` extension, a `Color.alpha(Int)` misuse) — all of which were fixed.

### Not executed (toolchain required)

| Suite | Command | Status |
|---|---|---|
| JVM unit tests (6 classes) | `./gradlew :app:testDebugUnitTest` | Not run — no JDK/Kotlin |
| Instrumented tests (1 class) | `./gradlew :app:connectedDebugAndroidTest` | Not run — needs device + SDK |
| Compile (`assembleDebug`) | `./gradlew :app:assembleDebug` | Not run — artifacts unreachable |
| Lint | `./gradlew :app:lintDebug` | Not run |
| Physical stylus validation | manual | Not run — no hardware |

## Test inventory (written, awaiting execution)

### JVM unit tests — `app/src/test/java/studio/vela/art/`

| Class | Coverage |
|---|---|
| `ViewportTest` | doc↔screen round trip, centre mapping, zoom-about-focal invariance, rotate-about-focal invariance, rotation normalisation, zoom clamping, fit maths, visible-rect containment, pan clamping |
| `DabGeneratorTest` | constant dab spacing on a straight stroke, pressure→size, gamma curve values, pressure output window, velocity thinning, tilt widening, taper-in growth, scatter bounds, zero-alpha rejection |
| `TileGeometryTest` | key packing/uniqueness, tile counts, boundary-spanning circles, out-of-document rejection, edge-clamped tile rects, rect→tile enumeration |
| `DocumentManifestTest` | JSON round trip, validation of empty/duplicate/invalid layers, "too new" version reporting, migration identity, unknown-field tolerance, blend-name fallback |
| `CanvasSpecTest` | megapixel/memory maths, DPI→mm, limit clamping, oversized warning, scale aspect ratio, preset uniqueness/lookup |
| `MultiTapRecognizerTest` | two-finger undo, three-finger redo, drag rejection, slow-tap rejection, single-finger rejection, disabled gestures, palm-rejection policy, stylus-eraser policy |

### Instrumented tests — `app/src/androidTest/`

| Test | Coverage |
|---|---|
| `projectArchiveRoundTripsLayersAndProperties` | writes a real `.vela`, reads it back, asserts layer count, opacity, blend mode, alpha lock, active layer id |
| `corruptArchiveIsReportedNotThrown` | a non-ZIP file returns `LoadResult.Failure` instead of throwing |
| `floodFillFillsTheContiguousRegionOnly` | red fill stops at a wall, does not leak to the other region |
| `globalFillReplacesEveryMatchingPixel` | non-contiguous fill covers all matching pixels only |

## Performance results

**None.** No device, no profiler. `PERFORMANCE.md` describes the design and the exact procedure to
produce numbers; it contains no fabricated measurements.

## Manual test plan for the first device session

1. Cold start → gallery renders with empty state; create canvas from each preset category; verify
   memory estimate text changes and the warning appears above 16 MP.
2. Draw with a stylus at 3 pressures; confirm line width and opacity follow pressure; repeat with
   tilt (charcoal preset shows widest variation).
3. Rest palm on canvas mid-stroke → no stray marks; disable palm rejection in settings → stray
   marks appear (proves the setting is wired).
4. Two-finger tap → undo; three-finger tap → redo; disable both → nothing happens.
5. Pinch/rotate/pan while zoomed to 400 % → stroke lands under the cursor (coordinate sanity).
6. Draw on layer 2 with Multiply over layer 1 → correct composite; toggle alpha lock → paint stays
   inside existing pixels; enable clipping → paint clipped to layer below.
7. Fill with tolerance 0 vs 0.3 on a soft-edged shape → different regions filled.
8. Kill the app mid-session → reopen → autosaved work present; corrupt `document.vela` manually →
   backup recovery message appears.
9. Export PNG at 100 % and 25 %, share, verify dimensions and transparency handling.
10. Draw for 10 minutes with 8 layers on a 3508×2480 canvas → record `dumpsys gfxinfo` frame stats
    in `PERFORMANCE.md`.
