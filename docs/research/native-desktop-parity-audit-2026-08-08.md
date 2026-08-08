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
| Notifications | Exact destination parsing, contextual Android 13 permission UX, four private channels, redacted local rendering, and typed destination taps exist | No server/device push registration or advertised private delivery contract; #20 still blocks background producers and token-bound actions |
| Reconnect | Authoritative resume/projection is tested | No ordered replay cursor, mutation receipt, or multi-client barrier contract |
| Rich conversation | Typed parts, artifacts, Markdown, tool disclosure, and respectful near-end streaming follow exist | Specialised safe reference/media actions and device/a11y proof remain |
| Profiles | CRUD/default/start plus bounded SOUL editing, setup-command copy, and explicit provider/model assignment exist in the current patch | Profile import/export exchanges backend filesystem paths, not bounded archive bytes suitable for Android SAF |
| MCP/toolsets | Catalog review/install/test/toggle/remove/reload and toolset toggles exist | Custom edit, OAuth, per-tool filters, and richer toolset setup remain client work; revision-safe patching lacks a server contract |
| Messaging/webhooks/cron | Messaging and Cron CRUD/run history exist | Pairing, delivery targets, and blueprints remain. Webhook HTTP routes do not accept a profile; Desktop scopes them by selecting a profile-specific child process inside Electron, which Android cannot reproduce safely. Requested webhook edit/test routes also do not exist upstream |
| Memory/maintenance | Profile-scoped Starmap graph/search/node detail/edit/removal, diagnostics, bounded redacted host logs, read-only update status, and exact-receipt host backup export exist | Memory/Curator routes still target only the serving process rather than an explicit remote profile; update apply and backup restore remain intentionally absent |
| Android-native security/media | Secure-screen, opt-in biometric/device-credential re-entry, MediaSession controls, reduced-feedback-aware voice haptics, and profile-scoped WebSocket PCM read-aloud exist in source | Wake-word/barge-in orchestration is not exposed as a safe remote Dashboard contract; biometric and physical lock-screen/Bluetooth/haptic proof remains device work |
| Release quality | JVM/lint/build gates, API 28/36 managed-device tests, deterministic Macrobenchmarks, checked-in accepted-baseline comparison, Baseline Profile generation, SBOM/provenance, and two-clean-build payload comparison are automated | Battery evidence, physical Samsung/accessibility matrix, signing-certificate review, Play App Signing handoff, and owner acceptance remain runtime/promotion gates |

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
- Added explicitly host-wide, bounded/redacted agent logs and read-only Hermes
  update status without exposing update application as a profile action.
- Added confirmed host backup creation, exact process-receipt polling, and
  bounded direct-to-SAF ZIP export without persisting archive bytes in app storage.
- Replaced the global attachment spinner/eager shared batch with a transient,
  scope-fenced per-item lifecycle: bounded MIME/size validation, exact local-read
  progress, indeterminate one-frame upload, multi-document selection, and
  independent retry/cancel/remove. Ready handles remain foreground-only and the
  durable prompt queue remains text-only until Hermes owns a durable descriptor.
- Added opt-in device-local biometric re-entry that withholds Hermes composition
  on a fresh process and after five background minutes, keeps configuration
  changes unlocked, and exposes an Android device-credential recovery path on
  every supported API. No biometric material or protected user-entered content
  is stored in Android saved state by the app.
- Added contextual Android notification permission/settings recovery, four
  private redacted product channels, and a typed local renderer that can only
  route through validated Hermes destinations. Background delivery and inline
  actions remain absent until Hermes advertises device registration and
  short-lived action-token contracts.
- Added profile-scoped `/api/audio/speak-stream` playback with dashboard
  single-use ticket authentication, bounded mono int16 PCM assembly, AudioTrack
  streaming, MediaSession controls, cancellation, and pre-audio buffered-speech
  fallback without replaying a partially spoken reply.
- Added a credential-free benchmark-release fixture with 500 mixed
  messages, continuous streaming, core-surface journeys, API 28/36 managed
  device CI, Baseline Profile generation, SBOM/provenance retention, and
  deterministic unsigned APK/AAB payload comparison.

The practical conclusion is not “Android is missing most of Desktop.” The
foreground client is broad and functional. The remaining distance is
concentrated in server-contract boundaries, remote-management depth, native
lifecycle integrations, and release/device proof. Those must be reported as
blocked or unverified rather than hidden behind source-only parity claims.
