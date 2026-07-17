# Hermes for Android

Native Android client for [Nous Research Hermes Agent](https://github.com/NousResearch/hermes-agent). The project targets first-party-quality integration with the same backend, sessions, profiles, skills, memory, tools and configuration used by Hermes Desktop, CLI and TUI.

This repository is an independent work in progress and is not currently an official Nous Research release.

## Current working slice

- Manual HTTPS or explicitly approved private-IP HTTP backend connection
- Android Keystore-backed static token storage
- HTTP status validation plus a real JSON-RPC WebSocket handshake
- Unified cross-profile session list
- Session resume and new-session creation
- Streaming assistant text, reasoning and status events
- Structured tool start/completion cards
- Dangerous-command approval and clarification responses
- Stop/interruption
- SAF file, image and PDF attachments with bounded reads and server-queue cleanup
- Dynamic Hermes model catalogue with session-scoped model, reasoning, fast-mode and YOLO controls
- Session rename, branch, undo, compression, archive and live-run steering
- Phone master/detail navigation and two-pane tablet layout
- Unknown protocol-event tolerance

See [`docs/research/desktop-parity-matrix.md`](docs/research/desktop-parity-matrix.md) for the exact implemented and blocked state. Nothing marked blocked or not implemented is represented as working UI.

## Build

Requirements: JDK 17 and Android SDK 35.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Install the debug build:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Backend

Start a Hermes backend with its embedded structured chat gateway enabled. The client validates both `/api/status` and `/api/ws`; an HTTP-only success is not accepted as a working connection.

For public or untrusted networks, use HTTPS/WSS. Cleartext is rejected unless the user explicitly opts in and the host is a literal loopback, RFC1918, IPv6 ULA or Tailscale CGNAT address.

## Upstream baseline

The initial audit is pinned to Hermes Agent commit `0f102fa4dc04b7dfdab048169aaaa640d09d7523` (Hermes Agent `0.18.2`, Desktop `0.17.0`) from 17 July 2026. Upstream remains read-only from this repository; proposed protocol changes are documented locally for later review.

## Licence

MIT. See [`LICENSE`](LICENSE).
