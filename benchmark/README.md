# Hermes benchmark harness

This module is the credential-free #34 harness foundation. It targets the
non-minified release app package (`com.nousresearch.hermes`) for representative measurement,
supports connected devices, and declares an API 36 Pixel 6 Gradle Managed
Device (`pixel6Api36`). CI runs it for manually dispatched evidence runs and
pull requests into `main`.

## Journeys

`HermesStartupBenchmark` records `StartupTimingMetric` (TTID and TTFD when the
app reports full display) and `FrameTimingMetric` for cold and warm startup.
`HermesSurfaceJourneyBenchmark` provides named Atlas/chat, continuous-stream
transcript scroll, composer, Files/Artifacts, and Manage frame journeys. The
non-minified release-only fixture activity renders the production Compose
surfaces with deterministic local data and no credentials or network access.

`BaselineProfileGenerator` exercises startup and the primary fixture surfaces
through the AndroidX Baseline Profile plugin. The generated profile is wired
into release builds and retained with benchmark evidence.

## Deterministic fixtures

`DeterministicFixtures` produces 500 mixed user/assistant/tool/error messages
and a 120-chunk continuous stream at a fixed 25 ms interval. The data contains
no credentials or production content, is stable across runs, and feeds the
release-only fixture built from the production timeline and renderer surfaces.

## Raw evidence format and gate

Each raw result line is represented by `BenchmarkEvidence` and contains:

```json
{
  "benchmark": "cold-start",
  "commit": "<git sha>",
  "device": "Pixel 6",
  "androidApi": 36,
  "toolchain": "<AGP / Gradle / Kotlin>",
  "profileState": "<none|baseline-profile>",
  "repetitions": 5,
  "environment": {"runner": "gmd"},
  "metrics": {"timeToInitialDisplayMs": 0.0}
}
```

`BenchmarkRegression` is the machine-checkable lower-is-better comparator:
candidate values may be at most 10% above the accepted baseline. Missing
metrics fail the comparison. `BenchmarkHarnessTest` verifies the exact 10%
boundary and rejection above it.

No physical-device result is inferred from the emulator. The accepted API 36
baseline and its 10% CI comparison are recorded from the manually dispatched
evidence run; physical reference-device results remain an owner-review gate.
