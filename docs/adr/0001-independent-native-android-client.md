# ADR 0001: Keep Hermes Android an independent native client

## Status

Accepted

## Context

Hermes Android targets feature parity with the shipped Hermes Desktop and its
backend contract. Electron-specific behaviour needs Android-native adaptation,
and unrelated upstream Android implementations can introduce incompatible
architecture, product choices, or provenance.

## Decision

Hermes Android remains an independent Jetpack Compose client in the
`luinbytes/hermes-android` repository. The shipped Hermes Desktop and backend
contract are the parity sources. Upstream Android applications, proposals,
issues, pull requests, and implementation code are not used as product or code
inputs.

When parity needs a missing server capability, define the smallest explicit
Hermes backend contract and keep the Android fallback honest and safe until
that contract exists.

## Consequences

- Desktop outcomes are adapted to Android rather than copied screen-for-screen.
- Android architecture and dependencies are selected for this repository.
- Backend blockers remain visible instead of being hidden behind unreliable
  client workarounds.
- Reviews must reject code or design copied from an upstream Android client.
