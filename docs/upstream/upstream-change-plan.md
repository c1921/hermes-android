# Upstream change plan

No upstream pull requests or issue comments will be created by this project. Changes are to be prepared locally for explicit owner review.

## 1. Resumable ordered event envelope

Existing limitation: `message.delta` and many status events have neither a server event ID nor monotonic per-run sequence. A reconnect can hydrate final history but cannot prove whether an in-flight delta was missed or duplicated.

Proposal: add an optional capability `event_replay_v1`. Event envelopes gain `run_id`, `event_id`, `sequence` and `emitted_at`. Add `session.events.resume` accepting a durable/runtime session plus last acknowledged sequence and returning replay or `resync_required`.

Compatibility: fields are additive; older clients ignore them. Servers advertise the capability before clients request replay.

Security: replay is scoped to the authenticated profile/session; bounded retention and payload limits prevent history exfiltration or memory exhaustion.

Tests: ordered replay, duplicates, out-of-order rejection, expired cursor, profile isolation, reconnect during approval and unknown future event types.

Suggested commits: schema/envelope → server replay buffer → contract tests/docs. Android support remains a separate repository commit.

## 2. Native application OAuth exchange

Existing limitation: dashboard OAuth ends in HttpOnly browser cookies. Electron can own a persistent browser session; Android Custom Tabs cannot safely hand those cookies to OkHttp.

Proposal: register a public native OAuth client using Authorization Code + PKCE and an app/universal link. Exchange the code for a revocable Hermes client session represented by a Keystore-stored refresh credential. REST accepts its bearer access token; WS uses a freshly minted single-use ticket. Provider discovery advertises native-flow support and redirect URIs.

Compatibility: browser cookie flow remains unchanged. Native flow is opt-in and capability-gated.

Security: exact redirect matching, PKCE S256, state and nonce validation, no client secret, short access TTL, rotated refresh credentials, device-session listing/revocation and audit logging.

Tests: interception, code replay, redirect mismatch, verifier mismatch, refresh rotation/reuse, logout/revocation and ticket single-use.

## 3. Mobile delivery registration

Existing limitation: Hermes can deliver through messaging gateways but has no first-party mobile-device semantics.

Proposal: a general `client_devices_v1` service separate from model tools. Register an installation public key and push token; return a revocable device ID. Events include opaque notification metadata and deep-link target, never transcript/tool secrets by default. Approval actions use a short-lived, single-use, run-bound action token and still require authenticated server confirmation.

Compatibility: optional service and capability. No foreground socket or push provider is required for existing Hermes clients.

Security: token rotation, multiple devices, per-device revocation, delivery acknowledgements, de-duplication IDs, bounded offline queue, private notification defaults and no push-provider access to message bodies.

Tests: multi-device delivery, revoked/rotated tokens, duplicate ACK, expired approval action, wrong session/profile and offline queue bounds.

## 4. Canonical protocol schema

Existing limitation: Desktop TypeScript, Python handlers and external clients manually mirror parts of the contract.

Proposal: publish a versioned JSON Schema or OpenAPI plus JSON-RPC method/event schema generated from the same models used in server contract tests. Configuration fields should declare sensitivity, risk/confirmation level, mutability and application/restart semantics so remote clients do not maintain key-name exclusions. Generate TypeScript and Kotlin boundary models. Preserve an extension/unknown-event escape hatch.

Suggested boundary: schema and golden fixtures first; generators and clients follow independently. Avoid changing runtime behaviour in the schema commit.

## 5. Atomic rollback preview precondition

Existing limitation: `rollback.diff` returns a bounded textual diff and stat, while `rollback.restore` accepts only the checkpoint hash. A full client can recheck the preview immediately before restore, as Android does, but the gateway cannot atomically reject a workspace mutation occurring between that check and restore.

Proposal: advertise an optional `rollback_precondition_v1` capability. Return an opaque, short-lived `preview_id` from `rollback.diff`, bound server-side to the runtime session, checkpoint, working directory and complete current workspace tree. Allow `rollback.restore` to accept `expected_preview_id` and reject if that bound state no longer matches immediately before mutation.

Compatibility: both fields are additive and optional. Existing clients and restores retain current behaviour; capable clients send the precondition after displaying the preview.

Security: preview IDs must be unpredictable, short-lived, single-use after successful restore, scoped to the authenticated runtime session and never reveal a server path. Rate-limit creation and bound server storage.

Tests: matching restore, workspace change, checkpoint mismatch, session/profile mismatch, expiry, replay, concurrent restore, busy-session rejection and legacy restore without a precondition.

Suggested commits: capability and preview-store contract → atomic restore enforcement/tests → protocol documentation. No upstream pull request is opened by this repository.

## 6. Safe MCP patch and remote OAuth hand-off

Existing limitation: configured MCP reads intentionally redact environment and header values, while the only general edit route replaces the entire raw `mcp_servers` map. A remote client cannot safely round-trip that summary without destroying hidden credentials or stale fields. The existing MCP OAuth action opens a browser on the Hermes server host and blocks, so a remote Android browser cannot own or complete the flow.

Proposal: add a profile-scoped per-server PATCH contract with explicit optional operations for transport fields, enabled tools and secret replacement/removal; omitted secret fields remain unchanged. Separately, expose an MCP OAuth start/status/cancel flow that returns an authorization URL to the authenticated client and binds the callback state to the selected profile and server. The server continues storing tokens and performing discovery; Android only opens the returned URL and polls opaque status.

Compatibility: existing whole-map replace and host-browser OAuth remain unchanged. New routes are additive and capability-advertised. Catalog install/delete/toggle paths require no change.

Security: validate server identity and transport with the existing MCP security layer; never return stored secrets; require an explicit sentinel to remove a secret; bind OAuth state to authenticated profile/server, expire it, reject replay and cross-device completion, and redact URLs/tokens from logs and diagnostics.

Tests: redacted no-op patch, secret replace/remove, concurrent edit conflict, profile isolation, suspicious command rejection, OAuth start/status/cancel, expiry, replay, wrong profile/server and callback forgery.

Suggested commits: per-server patch model/handler/tests → remote OAuth transaction/tests → Desktop/docs adoption. No upstream pull request is opened by this repository.
