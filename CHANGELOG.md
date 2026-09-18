# Changelog

## 0.1.2

- Fix Android lint failure `PermissionImpliesUnsupportedChromeOsHardware` by explicitly declaring the generic camera feature as optional while keeping AR camera support required.
- Replace deprecated `DisplayMetrics.scaledDensity` usage with `TypedValue.applyDimension` for SP conversion.
- Keep the GitHub Actions Android SDK setup fix from 0.1.1 (`setup-android@v4`, `platform-tools` only).

## 0.1.1

- Fix GitHub Actions SDK bootstrap after removal of the legacy Android SDK `tools` package.
