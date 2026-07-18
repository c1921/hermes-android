# Android client architecture RFC

Status: accepted for implementation

## Decision

Use a hybrid native client: Hermes REST APIs for backend-owned management data and TUI Gateway JSON-RPC/WebSocket for interactive control. Add a small, general upstream mobile-session extension later for replay cursors, native OAuth exchange and push delivery. Do not represent the app itself as a messaging platform adapter.

## Alternatives

| Architecture | Coverage | Background/push | Security and complexity | Upstream fit | Decision |
| --- | --- | --- | --- | --- | --- |
| Native client directly to `hermes serve` | Strong for chat and REST management | No push contract; socket is foreground-oriented | Smallest trust boundary; remote TLS/auth required | Desktop already uses these seams | Foundation |
| Android messaging gateway adapter | Good asynchronous delivery, weak full-client management | Natural delivery adapter but requires FCM/device semantics | Adds registration, revocation, queues and another identity surface | Gateway adapters model chat platforms, not rich control clients | Rejected as primary |
| Hybrid full client plus mobile gateway component | Full interactive coverage with a path to push | Push actions can resolve approvals without a permanent socket | More backend work, but boundaries are explicit | Generalises to future iOS/web clients | Chosen target |
| Local Hermes hosted inside the APK | Potentially broad | Expensive, fragile under Android process limits | Python/native dependencies, arbitrary tools and filesystem access are a large sandbox mismatch | High maintenance burden | Rejected |
| Termux companion/runtime | Real local Hermes and operator control | Termux manages its own process; notifications possible via Termux APIs | Cross-app trust, install and permission complexity | Upstream already supports Termux | Optional later integration |
| OpenAI-compatible API-only client | Text generation only | No structured agent delivery | Simple but loses sessions, tools, approvals, skills and management | Not Desktop parity | Rejected |

## Component boundaries

- `security`: Keystore credential encryption and future biometric policy.
- `network`: endpoint validation and typed REST access. No UI calls networking directly.
- `protocol`: JSON-RPC frames, WebSocket lifecycle and typed method/event models.
- `data`: backend registry, session reconciliation and repository orchestration.
- `domain`: deterministic timeline reducer and capability decisions.
- `platform`: bounded Android intent/share ingestion; untrusted payloads become draft input only and never authorize a Hermes action.
- `ui`: immutable state rendering and user intent.

Backend truth wins. Local data stores connection metadata, encrypted credentials, drafts and bounded caches; it does not become a second session database.

## Identity and reconciliation

Hermes distinguishes stored/durable session IDs from live runtime IDs. Android keeps both and translates only at the transport boundary:

- REST list/detail/navigation: durable ID.
- Gateway streaming and prompt control: runtime ID returned by `session.resume` or `session.create`.
- Profile is part of every cache and navigation key.

`tool_id` and request IDs are stable event identities. Message-stream identity is currently scoped to `(runtime session, local generation)` because upstream emits no replay cursor. After reconnect, Android must re-run `session.resume`, hydrate authoritative history, then merge only stable pending items. Exact delta replay is blocked on the upstream sequence proposal.

## Connection lifecycle

1. Validate the endpoint policy.
2. Probe `/api/status`.
3. Open `/api/ws` and require the WebSocket handshake.
4. Load sessions and capability surfaces.
5. Resume the selected durable session.
6. On network loss, enter a bounded exponential reconnect state, minting a fresh OAuth ticket when applicable.
7. Rehydrate REST history and reconcile before accepting new input.

No permanent foreground service is part of this architecture. Background completion and approval notifications require server push registration; polling or holding an immortal socket under Doze is not an acceptable substitute.

## Compatibility

Unknown JSON fields and event types are retained at the boundary or ignored safely. UI is capability-gated. Missing endpoints disable only their feature and show the required Hermes version. The initial source contract is pinned to Hermes `0.18.2`; the supported-version range will be declared only after real contract tests run against older releases.
