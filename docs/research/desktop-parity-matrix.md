# Desktop parity matrix

Baseline: Hermes Agent `0f102fa4dc04b7dfdab048169aaaa640d09d7523`, Agent `0.18.2`, Desktop `0.17.0`. Status is intentionally strict: **implemented** means a real client path exists in this repository; **foundation** is tested domain/transport work without a complete surface; **blocked** identifies a concrete upstream contract gap; **not implemented** is not shipped as decorative UI.

| Desktop capability | Source / backend contract | Android status | Mobile adaptation | Test / limitation | Upstream change |
| --- | --- | --- | --- | --- | --- |
| Remote static-token backend | `electron/connection-config.ts`; `/api/status`, `/api/ws?token=` | Implemented | Manual endpoint with explicit private HTTP opt-in | Transport policy unit tests; real backend test pending | No |
| OAuth remote backend | `dashboard_auth/routes.py`; `/api/auth/ws-ticket` | Blocked | Native PKCE app link, revocable device session | Browser cookies cannot be transferred safely from Custom Tabs | Native OAuth exchange |
| Username/password dashboard auth | `/auth/password-login` | Blocked | Credential form followed by native client session | Current result is browser cookie-only | Native OAuth/session exchange |
| Multiple saved backends | Desktop connection config | Foundation | Backend registry and scoped Keystore secrets | Registry exists; backend picker UI pending | No |
| Capability/version display | `/api/status`; `session.info.desktop_contract` | Foundation | Contract warning and version-gated attachment/YOLO controls | Compatibility warning implemented; full diagnostics UI pending | Canonical capability document desirable |
| Session list across profiles | `/api/profiles/sessions` | Implemented | Phone atlas; tablet rail | Real REST path | No |
| Session resume | `session.resume` | Implemented | Durable→runtime identity translation | Reducer tests; real backend pending | No |
| New session | `session.create` | Implemented | One-tap new conversation | Real RPC path | No |
| Streamed text | `message.start/delta/complete` | Implemented | Stable assistant row, no per-token animation | Reducer test | Replay cursor for exact reconnect |
| Structured tool activity | `tool.start/progress/complete` | Implemented baseline | Expandable causal row | Stable `tool_id` reducer test; specialised renderers pending | No |
| Reasoning visibility | `reasoning.delta/available` | Implemented baseline | Collapsed disclosure | Preference/config control pending | No |
| Status and compression | `status.update` | Implemented baseline | Quiet inline technical state | Special lifecycle styling pending | No |
| Approval allow/deny | `approval.request/respond` | Implemented | Blocking prompt bound to source run | Reducer test; permanent-allow confirmation needs hardening | Push action token for background |
| Clarification | `clarify.request/respond` | Implemented | Choice/text blocking prompt | Real RPC path; accessibility UI test pending | Push for background |
| Sudo/secret request | `sudo.request/respond`, `secret.request/respond` | Not implemented | Secure masked prompt that cannot persist | Omitted from UI | No |
| Stop/interruption | `session.interrupt` | Implemented | Stop replaces send during active submission | Real RPC path | No |
| Steer/redirect | `session.steer` | Implemented | Composer switches to explicit steer while a run is active; stop remains separate | RPC path; real backend test pending | No |
| Retry/undo/compress/reset | session/slash RPC methods | Undo and compress implemented; retry/reset absent | Confirmed session action with optional compression focus | Pinned result fixtures; message-level retry not yet exposed | No |
| Branch | `session.branch` | Implemented baseline | Confirmed session action preserving the visible transcript | Branch identity fixture; real backend test pending | No |
| Session rename/archive/delete/search | `session.title`; REST session routes | Rename/archive implemented; delete/search absent | Session action menu | Rename handles pre-first-turn rows; delete/search omitted | No |
| Reconnect without duplicates | `session.resume`; events | Foundation; exact in-flight replay blocked | Bounded backoff, then durable-session rehydrate | Replacement-close regression test; no universal stream sequence/replay cursor | Event replay v1 |
| Draft persistence/queue | Desktop renderer state | Not implemented | Backend/session-scoped DataStore draft | Composer survives config only | No |
| Slash autocomplete/catalogue | `commands.catalog`, `complete.slash`, `slash.exec` | Not implemented | Inline command palette | Omitted | No |
| Model/provider catalogue | `model.options`, REST model/provider APIs | Implemented baseline | Searchable dynamic picker; never hard-code catalogue | Pinned `0.18.2` response fixture; provider setup/account management pending | Canonical schema desirable |
| Reasoning effort/fast mode/YOLO | `config.get/set`, `session.info` | Implemented baseline | Capability-gated effort/fast controls; session-only YOLO with explicit warning | Protocol fixture; Compose/device tests pending | No |
| File/image/PDF attach | `file.attach`, `image.attach_bytes`, `pdf.attach`, `image.detach` | Implemented baseline | SAF upload, 10 MiB client cap, removable pending chips | Pinned response fixtures; camera/progress/large streaming pending | No for current path |
| File/artifact preview | REST/desktop filesystem bridge, artifact tools | Not implemented | Safe native viewers; isolated WebView | No preview shipped | General remote artifact descriptor desirable |
| Voice STT/TTS | `/api/audio/transcribe`, `/api/audio/speak` | Not implemented | Press/lock recording, Media3 playback | Permissions declared only; no controls | No |
| Profiles management | `/api/profiles`; `/api/profiles/active`; profile-scoped `session.create`/`session.resume` | Implemented baseline | List/create/rename/delete, sticky default, current-process distinction, start profile-scoped session | Contract test; SOUL/editor and per-profile backend overrides pending | Remote profile gateway override discovery remains desirable |
| Skills installed/hub/manage | REST skills/hub APIs, `skills.manage` | Installed list/search/toggle implemented; hub install flow absent | Dedicated capability surface with provenance and usage | Typed REST contract test; source review/install/update/remove pending | No |
| MCP catalogue/config/reload | REST MCP APIs, `reload.mcp` | Not implemented | Structured editor plus advanced raw diff | Omitted | No |
| Toolsets/providers/config | REST config/schema/toolset APIs | Not implemented | Schema-driven forms | Omitted | Formal schema improves durability |
| Cron/jobs/runs | REST cron routes | List/create/edit/delete/pause/resume/run-now and bounded run history implemented | Exact schedule editor labels server-side execution/timezone; run rows open their session | Typed REST route test; natural-language schedule helper pending | Push delivery for mobile result notifications |
| Messaging management | REST messaging platform routes | Not implemented | Status/setup/diagnostics | Omitted | No |
| Agents/Command Center | Desktop agents/command-center; delegation RPCs | Not implemented | Run tree and intervention surface | Omitted; no empty screen shipped | Replay/push beneficial |
| Usage/token accounting | session usage + analytics REST | Not implemented | Accessible summaries | Omitted | No |
| Checkpoint/rollback | `rollback.list/diff/restore` | Not implemented | Explicit diff and confirmation | Omitted | No |
| Logs/doctor/security audit/backup | REST ops/action status | Not implemented | Redacted diagnostics/export | Omitted | No |
| Notifications/deep links | Desktop native notifications | Blocked for background delivery | Private channels and exact target links | Foreground prompts work only | Device delivery v1 |
| Local Termux runtime | Upstream Termux support | Research only | Detect/link to explicit companion | No cross-app runtime API | Optional Termux companion contract |
| Phone/tablet/foldable layout | Desktop has responsive renderer | Implemented baseline | Master/detail below 840dp, two-pane above | Visual device QA pending | No |
| Keyboard/accessibility/reduced motion | Desktop design system | Foundation | Semantics, labelled actions, simple transitions | Full TalkBack/keyboard/reduced-motion audit pending | No |
