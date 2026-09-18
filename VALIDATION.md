# Validation status

Validated in the source-generation workspace:

- Android manifest and resource XML parse successfully.
- GitHub Actions / Dependabot YAML parse successfully.
- `gradlew` shell launcher passes `bash -n`.
- Pure Kotlin pitch model compiles with `kotlinc`.
- Exact 22 yd and 24 yd metric conversion smoke tests pass.
- The repository contains no generated APK/AAB, private keys, credentials, analytics SDKs, location permission, storage permission, or network permission.

## Build boundary

A full Android/ARCore Gradle build could not be executed in the source-generation workspace because the Android SDK and dependency resolver are not installed/available there. The included GitHub Actions workflow installs JDK 17 and Android SDK 36 and runs `lintDebug`, `testDebugUnitTest`, and `assembleDebug` on every push/PR. The release workflow runs release lint/tests and builds APK + AAB on `v*` tags or manual dispatch.

AR tracking behavior and metric error must be validated on physical ARCore-certified devices; an emulator cannot establish the outdoor long-baseline accuracy required by ARPitch.
