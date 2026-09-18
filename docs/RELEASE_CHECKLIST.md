# Production release checklist

## Tracking & geometry

- [ ] Validate 4 yd / 22 yd / 24 yd conversion against physical ground truth.
- [ ] Verify world lock from batting, bowling and both side views.
- [ ] Verify no pitch geometry is tied to screen-space pixels.
- [ ] Test reset / re-place at least 50 times without anchor leaks.
- [ ] Test tracking loss and recovery.
- [ ] Test surface placement on plane, DepthPoint and oriented-point fallback paths.

## Device coverage

- [ ] Pixel flagship + midrange.
- [ ] Samsung flagship + A-series where ARCore supported.
- [ ] OnePlus / Oppo / Vivo representative supported devices.
- [ ] 60 Hz / 90 Hz / 120 Hz displays.
- [ ] Wide / ultrawide aspect ratios.
- [ ] Android versions supported by Play distribution target.

## Performance

- [ ] 30-minute thermal soak outdoors.
- [ ] Monitor FPS and frame-time spikes.
- [ ] Verify no per-frame large allocations in profiler.
- [ ] Verify GL context recreation after app background/foreground.
- [ ] Verify camera is released on pause.

## Product safety / claims

- [ ] No unsupported centimeter-level accuracy claim.
- [ ] ARCore privacy disclosure visible.
- [ ] Explain tracking-quality requirements to user.
- [ ] Add explicit field-test evidence before publishing tolerance numbers.

## Build / delivery

- [ ] CI green: lint + unit tests + debug APK.
- [ ] Release R8 build smoke-tested on device.
- [ ] Real release keystore configured outside source control.
- [ ] Play App Signing enabled.
- [ ] Version code/name updated.
- [ ] Crash reporting/privacy policy configured if telemetry is added.
