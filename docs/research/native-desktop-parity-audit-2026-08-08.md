# Hermes Android/Desktop parity audit — 2026-08-08

## Verdict

The 2026-08-07 gap report is directionally useful but no longer describes the
current Android tree. It correctly identifies the remaining lifecycle,
management, accessibility, and release risks, but it overstates four product
gaps that have since shipped on `dev`: multiple Dashboard password-provider
selection, validated Android entry delivery, ordered typed conversation parts,
and the adaptive Artifacts/Files viewer.

Android is close to Desktop in the foreground chat/session path. It is not yet
a release-ready 1:1 native port: background delivery, Android-safe Dashboard
OAuth, exact event replay, several remote-management surfaces, biometric/media
integration, full device accessibility, and release proof remain incomplete.

## Audit pins

- Audited at `2026-08-08T07:07:32+0100`.
- Android baseline: [`62c8d820a387a70f025738389d1e66366fd77f58`](https://github.com/luinbytes/hermes-android/commit/62c8d820a387a70f025738389d1e66366fd77f58), plus the reviewed local parity changes described below.
- Current official Hermes Agent `main`: [`b9aa9289a8083f2e9d248ad6837b2938f5ee92d7`](https://github.com/NousResearch/hermes-agent/commit/b9aa9289a8083f2e9d248ad6837b2938f5ee92d7).
- The relevant Desktop REST/type contracts are unchanged from the earlier
  `eaa53de4` audit pin. The only intervening changed file in the inspected
  message-stream surface tightens selected-session cwd ownership; it does not
  add a new Android parity contract.

## Corrections to the 2026-08-07 report

| Reported gap | Current evidence | Audit result |
| --- | --- | --- |
| Multiple password providers are rejected | Native onboarding and reconnect provider selection landed in `110ced5`; the selected provider is rediscovered and submitted exactly | Closed in Android source; physical renewable-session QA is still required |
| Android has only launcher/share entry handling | Bounded launcher, share, app-link, notification, shortcut, and widget request parsing/routing landed in `8ba1dbd` | Client path exists; hosted App Links and real device producer proof remain external/runtime work |
| Conversation history lacks ordered typed parts | The pure reducer/registry landed in `6498158`; the current parity patch adds artifact/media/source history shapes and `message.interim` sealing | Foreground projection is implemented; exact missed-event replay still needs an upstream cursor/receipt contract |
| No Artifacts destination | Adaptive list/detail, profile-scoped extraction, safe previews, origin navigation, SAF export, and read-only provider grants landed in `62c8d82` | Source/build complete; physical viewer, focus, and grant-lifecycle proof remains pending |

## Remaining parity frontier

| Area | Current state | What actually blocks parity |
| --- | --- | --- |
| Dashboard OAuth | Password auth is complete; Android native OAuth is absent | Current `native_pkce` contract permits loopback redirects only and has no Android callback/revoke contract |
| Notifications | Exact destination parsing exists | No server/device push registration and private delivery contract; #20 owns producers and actions |
| Reconnect | Authoritative resume/projection is tested | No ordered replay cursor, mutation receipt, or multi-client barrier contract |
| Rich conversation | Typed parts, artifacts, Markdown, tool disclosure, and respectful near-end streaming follow exist | Specialised safe reference/media actions and device/a11y proof remain |
| Profiles | CRUD/default/start plus bounded SOUL editing, setup-command copy, and explicit provider/model assignment exist in the current patch | Profile import/export exchanges backend filesystem paths, not bounded archive bytes suitable for Android SAF |
| MCP/toolsets | Catalog review/install/test/toggle/remove/reload and toolset toggles exist | Custom edit, OAuth, per-tool filters, and richer toolset setup remain client work; revision-safe patching lacks a server contract |
| Messaging/webhooks/cron | Messaging and Cron CRUD/run history exist | Pairing, delivery targets, and blueprints remain. Webhook HTTP routes do not accept a profile; Desktop scopes them by selecting a profile-specific child process inside Electron, which Android cannot reproduce safely. Requested webhook edit/test routes also do not exist upstream |
| Memory/maintenance | Profile-scoped Starmap graph/search/node detail/edit/removal, diagnostics, and host status exist | Memory/Curator routes still target only the serving process rather than an explicit remote profile; logs/backup/update surfaces remain client work |
| Android-native security/media | Secure-screen and voice playback exist | Biometric re-entry, MediaSession controls, and purposeful haptics remain client work and need physical-device proof |
| Release quality | JVM/lint/build gates are available | TalkBack/switch/keyboard/foldable tests, benchmarks, battery/performance, reproducibility, provenance, and owner device acceptance remain |

## Work completed during this audit

- Finished and committed the adaptive Artifacts/Files slice (`62c8d82`).
- Added typed artifact/media/source history projection and Desktop
  `message.interim` handling with replay/idempotence fixtures.
- Replaced unconditional streaming scroll-to-bottom with a near-end policy and
  an accessible **Jump to latest message** action.
- Added bounded, acknowledgement-checked SOUL reads/writes, display/copy-only
  setup guidance, and explicit profile provider/model assignment. Unsaved
  identity edits use saveable state and require discard confirmation.
- Replaced the stale Starmap placeholder with the current profile-scoped
  `/api/learning` graph and bounded node maintenance contract.

The practical conclusion is not “Android is missing most of Desktop.” The
foreground client is broad and functional. The remaining distance is
concentrated in server-contract boundaries, remote-management depth, native
lifecycle integrations, and release/device proof. Those must be reported as
blocked or unverified rather than hidden behind source-only parity claims.
