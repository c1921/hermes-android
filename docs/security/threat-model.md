# Threat model

Status: living pre-release review, updated 18 July 2026

## Assets and trust boundaries

Assets include backend credentials, provider secrets reachable through Hermes, conversations, workspace files, approval authority and profile identity. Trust boundaries exist at Android intents, local storage, OS notifications/clipboard, network/TLS, Hermes authentication, rendered Markdown/HTML, file previews and third-party push infrastructure.

## Threats and controls

| Threat | Current or required control | State |
| --- | --- | --- |
| Backend impersonation/TLS downgrade | HTTPS by default; endpoint validation; no silent HTTPS→HTTP fallback | Implemented |
| Cleartext LAN token exposure | Explicit opt-in plus literal private-address allow-list; persistent warning | Implemented, warning UI needs strengthening |
| WebSocket credential leakage | Mint a fresh 30-second single-use Dashboard ticket for each upgrade; never place the session cookie in the upgrade or log WebSocket URLs | Implemented; deployment proxies should still redact query strings |
| Token theft at rest | AES-GCM key generated in Android Keystore; ciphertext only in private preferences; backups disabled | Implemented |
| Draft leakage at rest | App-private backend/profile/session-scoped DataStore, hashed preference keys, backup/device-transfer exclusion, bounded size, and backend-forget cleanup | Implemented baseline; rooted-device risk remains |
| Unsafe slash command exposure | Catalogue is server-sourced but Android allow-lists supported built-ins, excludes terminal-only commands, bounds input/output, and only runs commands the user explicitly submits | Implemented baseline; server-side command policy remains authoritative |
| OAuth interception/replay | PKCE S256, state/nonce, exact app link and single-use code | Blocked on native upstream flow |
| Malicious deep link | Treat route IDs as untrusted; require authenticated backend lookup; no credentials/actions in links | Required before link routing ships |
| Exported component/intent spoofing | Only launcher activity exported; explicit declarations; validate all inbound content | Implemented baseline |
| Screenshot/recents leakage | Optional `FLAG_SECURE`, private recent-task snapshot | Planned |
| Clipboard leakage | No automatic copy; sensitive-copy warning and timed clear where supported | Planned |
| Notification privacy | Secret content hidden by default; separate channels; action tokens | Blocked on push contract |
| Markdown/tool-output injection | Render declarative Markdown only; no executable HTML/JS; links require explicit open | Current UI is plain/code text; full renderer planned |
| WebView compromise | Isolated preview activity/process, JS off by default, no `file://`, no universal file access, CSP and MIME checks | Planned; no WebView shipped |
| Path traversal/file preview | Backend path normalisation plus client display-name/MIME validation; SAF export | Planned |
| Oversized payload/zip bomb | HTTP/body, attachment, decompression, pixel and page limits; stream to disk | Planned |
| Malicious skill | Preserve Hermes review/scan boundary; show source/origin/trust; never direct-install around Hermes | Planned |
| Tool-output approval spoofing | Approval UI is driven only by a typed `approval.request` event bound to runtime session, never Markdown | Implemented |
| Sudo or secret prompt leakage | Typed gateway events open a blocking password-semantics field backed only by non-saveable Compose state; values are sent once through the matching response method and never enter timelines, drafts, preferences, logs or diagnostics | Implemented |
| Replay/duplicate events | Stable tool/request IDs; reducer upsert; authoritative rehydrate | Partial; stream cursor upstream gap |
| Cross-profile/session confusion | Cache/navigation identity includes backend, profile, durable ID and runtime ID; events filtered by runtime | Implemented baseline, more tests required |
| Diagnostic leakage | Structured redaction and explicit export review; never include tokens or raw credential stores | Planned |
| Dependency compromise | Version catalogue, dependency locking, Dependabot/review, secret scan, SBOM and release provenance | CI foundation planned |
| Android backup extraction | `allowBackup=false`, cloud/file exclusions | Implemented |

## Release gates

Before public release: complete MASVS-aligned review, dependency and secret scans, network interception tests, exported-component audit, WebView/file corpus tests, notification-action replay tests, database extraction test, diagnostic redaction fixtures, SBOM, signed provenance and an independent review of every high-risk finding. No critical or high finding may remain open.
