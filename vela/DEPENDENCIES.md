# Dependency and license inventory

No third-party *assets* (textures, fonts, icons, brushes) are bundled. Icons come from
Material Icons (Apache-2.0, via `material-icons-extended`); grain textures are generated at
runtime; brushes are defined in code. App identity (name, colours, icon) is original.

## Runtime dependencies

| Component | Version | License | Why |
|---|---|---|---|
| Kotlin stdlib | 2.1.0 | Apache-2.0 | Language runtime |
| kotlinx-coroutines-android | 1.9.0 | Apache-2.0 | Async work, autosave, IO off the main thread |
| kotlinx-serialization-json | 1.7.3 | Apache-2.0 | `.vela` manifest, settings blob |
| AndroidX Core KTX | 1.15.0 | Apache-2.0 | `FileProvider`, platform helpers |
| AndroidX Activity Compose | 1.9.3 | Apache-2.0 | `ComponentActivity`, `setContent`, edge-to-edge |
| AndroidX Lifecycle (runtime-ktx, runtime-compose, viewmodel-compose) | 2.8.7 | Apache-2.0 | ViewModel, `collectAsStateWithLifecycle`, lifecycle observer |
| AndroidX Compose BOM | 2024.12.01 | Apache-2.0 | Aligns Compose artifact versions |
| Compose UI / Graphics / Foundation / Material3 | via BOM (1.7.x / 1.3.x) | Apache-2.0 | UI, layout, theming |
| Compose Material Icons Extended | via BOM | Apache-2.0 | Icon set |
| AndroidX Navigation Compose | 2.8.5 | Apache-2.0 | Screen navigation |
| AndroidX Room (runtime, ktx, compiler via KSP) | 2.6.1 | Apache-2.0 | Project catalogue with reactive queries |
| KSP (com.google.devtools.ksp) | 2.1.0-1.0.29 | Apache-2.0 | Room code generation |
| AndroidX DataStore Preferences | 1.1.1 | Apache-2.0 | Settings persistence |
| Coil Compose | 2.7.0 | Apache-2.0 | Thumbnail loading in the gallery |

## Test-only dependencies

| Component | Version | License |
|---|---|---|
| JUnit 4 | 4.13.2 | EPL-1.0 |
| kotlinx-coroutines-test | 1.9.0 | Apache-2.0 |
| AndroidX Test Ext JUnit | 1.2.1 | Apache-2.0 |
| AndroidX Test Espresso Core | 3.6.1 | Apache-2.0 |
| Compose UI Test (JUnit4, manifest) | via BOM | Apache-2.0 |

JUnit 4's EPL-1.0 applies to test code only and does not ship inside the APK.

## Build tooling

| Tool | Version | License |
|---|---|---|
| Android Gradle Plugin | 8.7.3 | Apache-2.0 |
| Gradle | 8.9 | Apache-2.0 |
| Kotlin Gradle Plugin (+ Compose, Serialization plugins) | 2.1.0 | Apache-2.0 |

## Deliberately not used

| Considered | Why not |
|---|---|
| AndroidX Ink (`androidx.ink:*`) | Alpha artifacts; cannot be a production-critical dependency; brush model insufficient (ADR-002) |
| Rust + wgpu (ProCraft-style) | Real engine complexity with no ability to profile here; the rendering abstraction keeps this open (ADR-001) |
| Hilt / Koin | Compiler plugin + indirection for a five-object graph (ADR-009) |
| Sketch / LuckySVG / PSD libraries | Not needed until vector/PSD features exist; licence surface would grow for no current benefit |
| Google Fonts / Inter font files | Font licensing is fine (OFL) but the binary was not fetchable here; the platform sans-serif is used, and the typography scale is centralised for a one-line swap |

## Updating

Bump AGP + Kotlin + KSP + Compose BOM together, then re-run unit tests. AndroidX/Toolchain versions
are pinned in three places only: `build.gradle.kts` (root), `app/build.gradle.kts`,
`gradle/wrapper/gradle-wrapper.properties`.
