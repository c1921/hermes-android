# Upstream baseline

Latest verification: 17 July 2026 (Europe/London)

## Pinned source

| Component | Source | Audited revision/version |
| --- | --- | --- |
| Hermes Agent | `https://github.com/NousResearch/hermes-agent` | `0f102fa4dc04b7dfdab048169aaaa640d09d7523` |
| Hermes Python package | `pyproject.toml`, `hermes_cli/__init__.py` | `0.18.2` |
| Hermes Desktop | `apps/desktop/package.json` | `0.17.0` |
| Desktop transport | `apps/shared/src/json-rpc-gateway.ts` | same commit |

The upstream checkout is an audit input only and is not vendored into this repository.

## Relevant implementation areas

| Concern | Source entry points |
| --- | --- |
| Desktop shell and feature surfaces | `apps/desktop/src/app`, `apps/desktop/src/components` |
| Desktop REST adapter/types | `apps/desktop/src/hermes.ts`, `apps/desktop/src/types/hermes.ts` |
| Shared JSON-RPC client | `apps/shared/src/json-rpc-gateway.ts` |
| Remote backend resolution | `apps/desktop/electron/connection-config.ts`, `apps/desktop/electron/main.ts` |
| Structured gateway methods/events | `tui_gateway/server.py`, `tui_gateway/ws.py`, `tui_gateway/transport.py` |
| Dashboard/serve REST and WS | `hermes_cli/web_server.py` |
| Dashboard auth/OAuth/tickets | `hermes_cli/dashboard_auth/*` |
| Messaging adapters | `gateway/platforms`, `gateway/run.py` |
| Stable external run API | `gateway/platforms/api_server.py` |
| Sessions | `hermes_state.py`, `tui_gateway/server.py`, REST routes in `hermes_cli/web_server.py` |
| Cron | `cron`, REST routes around `hermes_cli/web_server.py:10654` |
| Skills and MCP | `skills`, `hermes_cli/skills_hub.py`, REST routes around `hermes_cli/web_server.py:12565` and `13699` |
| Profiles | `hermes_cli/profiles.py`, REST routes around `hermes_cli/web_server.py:13233` |
| Voice | `hermes_cli/voice.py`, REST routes around `hermes_cli/web_server.py:3742` |
| Termux detection/support | `hermes_constants.py`, `hermes_cli/main.py`, Termux extras in `pyproject.toml` |

## Full-client protocol

The interactive client contract is JSON-RPC 2.0 over `/api/ws`. On connection, the server emits `gateway.ready`. The audit found 117 registered methods in `tui_gateway/server.py`, including:

- `session.create`, `session.list`, `session.resume`, `session.history`, `session.undo`, `session.compress`, `session.branch`, `session.interrupt`, `session.steer`
- `prompt.submit`, `prompt.background`
- `image.attach`, `image.attach_bytes`, `pdf.attach`, `file.attach`
- `approval.respond`, `clarify.respond`, `sudo.respond`, `secret.respond`
- `model.options`, `config.get`, `config.set`, `skills.manage`, `cron.manage`, `agents.list`, `delegation.status`, `rollback.list`, `rollback.restore`

Events include `message.start`, `message.delta`, `message.complete`, `reasoning.delta`, `status.update`, `tool.start`, `tool.progress`, `tool.complete`, `approval.request`, `clarify.request`, `sudo.request`, `secret.request` and background/subagent events. Unknown event names are explicitly possible in Desktop's shared type and must not crash clients.

REST remains the supported source for management/list data used by Desktop: sessions, profiles, config/schema, providers/models, skills hub, MCP catalogue, cron, messaging platforms, analytics, logs and operations. Android therefore uses REST and JSON-RPC together rather than treating the OpenAI-compatible API as the product protocol.

## Authentication paths

- Local/static-token mode: bearer token for REST and `?token=` for `/api/ws`.
- Gated remote mode: HttpOnly cookie session created through a registered dashboard auth provider. A client mints a single-use, roughly 30-second WebSocket ticket at `POST /api/auth/ws-ticket` and connects with `?ticket=`. Tickets must never be cached or reused.
- Password providers use `POST /auth/password-login` but converge on the same cookie/session/ticket flow.
- Nous Portal is one dashboard auth provider, not a separate Android token API.

Desktop stores OAuth cookies in an Electron persistent session partition. Android Custom Tabs and an app HTTP client do not share a cookie jar, so copying the Desktop flow without an explicit app redirect/token exchange would be unreliable and insecure.

## Observed limitations affecting Android

1. Gateway events do not carry a universal monotonic event sequence, replay cursor or stable ID. Tool calls have `tool_id`, prompts have request IDs, but streamed message deltas do not. Exact replay/de-duplication after a dropped socket cannot be implemented generically.
2. OAuth assumes browser-cookie REST plus single-use WS tickets; there is no documented native-app callback/token exchange contract.
3. The backend has no first-party mobile push device registration, revocation, delivery acknowledgement or notification-action API.
4. Static WS auth uses a query token because WebSocket browser clients cannot set arbitrary headers. Query credentials can appear in infrastructure access logs unless operators redact them.
5. Remote file and artifact support is split across REST, gateway attachment methods and Desktop/Electron filesystem capabilities. Desktop-native paths cannot be copied to Android.
6. Android can host Hermes under Termux, and upstream contains significant Termux support, but an app cannot safely embed or control another app's Termux runtime without an explicit companion contract.

## Relevant upstream issues and PR themes

- `#35966` requests a native desktop/mobile client using the Gateway/API Server.
- `#62753` asks for an Android app and a coherent external API.
- `#11911` requests mobile voice and background operation.
- `#37835` describes a mobile-first structured `/api/ws` chat hub with human interrupts and profile-aware sessions.
- `#36970` and `#38602` document Desktop remote-client onboarding gaps and the static remote URL/token workaround.
- Recent Termux fixes cover process cleanup, builds, dependency selection and gateway restart behaviour; this makes Termux a viable optional operator path, not an embedded-runtime API.

Issue references are research evidence only. This project does not comment on or modify upstream issues or pull requests.

