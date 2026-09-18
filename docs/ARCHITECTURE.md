# ARPitch architecture notes

## Product invariant

The virtual pitch must behave as if it were physically painted on the ground. That requires a stable metric transform, not a UI overlay.

### Coordinate frames

- **ARCore world:** current frame's world coordinate system.
- **Anchor frame:** batting-end anchor. Its origin is the first wicket centre.
- **Pitch-local frame:** anchor frame plus one local-Y yaw used to point +Z down the pitch.
- **Pitch geometry:** all dimensions in meters.

The pitch model matrix each frame is:

```text
M_pitch = M_anchor(current frame) × R_localHeading
```

The camera matrix is:

```text
MVP = Projection(ARCore camera) × View(ARCore camera) × M_pitch
```

This is why moving farther away makes the wicket smaller automatically; no custom distance-scaling code exists.

## Placement state machine

```text
SCANNING
  └─ place valid ground hit → AIMING
AIMING
  ├─ camera heading continuously projected into anchor-local XZ plane
  ├─ 0.5° manual fine adjustment
  └─ lock → LOCKED
LOCKED
  ├─ origin + heading fixed relative to ARCore anchor
  ├─ distance may still change deterministically
  └─ reset → SCANNING
```

## Hit quality order

1. Horizontal upward-facing `Plane` hit inside its polygon.
2. `DepthPoint` when Depth is supported.
3. `Point` only when ARCore estimated a surface normal.

This gives a graceful fallback instead of making Depth mandatory.

## Rendering budget

The scene is intentionally simple:

- Camera background: 1 draw call.
- Pitch translucent ribbon: 1 draw call.
- Markings / footprints: 1 draw call.
- Wickets: 1 draw call.

Pitch geometry is uploaded as static VBO data. It changes only when distance changes. Camera movement changes only matrices.

## Stability rules

- Keep world geometry relative to an `Anchor`, never stale raw world coordinates.
- Never “correct” a locked pitch from screen pixels.
- Do not average the anchor every frame. ARCore already owns the tracking filter.
- Render the latest anchor pose each frame so ARCore map refinements remain coherent.
- Warn on tracking loss rather than silently moving the pitch.
- Keep motion smooth during scanning; aggressive movement reduces visual tracking quality.

## Long-baseline limitation

At 22 yards the far wicket is outside the high-confidence range of many depth estimates. Depth should improve local surface placement, not be treated as the sole 20 m ruler. The metric baseline comes from the ARCore world scale and deterministic geometry. Field validation must quantify device-specific drift.

## Production extensions

### 1. Far-end verification

When the user reaches the bowling end, collect a new local ground observation near the predicted far wicket and report residual error. Do not automatically distort the pitch unless the correction model is validated.

### 2. Terrain conformance

Sample raw/smoothed depth or a reconstructed surface under pitch-line vertices. Keep the metric XZ baseline rigid but allow only the display ribbon/paint to follow small Y-height changes.

### 3. Recording-based regression

Use ARCore Recording/Playback datasets to reproduce tracking sessions and compare changes to placement, heading and relocalization behavior across releases.

### 4. Device quality profiles

Build an empirical device database from field tests. A profile can tune onboarding scan duration, confidence thresholds and feature availability without changing metric geometry.

### 5. Persistent sessions

If reopening the exact pitch later is required, add a deliberate persistence design (Cloud Anchors / Geospatial / saved visual map strategy depending the use case). Do not mix persistence into the core single-session ruler until field accuracy is characterized.
