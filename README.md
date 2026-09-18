# ARPitch — Android AR cricket pitch placement

ARPitch is an Android-first, metric, world-locked AR foundation for laying out cricket stumps and crease guides without a tape measure. The user scans the ground, places the batting end, points down the intended pitch direction, selects **4 yd / 22 yd / 24 yd / custom**, locks the direction, then walks around the result. The pitch remains in 3D world space and naturally changes apparent size/perspective with camera movement.

> **Important:** this is not a 2D camera overlay. All pitch geometry is rendered in meters relative to an ARCore `Anchor`.

## What is already implemented

- Native Android / Kotlin, no Unity runtime.
- ARCore 1.54.0 world tracking.
- Horizontal plane detection with Depth hit-test fallback and estimated-surface-normal feature fallback.
- One authoritative batting-end anchor; the entire pitch is generated as local metric geometry from that anchor.
- Live direction aiming, then hard direction lock.
- 0.5° fine heading correction buttons.
- Exact yard-to-meter conversion (`1 yd = 0.9144 m`).
- 4 yd, 22 yd, 24 yd and custom yard/meter distances.
- 3D wicket geometry at both ends.
- Pitch ribbon, centerline, wicket footprints, bowling crease, popping crease and return-crease visualization.
- World-projected labels for Batting End / Bowling End / distance.
- Tracking / plane / Depth / FPS diagnostics.
- Camera-to-bowling-end live distance readout.
- No location permission, storage permission or network permission.
- R8/shrinking release build.
- GitHub Actions for lint, unit tests, debug APK, optimized release APK/AAB.
- Dependabot configuration for Gradle and GitHub Actions.

## Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| AR tracking | Google ARCore 1.54.0 |
| Rendering | Native OpenGL ES 2.0 |
| UI | Android Canvas overlay |
| Android Gradle Plugin | 9.4.0 |
| Gradle | 9.6.0 |
| Compile / Target SDK | 36 |
| Minimum Android | API 24 |

OpenGL ES 2.0 is intentional: the AR scene itself is extremely lightweight and this keeps the rendering path available across the broadest ARCore-certified Android hardware. The virtual scene is three mostly-static GPU meshes and only rebuilds geometry when the selected distance changes.

## Architecture

```text
Camera + IMU
    ↓
ARCore visual-inertial tracking
    ↓
Ground hit selection
Plane → DepthPoint → oriented Feature Point fallback
    ↓
Batting-end ARCore Anchor
    ↓
Anchor-local heading + exact metric distance
    ↓
Pitch local coordinate system
X = across pitch, Y = surface normal, Z = pitch direction
    ↓
OpenGL camera projection
    ↓
World-locked 3D wickets / creases / pitch / labels
```

The core stability rule is simple: **positions are never saved in screen pixels.** Screen coordinates are used only for interaction and labels. The actual pitch is always rendered from the current ARCore anchor pose each frame.

## Build locally

Requirements:

- Android Studio compatible with AGP 9.4.
- JDK 17.
- Android SDK 36.
- A physical ARCore-certified Android device. AR tracking is not meaningfully testable in a normal emulator.

Windows:

```bat
gradlew.bat assembleDebug
```

macOS / Linux:

```bash
./gradlew assembleDebug
```

The wrapper launcher self-fetches the official Gradle 9.6.0 wrapper JAR if it is not present yet.

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions — push and build

1. Create an empty GitHub repository.
2. Push this project.
3. Open **Actions → Android CI**.
4. The workflow installs JDK 17 + Android 36 SDK, then runs lint, unit tests and `assembleDebug`.
5. Download the installable APK from the workflow artifact named `ARPitch-debug-<commit>`.

A tag like `v0.1.0` also runs **Release Build**, producing optimized release APK/AAB artifacts. For Play Store distribution, add your real signing pipeline/keystore rather than distributing an unsigned release artifact.

## Field workflow

1. Open ARPitch and slowly move the phone left/right over the ground until `SURFACE EXCELLENT` or `GOOD` appears.
2. Aim the center reticle at the batting-end stump center and tap **PLACE BATTING END**.
3. Point the phone down the desired pitch axis. The full metric pitch previews from the anchored origin.
4. Select `22 yd`, `24 yd`, `4 yd`, or **Custom**.
5. Use `↶ 0.5°` / `0.5° ↷` if needed.
6. Tap **LOCK DIRECTION**.
7. Walk toward either end or move to the side. The pitch should stay world-locked while perspective changes naturally.
8. Place physical stumps on the green virtual footprints and line the ground from the virtual crease guides.

## Accuracy model

ARPitch uses ARCore metric world coordinates, but a phone camera is **not a certified surveying instrument**. Outdoor long-baseline error depends on:

- Device camera/IMU calibration quality.
- Scene texture and visual features.
- Lighting and motion blur.
- How much the user scans before placement.
- Tracking interruptions or rapid motion.
- Ground flatness and how ARCore estimates the local surface.
- Accumulated visual-inertial drift over a 20+ meter walk.

Do not publish a marketing claim such as “±2 cm” until it is supported by a real multi-device field validation dataset. See `docs/FIELD_TEST_PLAN.md`.

## Why a single authoritative anchor?

Hundreds of anchors do not make the pitch more accurate. They add management cost and can create inconsistent local corrections. ARPitch keeps one anchor at the batting end and represents the whole pitch as deterministic child geometry in meters. This is efficient and internally rigid: the 22-yard baseline cannot stretch because of UI or rendering code.

A future **far-end verification** mode can add a second observation as a quality check without turning the far end into a competing coordinate system.

## Offline behavior

The AR experience itself does not require ARPitch to have Internet permission. ARCore tracking is local once Google Play Services for AR is installed and available on the device. The current app intentionally requests only camera access.

## Privacy notice

The app includes the ARCore disclosure in its About dialog:

> This application runs on Google Play Services for AR (ARCore), which is provided by Google LLC and governed by the Google Privacy Policy.

## Next production milestones

See `docs/ARCHITECTURE.md` and `docs/RELEASE_CHECKLIST.md`. The highest-value next items are multi-device field calibration, far-end verification, depth-based ground conformance/occlusion, ARCore Recording datasets for regression tests, signed Play builds, crash telemetry with consent, accessibility/localization and automated physical-device testing.
