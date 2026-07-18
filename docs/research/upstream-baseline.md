# Upstream baseline

Latest verification: **2026-07-18** (Europe/London)

This document records the upstream contract audited for the Android client. It describes only behavior present in the pinned upstream source, except where an item is explicitly labeled as an open issue, open pull request, or inference.

## Pinned source

| Component | Authoritative source | Audited revision/version |
| --- | --- | --- |
| Hermes Agent repository | [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent) | [`5122ddd478143a6901bb752cf8ebcd1c5154b6da`](https://github.com/NousResearch/hermes-agent/commit/5122ddd478143a6901bb752cf8ebcd1c5154b6da) |
| Hermes Python package | [`pyproject.toml:8-20`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/pyproject.toml#L8-L20), [`hermes_cli/__init__.py:17-18`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/hermes_cli/__init__.py#L17-L18) | `0.18.2`, release date `2026.7.7.2` |
| Hermes Desktop | [`apps/desktop/package.json:1-11`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/apps/desktop/package.json#L1-L11) | `0.17.0` |
| Full-client transport | [`tui_gateway/server.py`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/tui_gateway/server.py), [`tui_gateway/ws.py`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/tui_gateway/ws.py) | Same commit |

The audited commit was the fetched `origin/main` tip at `2026-07-17T23:04:33Z`. The existing upstream checkout at `/home/lu/.hermes/hermes-agent` was clean and pointed to `git@github.com:NousResearch/hermes-agent.git`. It was fetched without checking out or modifying its working tree. Source inspection used the detached worktree `/tmp/hermes-agent-audit-5122ddd47`.

The Desktop appearance contract was rechecked separately against fetched `origin/main` commit [`5988fe6cd5547d3620df1de889ac6007f5463b4d`](https://github.com/NousResearch/hermes-agent/commit/5988fe6cd5547d3620df1de889ac6007f5463b4d) on 18 July 2026. `apps/desktop/src/themes/presets.ts` still exposes the six built-in presets and `apps/desktop/src/themes/context.tsx` remains authoritative for dark-only light-palette synthesis. This targeted recheck does not replace the pinned full protocol audit above.

The Desktop message and artifact action contract was rechecked against fetched `origin/main` commit [`e45d12642d5d0753e492d51be17cccf687aa8b06`](https://github.com/NousResearch/hermes-agent/commit/e45d12642d5d0753e492d51be17cccf687aa8b06) on 18 July 2026. Completed assistant messages retain an always-mounted copy action in `assistant-message.tsx`; `markdown-text.tsx` routes supported links through the constrained Desktop external-link bridge; and the artifact surface provides copy and native-open outcomes. Android preserves those outcomes through explicit touch actions and adds the platform share sheet as a mobile adaptation. This targeted recheck does not replace the pinned full protocol audit above.

No Hermes backend was started for this baseline refresh. Runtime behavior is not claimed unless it is represented by upstream source or tests. The official documentation was checked against source, but source controls where they disagree.

## Relevant implementation areas

Paths below are relative to the pinned upstream checkout.

| Concern | Source entry points | Android relevance |
| --- | --- | --- |
| Desktop product surface | `apps/desktop/src/app`, `apps/desktop/src/components`, `apps/desktop/src/store` | First-party parity reference for chat, sessions, tools, approvals, settings, skills, cron, notifications, and multi-profile state. |
| Desktop backend and transport wiring | `apps/desktop/electron/main.ts`, `apps/desktop/electron/connection-config.ts`, `apps/desktop/src/hermes.ts` | Remote URL resolution, token versus cookie auth, WebSocket ticket minting, and REST calls. |
| Shared JSON-RPC client | `apps/shared/src/json-rpc-gateway.ts` | Frame parsing, request timeouts, connection states, and the known event-name set. |
| `hermes serve` dashboard/backend | `hermes_cli/web_server.py` | `/api/ws`, `/api/status`, management REST routes, file routes, profiles, skills, cron, models, voice, diagnostics, and auth middleware. |
| Full-client protocol implementation | `tui_gateway/server.py`, `tui_gateway/ws.py`, `tui_gateway/transport.py` | JSON-RPC methods and streamed events used by Desktop and suitable for a native full client. |
| Dashboard authentication | `hermes_cli/dashboard_auth/`, `plugins/dashboard_auth/` | OAuth/OIDC and password providers, HttpOnly session cookies, refresh, logout, and single-use WebSocket tickets. |
| Session persistence and lifecycle | `hermes_state.py`, `tui_gateway/server.py`, `docs/session-lifecycle.md` | Stored versus live session identity, resume, branch, compression, history, interrupt, and profile ownership. |
| OpenAI-compatible API server | `gateway/platforms/api_server.py` | Separate HTTP/SSE integration surface with session, run, approval, and stop endpoints. It is not the Desktop protocol. |
| ACP | `acp_adapter/`, `hermes_cli/subcommands/acp.py` | Stdio protocol for editor clients. Useful as a behavior reference, not as an Android network transport. |
| Messaging gateway | Desktop `apps/desktop/src/app/messaging/index.tsx`; `gateway/run.py`, `gateway/platforms/`, `plugins/platforms/`, `gateway/session.py`; messaging and gateway-restart routes in `hermes_cli/web_server.py` | Asynchronous third-party messaging adapters and delivery. The Dashboard exposes a dynamic, profile-scoped platform catalogue plus allow-listed credential/config mutation, test and restart operations. A platform adapter is not a full native-client contract. |
| Profiles | `hermes_cli/profiles.py`, profile routes in `hermes_cli/web_server.py`, profile handling in `tui_gateway/server.py` | Profile-scoped configuration and sessions, including a currently open WebSocket profile-routing bug. |
| Skills, MCP, and plugins | `skills/`, `hermes_cli/skills_hub.py`, `tools/mcp_*`, `plugins/` | Discovery, review, installation, enablement, and reload surfaces used by Desktop. |
| Cron and background work | `cron/`, cron routes in `hermes_cli/web_server.py`, delegation methods in `tui_gateway/server.py` | Scheduled work, background tasks, subagents, and delivery state. |
| Voice | Desktop `apps/desktop/src/hermes.ts`, `lib/voice-playback.ts`, `lib/speech-text.ts`; `hermes_cli/voice.py`, `tools/voice_mode.py`, `tools/tts_tool.py`, audio routes in `hermes_cli/web_server.py` | `/api/audio/transcribe` accepts recorded audio. `/api/audio/speak` calls Hermes' configured `text_to_speech_tool` provider chain, removes its temporary server file, and returns a base64 audio data URL for client playback. |
| Android/Termux support | `pyproject.toml:230-250`, `constraints-termux.txt`, `scripts/install.sh`, `hermes_constants.py` | Official Tier 2 local-runtime path and its dependency exclusions. |
| Contract tests | `tests/tui_gateway/`, `tests/test_tui_gateway_ws.py`, `tests/hermes_cli/test_dashboard_auth_*`, `apps/desktop/**/*.test.*` | Primary evidence for protocol, auth, reconnect, and Desktop behavior. |

Official overviews: [Desktop App](https://hermes-agent.nousresearch.com/docs/user-guide/desktop), [Programmatic Integration](https://hermes-agent.nousresearch.com/docs/developer-guide/programmatic-integration), [Web Dashboard](https://hermes-agent.nousresearch.com/docs/user-guide/features/web-dashboard), and [Android / Termux](https://hermes-agent.nousresearch.com/docs/getting-started/termux).

## Protocol entry points

### Full-client protocol: TUI gateway JSON-RPC

The first-party full-client contract is JSON-RPC 2.0 over WebSocket at `GET /api/ws`. The same dispatcher can run over stdio. [`tui_gateway/ws.py:1-21`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/tui_gateway/ws.py#L1-L21) states that WebSocket and stdio use identical JSON-RPC frames and share every method, slash command, approval flow, and agent event.

On connect, the server accepts the socket and emits `gateway.ready`. At this commit its payload contains only `skin`; it does not advertise a protocol version, schema version, server instance, capability set, or authorization grant. See [`tui_gateway/ws.py:283-328`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/tui_gateway/ws.py#L283-L328).

The audit counted **117** unique `@method(...)` registrations in `tui_gateway/server.py`. Important groups include:

- Conversation: `session.create`, `session.list`, `session.resume`, `session.history`, `session.undo`, `session.compress`, `session.branch`, `session.interrupt`, `session.steer`, `prompt.submit`, `prompt.background`.
- Human input and safety: `approval.respond`, `clarify.respond`, `sudo.respond`, `secret.respond`.
- Attachments: `image.attach`, `image.attach_bytes`, `image.detach`, `pdf.attach`, `file.attach`.
- Discovery and configuration: `model.options`, `config.get`, `config.set`, `commands.catalog`, `complete.slash`, `skills.manage`, `reload.mcp`, `tools.list`, `toolsets.list`.
- Automation and agents: `cron.manage`, `agents.list`, `delegation.status`, `subagent.interrupt`, `spawn_tree.list`.
- Recovery and inspection: `rollback.list`, `rollback.diff`, `rollback.restore`, `session.usage`, `session.context_breakdown`.

Events are JSON-RPC notifications with method `event` and params containing `type`, `session_id`, and optional `payload`. This exact envelope is built at [`tui_gateway/server.py:1119-1144`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/tui_gateway/server.py#L1119-L1144). Desktop's shared client recognizes message, reasoning, tool, approval, clarification, secret, background, error, and lifecycle events while intentionally accepting unknown event names at [`apps/shared/src/json-rpc-gateway.ts:1-41`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/apps/shared/src/json-rpc-gateway.ts#L1-L41).

The official [Programmatic Integration](https://hermes-agent.nousresearch.com/docs/developer-guide/programmatic-integration) guide explicitly recommends this protocol for custom hosts that need sessions, slash commands, approvals, branching, multi-agent behavior, and streaming events.

### Dashboard REST API

Desktop combines the WebSocket with REST from `hermes_cli/web_server.py`. REST supplies status/version, profiles, aggregate session lists, config/schema, models/providers, skills and Skill Hub, MCP, cron, messaging configuration, analytics, logs, diagnostics, file transfer, voice, and operational actions.

Delegation control is already a typed gateway surface. `delegation.status` returns the active registry plus `paused`, `max_spawn_depth` and `max_concurrent_children`; `delegation.pause` changes only whether future delegate calls may spawn; and `subagent.interrupt` cooperatively stops one advertised child. Live detail arrives through `subagent.spawn_requested`, `subagent.start`, `subagent.thinking`, `subagent.tool`, `subagent.progress` and `subagent.complete`. Background processes are separate: `process.list` is scoped by runtime session and returns an output tail, while `process.kill` verifies the process belongs to that session before terminating it. `spawn_tree.list/load` provides server-persisted completed trees, but is not required for live intervention.

`GET /api/status` is always public and currently returns the Hermes package version, release date, config versions, gateway lifecycle, active work, `auth_required`, and `auth_providers`; see [`hermes_cli/web_server.py:2752-2775`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/hermes_cli/web_server.py#L2752-L2775). This is useful for connection bootstrap, but it is not a versioned method/event capability document.

### OpenAI-compatible API server

`gateway/platforms/api_server.py` is a separate gateway platform adapter authenticated by `API_SERVER_KEY`. It exposes OpenAI Chat Completions, Responses, persisted sessions, asynchronous runs, run events, approvals, interruption, skills, toolsets, jobs, health, and capability endpoints. The route list is authoritative at [`gateway/platforms/api_server.py:1480-1518`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/gateway/platforms/api_server.py#L1480-L1518).

This API is useful for interoperable chat and automation, but it does not expose all Desktop management or live JSON-RPC behavior. It should not be treated as the sole first-party parity transport for Android.

### ACP

`hermes acp` starts the `acp_adapter` over JSON-RPC stdio. The adapter covers editor sessions, streaming, tools, permissions, models, MCP, history, and cancellation; its entry point is [`hermes_cli/main.py:13105-13125`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/hermes_cli/main.py#L13105-L13125). ACP is intentionally editor-oriented and local-process-oriented. There is no first-party ACP-over-network endpoint for a native Android app.

### Messaging adapters

Messaging integrations inherit `BasePlatformAdapter` and feed `MessageEvent` instances through the long-running gateway. The official adapter guide lists required send/connect methods and optional interactive approval/clarification controls at [`gateway/platforms/ADDING_A_PLATFORM.md:87-144`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/gateway/platforms/ADDING_A_PLATFORM.md#L87-L144).

This architecture is appropriate for Telegram, Discord, Slack, WhatsApp, and similar intermediaries. It does not provide a native client with the full session, configuration, file, skill, model, cron, and diagnostics surfaces. Android should not be modeled as a messaging adapter unless a concrete asynchronous delivery capability requires one.

## Authentication paths

### Local or explicitly insecure mode

- REST uses `X-Hermes-Session-Token`; the legacy `Authorization: Bearer <token>` form remains accepted. See [`hermes_cli/web_server.py:324-384`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/hermes_cli/web_server.py#L324-L384).
- `/api/ws` uses `?token=<session-token>`. The WebSocket handler does not accept an authorization header for this mode. See [`hermes_cli/web_server.py:15212-15293`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/hermes_cli/web_server.py#L15212-L15293).
- Desktop constructs the same token URL at [`apps/desktop/electron/connection-config.ts:75-89`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/apps/desktop/electron/connection-config.ts#L75-L89).

Query credentials can be recorded by reverse proxies or access logs. Operators must use TLS and redact query strings if this mode is exposed beyond loopback. The official Desktop documentation recommends password auth only on a trusted LAN or VPN and OAuth for public exposure.

### Gated dashboard mode

Non-loopback `hermes serve` engages the dashboard auth gate. Registered providers can implement OAuth/OIDC sessions, username/password sessions, or request-scoped bearer authentication.

- Provider discovery: `GET /api/auth/providers` at [`dashboard_auth/routes.py:152-174`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/hermes_cli/dashboard_auth/routes.py#L152-L174).
- OAuth/OIDC start and callback: `GET /auth/login` and `GET /auth/callback` at [`dashboard_auth/routes.py:182-370`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/hermes_cli/dashboard_auth/routes.py#L182-L370).
- Password login: `POST /auth/password-login` accepts provider, username, password, and optional next path, then sets the same session cookies as OAuth. It includes a process-local rate limit and generic errors to avoid username enumeration. See [`dashboard_auth/routes.py:425-555`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/hermes_cli/dashboard_auth/routes.py#L425-L555).
- Identity and logout: `GET /api/auth/me`, `POST /auth/logout`.
- WebSocket ticket: authenticated clients call `POST /api/auth/ws-ticket`, then connect to `/api/ws?ticket=...`. Tickets are in-memory, single-use, and expire after 30 seconds. See [`dashboard_auth/routes.py:594-644`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/hermes_cli/dashboard_auth/routes.py#L594-L644) and [`dashboard_auth/ws_tickets.py:1-99`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/hermes_cli/dashboard_auth/ws_tickets.py#L1-L99).

Desktop completes browser login inside the persistent Electron partition `persist:hermes-remote-oauth`, sends REST through Electron's cookie-aware network stack, and mints a fresh ticket immediately before every WebSocket connection. See [`apps/desktop/electron/main.ts:4989-5026`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/apps/desktop/electron/main.ts#L4989-L5026) and [`apps/desktop/electron/main.ts:5377-5417`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/apps/desktop/electron/main.ts#L5377-L5417).

Nous Portal is one dashboard auth provider. Its browser session is not a distinct native Android token API in current `main`.

## Live Android validation against an operator backend

On 18 July 2026, the `0.1.0-dev` debug client was installed on a Samsung SM-S906E running Android 16 and tested against the operator's personal Hermes Dashboard `0.18.0`. Live service inspection first confirmed that `hermes-dashboard.service` and `hermes-gateway.service` both used `HERMES_HOME=/home/discord/.hermes`; the sibling iniuria gateway remained isolated under `/home/discord/.hermes-iniuria`. No service was restarted and no backend configuration was changed.

The Android client completed password login, authenticated status validation, fresh single-use WebSocket ticket minting, `/api/ws` upgrade, and real session hydration. One new-session message was submitted with the required Hermes Android production-QA preamble and an explicit instruction that no work or response was required. The message was accepted, the composer returned to idle, and the agent performed no work. Reinstalling the same package with `adb install -r` preserved the Keystore-backed session and reconnected without another password entry.

The network path was then removed while the authenticated app was open. Android surfaced bounded reconnect attempts without discarding the saved backend. Restoring the path returned the client to `LIVE / JSON-RPC`. A stale reconnect warning discovered during this test was fixed at the successful-reconnect state boundary and the same cut-and-recover sequence proved that the warning now clears.

The temporary SSH tunnel, host-rewriting proxy, ADB port reversals, device test files, and local proxy files were removed after validation. The new QA session was archived from the client; a read-only search of the personal Hermes database found no persisted message or session matching the QA marker. Credentials, cookies, ticket values, message content, and host addresses are not recorded in repository artifacts.

## Current limitations affecting Android

1. **No versioned full-client handshake.** `gateway.ready` contains only `skin`. Android can read the package version from `/api/status`, but current `main` does not negotiate a protocol/schema version, per-method capability set, or compatibility range before dispatch.

2. **No attenuated native-client grant.** A normal gated WebSocket ticket authenticates the connection but does not scope the 117-method dispatcher. There is no server-enforced mobile method/parameter grant on current `main`. [Issue #62857](https://github.com/NousResearch/hermes-agent/issues/62857) and draft [PR #62858](https://github.com/NousResearch/hermes-agent/pull/62858) propose this, but the code is not in the audited commit.

3. **No universal event cursor or replay contract.** `_emit` sends only type, session ID, and payload. Current `main` has no monotonic stream revision, resume cursor, gap/reset signal, or durable mutation receipt for the TUI gateway. A client can rehydrate authoritative session history after reconnect, but it cannot generically prove exactly-once display or mutation semantics for in-flight deltas, prompts, or approvals. Draft PRs [#63149](https://github.com/NousResearch/hermes-agent/pull/63149), [#63190](https://github.com/NousResearch/hermes-agent/pull/63190), [#63197](https://github.com/NousResearch/hermes-agent/pull/63197), and [#63205](https://github.com/NousResearch/hermes-agent/pull/63205) are an unmerged stack for revisioned sync, mutation receipts, recoverable approvals, and conformance.

4. **Browser OAuth has no native-app handoff in current `main`.** Desktop relies on an Electron-owned persistent cookie partition. Android Custom Tabs and an app HTTP/WebSocket client do not share one cookie jar, so reproducing Desktop's flow requires an explicit secure handoff or another upstream-supported native flow. Open [PR #42655](https://github.com/NousResearch/hermes-agent/pull/42655) proposes a PKCE-bound one-time handoff, but it is not part of this baseline.

5. **No first-party Android push registration or delivery contract.** The audit found Desktop-local native notifications, but no FCM device registration, revocation, delivery acknowledgement, notification action, or deep-link delivery API in the server. Foreground WebSocket events do not solve Android background suspension.

6. **Profile routing is incomplete on the WebSocket surface.** `session.create` accepts `params.profile`, but its immediate `info.profile_name` still comes from `_current_profile_name()` at [`tui_gateway/server.py:5519-5660`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/tui_gateway/server.py#L5519-L5660), and `session.list` reads `_get_db()` without selecting `params.profile` at [`tui_gateway/server.py:5665-5706`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/tui_gateway/server.py#L5665-L5706). Open [issue #62503](https://github.com/NousResearch/hermes-agent/issues/62503) tracks the broader affected method set. Android must not claim reliable cross-profile WebSocket behavior until fixed or worked around through authoritative profile REST routes.

7. **Reloaded messages lack stable client identity metadata.** `_history_to_messages` converts persisted history into role, text, reasoning, and limited tool context, but does not return a stable message ID or timestamp; see [`tui_gateway/server.py:5258-5315`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/tui_gateway/server.py#L5258-L5315). Open [issue #31945](https://github.com/NousResearch/hermes-agent/issues/31945) requests this metadata. Android therefore cannot reliably reconcile optimistic or streamed message rows with reloaded history by server identity.

8. **A live session has one mutable event transport, not a multi-client subscription set.** `prompt.submit` rebinds `session["transport"]` to the request's current client before the turn runs at [`tui_gateway/server.py:8841-8857`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/tui_gateway/server.py#L8841-L8857), while session events route to the stored transport. Open [issue #55564](https://github.com/NousResearch/hermes-agent/issues/55564) reports one connected client taking over another client's live stream. Android must treat concurrent Desktop, web, and mobile control of the same live session as unsafe until upstream defines fanout or ownership semantics.

9. **Authenticated dashboard identity is not yet a proven agent-isolation boundary.** Open [issue #62549](https://github.com/NousResearch/hermes-agent/issues/62549) reports that the authenticated principal does not reach agent construction and requests user-scoped storage and execution boundaries. This is an issue report, not a source-proven exploit in this audit, but Android must not infer multi-user data isolation merely from a valid dashboard cookie or WebSocket ticket.

10. **Structured tool-event coverage is not uniform across backends.** Open [issue #66360](https://github.com/NousResearch/hermes-agent/issues/66360) reports that Codex app-server tool lifecycle events are visible in terminal output but not emitted through `hermes serve` WebSocket streaming. Until the associated fix ships and is exercised, Android needs graceful fallback rendering and must not assume every tool backend produces the same structured event sequence.

11. **The OpenAI-compatible API is not Desktop parity.** It has good chat/run/SSE interoperability and a machine-readable capability endpoint, but it does not replace the Dashboard REST plus TUI gateway combination for full product management and live client controls. Its own open work includes durable approval/stop state, correlated session SSE, steering, image inputs, model override, and compaction.

12. **Termux is an optional Tier 2 runtime, not an embeddable Android service contract.** The official [Android / Termux](https://hermes-agent.nousresearch.com/docs/getting-started/termux) guide supports CLI, cron, PTY/background terminal, Telegram, MCP, Honcho, and ACP through `.[termux]`. It explicitly excludes `.[all]`, local `faster-whisper` voice, automatic browser bootstrap, and Docker isolation, and warns that Android may suspend background jobs. The exact extras are defined at [`pyproject.toml:230-250`](https://github.com/NousResearch/hermes-agent/blob/5122ddd478143a6901bb752cf8ebcd1c5154b6da/pyproject.toml#L230-L250). A separately sandboxed Android app also cannot control Termux safely without an explicit companion protocol.

13. **Several Termux paths remain open upstream.** Hardcoded `/tmp`, non-systemd gateway restart, cloud-browser setup, and Termux voice detection have open issues or pull requests listed below. These are relevant only to an optional local-runtime mode, not to a native app connected to a remote `hermes serve` instance.

## Open upstream work affecting Android

Open state was verified against the official repository on 2026-07-18 with GitHub CLI. Issue reports and pull-request claims are signals, not shipped behavior. None of the pull requests below is present in the audited commit.

### Issues

| Upstream item | Android impact |
| --- | --- |
| [#35966 Native desktop/mobile client app](https://github.com/NousResearch/hermes-agent/issues/35966) | Requests a first-party client using the existing Gateway/API Server, with shared sessions, skills, memory, push, and proper files/links. |
| [#60124 Native Android companion app](https://github.com/NousResearch/hermes-agent/issues/60124) | Android-specific product request for remote connection, chat, voice, device tools, and offline history. It is labeled duplicate upstream. |
| [#11911 Native mobile app with voice calling](https://github.com/NousResearch/hermes-agent/issues/11911) | Tracks voice, push notifications, secure login, and background operation expectations. |
| [#62857 Scoped WebSocket grants](https://github.com/NousResearch/hermes-agent/issues/62857) | Identifies the missing versioned handshake and least-privilege authorization contract for native clients. |
| [#62503 `session.*` ignores `params.profile`](https://github.com/NousResearch/hermes-agent/issues/62503) | Multi-profile clients can read or write the launch profile database for several methods. |
| [#55564 `prompt.submit` hijacks session transport](https://github.com/NousResearch/hermes-agent/issues/55564) | Reports that the newest submitting client becomes the live session's event target, preventing safe concurrent Desktop, web, and Android observation. |
| [#31945 stable message metadata](https://github.com/NousResearch/hermes-agent/issues/31945) | Requests server message IDs and timestamps so reconnecting clients can reconcile streamed and persisted history. |
| [#62549 authenticated identity isolation](https://github.com/NousResearch/hermes-agent/issues/62549) | Requests propagation of the dashboard principal into agent construction and user-scoped execution/storage boundaries. |
| [#66360 Codex tool events missing from WebSocket](https://github.com/NousResearch/hermes-agent/issues/66360) | Reports missing structured tool lifecycle events for Codex app-server sessions reached through `hermes serve`. |
| [#52002 cross-session notifications](https://github.com/NousResearch/hermes-agent/issues/52002) | Tracks notification fanout and catch-up across sessions, relevant to mobile background delivery but not an FCM device contract by itself. |
| [#58853 persisted SSE approval and stop](https://github.com/NousResearch/hermes-agent/issues/58853) | Tracks durable approval and stop control for asynchronous API-server runs. |
| [#38994 session SSE with correlated tools](https://github.com/NousResearch/hermes-agent/issues/38994) | Tracks resumable session event streaming with tool-call correlation on the OpenAI-compatible API. |
| [#54301 steer API-server runs](https://github.com/NousResearch/hermes-agent/issues/54301) | Tracks steering a running API-server session instead of starting a competing turn. |
| [#26504 image input through the API server](https://github.com/NousResearch/hermes-agent/issues/26504) | Tracks image attachment support on the interoperable HTTP surface. |
| [#56835 remote Desktop crash after network resume](https://github.com/NousResearch/hermes-agent/issues/56835) | Desktop-specific report, but it highlights the reconnect behavior Android must independently handle around sleep and network changes. |
| [#52415 hardcoded `/tmp` on Termux](https://github.com/NousResearch/hermes-agent/issues/52415) | Breaks local terminal, code-execution, process, browser, MCP, and ACP paths on Android where `$TMPDIR` is the usable temp path. |
| [#29603 non-systemd gateway restart on Termux](https://github.com/NousResearch/hermes-agent/issues/29603) | A local Android gateway can die during restart instead of detaching cleanly. |
| [#51237 browser setup fails on Termux](https://github.com/NousResearch/hermes-agent/issues/51237) | Cloud browser setup still attempts unsupported local Chromium work on Android. |
| [#25657 Android TTS via Termux:API](https://github.com/NousResearch/hermes-agent/issues/25657) | Requests a lightweight Android-native TTS fallback because the desktop voice dependency chain is unavailable. |

### Pull requests

| Upstream item | Status on 2026-07-18 | Potential effect |
| --- | --- | --- |
| [#62858 scoped WebSocket grants](https://github.com/NousResearch/hermes-agent/pull/62858) | Open draft | Adds a proposed `hermes.mobile` audience, explicit scopes, handshake metadata, and a fail-closed method/parameter policy. |
| [#63149](https://github.com/NousResearch/hermes-agent/pull/63149), [#63190](https://github.com/NousResearch/hermes-agent/pull/63190), [#63197](https://github.com/NousResearch/hermes-agent/pull/63197), [#63205](https://github.com/NousResearch/hermes-agent/pull/63205) | Open drafts, stacked | Propose revisioned replay, durable mutation receipts, recoverable approvals, and a mobile contract conformance test. |
| [#42655 mobile OAuth handoff](https://github.com/NousResearch/hermes-agent/pull/42655) | Open | Proposes a short-lived PKCE-bound browser-to-app handoff that yields normal dashboard session cookies. |
| [#43773 gateway protocol reference](https://github.com/NousResearch/hermes-agent/pull/43773) | Open | Documents WebSocket, REST, auth, and session identity for external client authors. Documentation does not change the contract. |
| [#62509 profile-scoped session dispatch](https://github.com/NousResearch/hermes-agent/pull/62509) | Open | Proposes routing the affected `session.*` methods through the requested profile and correcting returned profile metadata. |
| [#55571 preserve session transport](https://github.com/NousResearch/hermes-agent/pull/55571) | Open | Proposes an opt-in `keep_transport` parameter so an external submitter can leave the existing event target intact. It does not add multi-client fanout. |
| [#66402 Codex tool-event relay](https://github.com/NousResearch/hermes-agent/pull/66402) | Open | Proposes forwarding Codex app-server tool lifecycle events through the TUI gateway event stream. |
| [#58856](https://github.com/NousResearch/hermes-agent/pull/58856), [#38997](https://github.com/NousResearch/hermes-agent/pull/38997), [#54466](https://github.com/NousResearch/hermes-agent/pull/54466), [#26695](https://github.com/NousResearch/hermes-agent/pull/26695) | Open | Proposed API-server persisted controls, correlated session SSE, steering, and image inputs. These are secondary transport improvements, not full Desktop parity. |
| [#49834 Capacitor Android thin client](https://github.com/NousResearch/hermes-agent/pull/49834) | Open | A concept/thin-client implementation. It is not present in `main` and is not evidence of official Android support. |
| [#65668 portable temp directory](https://github.com/NousResearch/hermes-agent/pull/65668) | Open | Replaces hardcoded `/tmp` in affected local-runtime paths. |
| [#29619 detached gateway restart](https://github.com/NousResearch/hermes-agent/pull/29619) | Open | Proposes a Python watcher so a Termux/non-systemd restart survives shell exit. |
| [#44390 Termux psutil manual install](https://github.com/NousResearch/hermes-agent/pull/44390) | Open | Aligns the documented manual install with the existing Android psutil compatibility shim. |

## Recent upstream history

The previous baseline pinned `0f102fa4dc04b7dfdab048169aaaa640d09d7523`. Current `origin/main` is 120 commits later. Package versions remain Hermes `0.18.2` and Desktop `0.17.0`, so version strings alone do not identify the audited contract.

Relevant changes between those commits include:

- [`11d36232c`](https://github.com/NousResearch/hermes-agent/commit/11d36232c): fixes Desktop interrupt targeting the wrong session and stale events restoring busy state.
- [`ba542338e`](https://github.com/NousResearch/hermes-agent/commit/ba542338e): scopes fast mode and surfaces profile ownership/model overrides in Desktop.
- [`81a140266`](https://github.com/NousResearch/hermes-agent/commit/81a140266): prewarms profile gateway sockets.
- [`9fc0074ba`](https://github.com/NousResearch/hermes-agent/commit/9fc0074ba): unifies gateway reset and recovery boundaries.
- [`05dea7be0`](https://github.com/NousResearch/hermes-agent/commit/05dea7be0) through [`4dc2b7be0`](https://github.com/NousResearch/hermes-agent/commit/4dc2b7be0): adds and hardens hosted dashboard MCP OAuth lifecycle behavior.
- [`5122ddd47`](https://github.com/NousResearch/hermes-agent/commit/5122ddd478143a6901bb752cf8ebcd1c5154b6da): passes the TUI Python environment through dashboard chat.

These changes reinforce that Android compatibility checks must use the exact commit and feature probes, not only the package version.

## Baseline conclusion for Android architecture

The source-supported full-client path is a native Android client connected to a remote `hermes serve` backend using:

1. Dashboard REST for bootstrap, authentication, management, discovery, files, and operational data.
2. `/api/ws` TUI gateway JSON-RPC for live conversation, streaming, tools, approvals, clarification, interruption, slash commands, and session control.

This is an evidence-based inference from the same split used by Hermes Desktop and from the official programmatic-integration guidance. The OpenAI-compatible API remains a useful secondary surface, and messaging gateway integration remains useful for asynchronous delivery, but neither alone provides Desktop parity. Local Hermes through Termux is optional and should remain a separately consented companion mode until upstream defines a safe cross-app lifecycle contract.

The main upstream changes needed for a production Android client are not a new chat transport. They are a versioned and scoped client contract, reconnect replay and idempotency, stable message identity, durable addressable approvals, safe multi-client event fanout, a supported native OAuth handoff, authenticated-user isolation, structured tool-event parity, push-device delivery, and correction of profile-scoped session dispatch.

## Reproduction commands

```bash
git -C /home/lu/.hermes/hermes-agent remote -v
git -C /home/lu/.hermes/hermes-agent status --short --branch
git -C /home/lu/.hermes/hermes-agent fetch --prune origin
git -C /home/lu/.hermes/hermes-agent rev-parse origin/main
git -C /home/lu/.hermes/hermes-agent log -1 --format='%H%n%cI%n%s' origin/main
git -C /home/lu/.hermes/hermes-agent worktree add --detach /tmp/hermes-agent-audit-5122ddd47 origin/main

rg -n '^version\s*=|__version__|"version"' \
  pyproject.toml hermes_cli/__init__.py apps/desktop/package.json
rg -o '@method\("[^"]+"\)' tui_gateway/server.py | sort -u | wc -l
git rev-list --count \
  0f102fa4dc04b7dfdab048169aaaa640d09d7523..5122ddd478143a6901bb752cf8ebcd1c5154b6da

gh issue view 62857 --repo NousResearch/hermes-agent \
  --json number,title,url,state,updatedAt,body,labels
gh pr view 62858 --repo NousResearch/hermes-agent \
  --json number,title,url,state,isDraft,mergeStateStatus,updatedAt,body,files
```
