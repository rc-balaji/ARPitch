# ARPitch field validation plan

The goal is to quantify real-world error instead of guessing an accuracy number.

## Ground-truth equipment

Use a certified steel tape or calibrated laser distance meter only for validation. It is not required by normal ARPitch users.

## Test matrix

Test at least:

- 5+ ARCore-certified Android phone families.
- Daylight, overcast, late-evening and floodlight conditions.
- Red soil, grass, synthetic turf and concrete.
- Highly textured surroundings and low-feature open fields.
- 4 yd, 22 yd and 24 yd baselines.
- Batting-end placement viewed from front, both sides and bowling end.
- Cold start and repeated run on the same ground.

## Per-run measurements

Record:

1. Device model / Android version / Google Play Services for AR version.
2. Selected ARPitch distance.
3. Ground-truth stump-centre distance.
4. Longitudinal error at bowling end.
5. Lateral error at bowling end.
6. Height error / visible float or ground penetration.
7. Heading error in degrees where measurable.
8. Tracking-loss events.
9. Time from launch to confident placement.
10. Average thermal state / frame rate after 5, 15 and 30 minutes.

Run at least 20 repetitions per important device/ground combination before deriving a tolerance claim.

## Acceptance gates for a public beta

Do not set these numbers until data exists. Define gates statistically, e.g. P50 / P95 longitudinal error, P95 lateral error, crash-free sessions, tracking-loss recovery rate and sustained FPS.

## Visual stability test

After locking the pitch:

- Stand at batting end and record 10 seconds.
- Walk to left side and right side.
- Walk toward bowling end.
- Turn around and view batting end.
- Return close to the original camera position.

Review frame-by-frame for screen-space sliding, sudden anchor jumps and geometry scale changes unrelated to perspective.

## Regression data

Whenever possible, capture ARCore Recording datasets for difficult sessions. Keep a small internal suite so renderer/tracking changes can be replayed before release.
