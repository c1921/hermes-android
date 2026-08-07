# Hermes Desktop parity refresh — 2026-08-07

## Scope and attribution

This is a bounded comparison of the shipped Hermes Desktop application with the
Hermes Android `dev` branch. It deliberately excludes Android/mobile issues,
proposals, and third-party clients. The report that prompted this work is most
likely GitHub issue [#2, “Connect Hermes Android to a secured Dashboard”](https://github.com/luinbytes/hermes-android/issues/2), which implements the
parent [#1, “Spec: authenticate Hermes Android through Dashboard sign-in”](https://github.com/luinbytes/hermes-android/issues/1).

Issue #2’s acceptance criteria are: collect dashboard URL/username/password;
sign in and persist only `hermes_session_at`; save only after authenticated HTTP
and WebSocket checks; expose reconnect-required failures without saving bad
state; keep credentials out of UI/backup/diagnostics/logs; prove the complete
connect-and-save flow against a fake dashboard; and pass focused and full tests.
Its parent spec explicitly makes OAuth and multi-account identity flows out of
scope. That is a valid narrow feature request, but it is not a 1:1 Desktop
parity specification.

The current upstream Desktop source was checked at Hermes Agent commit
[`f15a38ee73631b3cd5f7d30765c37d5f0245d403`](https://github.com/NousResearch/hermes-agent/commit/f15a38ee73631b3cd5f7d30765c37d5f0245d403), dated 2026-08-07. The Android
checkout was `dev` at [`f562904da792e1d5706d5dcede1cb9b6870a64ae`](https://github.com/luinbytes/hermes-android/commit/f562904da792e1d5706d5dcede1cb9b6870a64ae).
The older [`desktop-parity-matrix.md`](./desktop-parity-matrix.md) is pinned to
upstream `5122ddd` (2026-07-17), so its “implemented” password-auth row is not
evidence against today’s Desktop contract.

## What Desktop actually ships

| Desktop behavior | Primary source |
| --- | --- |
| Remote settings probe `/api/status`, inspect advertised providers, and distinguish OAuth/password-capable gateways from static-token gateways. Password-capable gateways use the same sign-in button and downstream session path; Desktop does not expose a separate native password form in settings. | [`gateway-settings.tsx`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/src/app/settings/gateway-settings.tsx#L360-L371), [`gateway-settings.tsx`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/src/app/settings/gateway-settings.tsx#L523-L579), [`gateway-settings.tsx`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/src/app/settings/gateway-settings.tsx#L1282-L1337) |
| The OAuth login window uses a persistent Electron session partition. REST requests are sent with that partition’s cookies, and a WebSocket uses a single-use ticket minted by `POST /api/auth/ws-ticket`; the WS URL carries `?ticket=`, not a browser cookie. | [`main.ts`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/electron/main.ts#L5815-L5842), [`main.ts`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/electron/main.ts#L6009-L6162) |
| Current Desktop can select a native OAuth flow from the server’s `auth_flows` capability, opening the system browser and completing loopback + PKCE. The fallback is the embedded cookie login window. | [`native-oauth.ts`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/electron/native-oauth.ts#L22-L27), [`native-oauth.ts`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/electron/native-oauth.ts#L71-L129), [`native-oauth-login.ts`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/electron/native-oauth-login.ts#L69-L77) |
| The server contract that Desktop calls accepts `provider`, username, and password, sets access and refresh session cookies, and mints a short-lived single-use WS ticket. The status response advertises `auth_providers` and `auth_flows`; native PKCE is advertised for non-password providers. | [`routes.py`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/hermes_cli/dashboard_auth/routes.py#L650-L739), [`routes.py`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/hermes_cli/dashboard_auth/routes.py#L799-L828), [`web_server.py`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/hermes_cli/web_server.py#L3173-L3204) |

## Concrete parity deltas

| Priority | Shipped Android behavior | Desktop parity gap | Evidence / consequence |
| --- | --- | --- | --- |
| P0 | Login hard-codes `provider: "basic"` and parses only the first accepted access-cookie name; the credential store is one `DashboardSessionCookie`. | Desktop/server sessions include both access and refresh cookies and let the server refresh an expired access token transparently. | [`DashboardAuthClient.kt`](../../app/src/main/java/com/nousresearch/hermes/network/DashboardAuthClient.kt#L16-L80), [`SecureTokenStore.kt`](../../app/src/main/java/com/nousresearch/hermes/security/SecureTokenStore.kt#L20-L53). Current upstream’s password-login tests cover access-cookie expiry and refresh-cookie recovery: [`test_dashboard_auth_password_login.py`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/tests/hermes_cli/test_dashboard_auth_password_login.py#L234-L314). Android therefore falls to reconnect once its access cookie expires instead of matching Desktop’s durable session behavior. |
| P0 | Android validates status with the cookie, asks for a WS ticket, then opens `/api/ws?ticket=` without a Cookie header. | This is the correct current Desktop/server transport, but issue #2’s wording says the WS handshake itself must reuse the cookie. The report’s acceptance criterion is stale/misleading relative to shipped Desktop behavior; the red test must assert cookie on ticket mint and ticket-only WS upgrade. | [`DashboardBackendConnector.kt`](../../app/src/main/java/com/nousresearch/hermes/data/DashboardBackendConnector.kt#L26-L65), [`DashboardAuthClient.kt`](../../app/src/main/java/com/nousresearch/hermes/network/DashboardAuthClient.kt#L83-L110), [`OkHttpHermesGatewayClient.kt`](../../app/src/main/java/com/nousresearch/hermes/protocol/OkHttpHermesGatewayClient.kt#L48-L56), [`OkHttpHermesGatewayClient.kt`](../../app/src/main/java/com/nousresearch/hermes/protocol/OkHttpHermesGatewayClient.kt#L165-L171). Existing fake-dashboard assertions already encode this ticket seam: [`DashboardBackendConnectorTest.kt`](../../app/src/test/java/com/nousresearch/hermes/data/DashboardBackendConnectorTest.kt#L25-L43). |
| P1 | Android has no provider discovery and no capability-driven auth selection. | Desktop probes status and uses advertised password providers/auth flows; Android cannot connect to a deployment whose password provider is not named `basic`, nor choose native OAuth. | Desktop provider/capability handling: [`gateway-settings.tsx`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/src/app/settings/gateway-settings.tsx#L211-L371). Android hard-code: [`DashboardAuthClient.kt`](../../app/src/main/java/com/nousresearch/hermes/network/DashboardAuthClient.kt#L49-L60). |
| P1 | Android’s `AuthMode.OAUTH` is an enum value, but the shipped onboarding path only accepts `DASHBOARD_SESSION`; there is no Android native browser/loopback/PKCE implementation. | Current Desktop supports `native_pkce` where advertised and persists/refreshes bearer tokens for that flow. This is the largest user-visible Desktop auth feature absent from Android, although issue #1 explicitly marked OAuth out of scope. | Desktop flow: [`native-oauth.ts`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/electron/native-oauth.ts#L71-L129), [`native-oauth-login.ts`](https://github.com/NousResearch/hermes-agent/blob/f15a38ee73631b3cd5f7d30765c37d5f0245d403/apps/desktop/electron/native-oauth-login.ts#L69-L77). Android gate: [`DashboardBackendConnector.kt`](../../app/src/main/java/com/nousresearch/hermes/data/DashboardBackendConnector.kt#L26-L32). |

The actionable conclusion is therefore: issue #2’s narrow connect-and-save slice is largely implemented, including the correct current ticket transport, but the implementation is not 1:1 with today’s Desktop. The first development correction should be a session-cookie bundle with refresh behavior (while retaining the current ticket-only WS upgrade), followed by provider/capability discovery. Native PKCE is a separate parity ticket because the original issue explicitly excludes OAuth.

## Red-capable test seam

Extend the existing public `DashboardBackendConnector.loginValidateAndSave` seam
and its `FakeDashboard` rather than testing UI details. Make the fake match the
current Desktop/server contract:

1. Return both access and refresh cookies from password login, with a deliberately
   short access lifetime; first run the test against the current one-cookie
   implementation so it fails red after access expiry.
2. Assert the provider and credentials on login, then assert the exact cookie
   bundle on REST status and on `POST /api/auth/ws-ticket`.
3. After access expiry, have the fake accept the refresh cookie and emit a new
   access cookie. Assert Android updates encrypted session state without asking
   for the password again and that the subsequent WS ticket is fresh.
4. Assert the WebSocket upgrade contains the single-use `ticket` query and no
   Cookie header, matching Desktop; keep missing/malformed-cookie, wrong-login,
   status-failure, ticket-failure, and WS-failure cases non-persisting.
5. Keep the existing password-redaction and legacy-token reconnect assertions;
   add unknown-provider and provider-capability fixtures before removing the
   Android `basic` hard-code.

For the later native-PKCE parity ticket, use a separate fake capability seam:
`auth_flows=["native_pkce"]`, state/PKCE challenge verification, loopback code
exchange, token refresh, and token-authenticated REST/ticket calls. Do not mix
that flow into the password-cookie ticket above.

