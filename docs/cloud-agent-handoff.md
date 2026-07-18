# Cloud agent handoff

- Snapshot date: 18 July 2026
- Expected starting branch: `main`
- Baseline before the spawn-tree and secure-screen slices: `0900caf98ff44c29136c8e3361468b00c06ab542`

## Objective

Continue building a first-party-quality native Android client for Nous Research Hermes Agent. This is not a WebView wrapper, generic chat client, mock dashboard, or design prototype. Every visible control must use a real Hermes REST or JSON-RPC/WebSocket contract, fail safely, and preserve the security model.

Do not restart the project or rewrite working slices. Pull `main`, inspect the current implementation and checklist, verify the baseline, then continue from the highest-impact unblocked gap.

## Source of truth

Read these committed files before changing code, in this order:

1. [`CONTRIBUTING.md`](../CONTRIBUTING.md)
2. [`README.md`](../README.md)
3. [`docs/research/desktop-parity-matrix.md`](research/desktop-parity-matrix.md)
4. [`docs/design/mobile-product-spec.md`](design/mobile-product-spec.md)
5. [`docs/architecture/android-client-rfc.md`](architecture/android-client-rfc.md)
6. [`docs/security/threat-model.md`](security/threat-model.md)
7. [`docs/research/upstream-baseline.md`](research/upstream-baseline.md)
8. [`docs/upstream/upstream-change-plan.md`](upstream/upstream-change-plan.md)

The parity matrix is the implementation checklist. Treat its statuses strictly. `Implemented` requires a real client path and verification. `Foundation`, `blocked`, and `not implemented` must not be presented as finished.

For protocol work, inspect the exact upstream Hermes source and cite the commit used. The current audited contract is `NousResearch/hermes-agent@5122ddd478143a6901bb752cf8ebcd1c5154b6da`. If auditing a newer commit, record it and update the baseline and parity matrix. Never guess request fields, event fields, routes, or semantics.

## Current checkpoint

Recent completed slices on `main`:

- Pending-message queue mirrors Desktop's client-owned FIFO semantics for the selected session: text entries are durably scoped by hashed backend/profile/session identity, can be edited or removed while the current run is active, drain through the audited `prompt.submit` contract only after the runtime settles, and stop after four failures for explicit recovery. Queued attachments and off-screen cross-session drain remain omitted.
- Composer history derives the current session's prior user prompts from authoritative timeline messages, provides a bounded mobile picker, and mirrors Desktop's draft-preserving backward/forward cursor semantics on Ctrl+Up/Ctrl+Down without creating another persisted transcript.
- Checkpoint management adds session-scoped checkpoint listing, mandatory bounded diff preview, explicit full-workspace restore confirmation, busy-session rejection, full-hash allowlisting, and authoritative history rehydration after Hermes removes the affected turn. File-scoped rollback remains omitted until a safe server file selector can supply the exact relative path.
- Usage management adds profile-scoped 7/30/90-day token, API-call, model, tool, skill and cost summaries plus the live session's `session.context_breakdown`.
- MCP management adds profile-scoped configured-server and Nous catalogue inspection, backend probes, reviewed non-OAuth catalog installation, canonical background-action polling, confirmed removal, enable/disable, and live `reload.mcp`; custom creation/edit, remote OAuth and per-tool filters remain follow-up work.
- Toolset management adds the exact profile-scoped Dashboard catalogue and toggle contract under Capabilities. Android exposes server-advertised target platform, setup state and resolved tools, accepts only advertised identities, validates the full mutation acknowledgement, and tells users changes affect new sessions because upstream exposes no live toolset reload.
- Server Settings uses the exact profile config/schema/deep-merge contract. Android intersects the schema with an audited positive set of bounded scalar fields, writes one nested field at a time, keeps values only in process memory, rejects secret-bearing and high-risk global policy/execution fields, checks profile identity across mutations, and requires Hermes' positive acknowledgement.
- Command Center now lists and replays the bounded cross-session spawn-tree snapshots persisted by Hermes TUI through exact `spawn_tree.list/load` calls. Android loads only an advertised path, does not reveal server filesystem paths, checks list/load session identity, and defensively normalises bounded historical subagent detail. It does not claim that Desktop or Android turns were archived when the TUI did not save them.
- Secure screen is now a durable device-local Diagnostics setting. The activity starts with `FLAG_SECURE` until DataStore resolves and then applies the stored choice across every surface, protecting screenshots, screen recording and recent-app thumbnails without changing Hermes or persisting sensitive content.
- `889aa37` adds the mobile Command Center with live delegation status, subagent trees, pause/resume, confirmed interruption, session-owned background processes, and confirmed process stop.
- `072f2b9` adds messaging gateway management.
- `17cb866` adds Hermes-native voice transcription and spoken playback. Android does not substitute platform TTS.
- `92b4dfa` adds non-persistent sensitive sudo and secret prompts.
- `2b4520f` adds the ambient Nous backdrop sequence and workspace assets.
- `a6e4a5e` adds the mobile slash-command palette.
- `6a69556` adds retry and confirmed reset.
- `774b3ee` adds durable drafts and session search.
- `e98fc0c` and `0808504` establish the Hermes visual system and audited upstream baseline.
- `510d139` makes CI build and upload both `app-debug.apk` and `app-debug.aab`.
- `a397dcd` fixes the README download badge to select successful `main` runs.

The baseline GitHub Actions run for `a397dcd` completed successfully:

- Run: `https://github.com/luinbytes/hermes-android/actions/runs/29631368219`
- Artifact: `hermes-android-debug`
- Gate: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:bundleDebug`

The repository was clean and local `main` matched `origin/main` at this snapshot.

## Known release issue

GitHub-hosted runners currently generate a new Android debug signing key per run. Consecutive CI APKs therefore have different certificates and cannot update one another in place. A fresh install works, but a later CI build requires uninstalling the previous build and loses app-local backends, encrypted sessions, and drafts.

Treat stable update signing as important release work. Do not commit a private signing key or password. If secure repository secrets and an owner-approved signing identity are unavailable, document the exact blocker and continue with another unblocked checklist item. Do not claim the debug artifact is a production release.

## Remaining work

Use the parity matrix for the full list. Prioritise production impact over cosmetic breadth.

Good unblocked candidates include:

- MCP custom-server creation, safe per-server editing, remote-client OAuth setup, and per-tool filters; edit/OAuth require the upstream changes recorded in the change plan.
- Agent background delivery and replay coverage for non-TUI clients; TUI-persisted `spawn_tree.list/load` replay is complete.
- Platform integrations that do not require a new upstream contract.
- The exact Desktop appearance presets (`nous`, `midnight`, `ember`, `mono`, `cyberpunk`, `slate`) after higher-impact functional work.

Do not spend the run pretending to solve these upstream blockers locally:

- Native OAuth or OIDC without an upstream native PKCE exchange.
- Reliable Android background push without an upstream device registration and action-token contract.
- Exact in-flight reconnect replay without an upstream ordered event cursor.

The upstream change plan already records those proposals.

## Product rules

- Match the official Hermes and Nous Research branding already committed to the repository.
- Preserve Hermes blue, editorial serif display type, Courier-style utility type, official assets, and the existing rounded shape scale.
- No hard edges on interactive containers. Rounding is required.
- Reuse existing components, tokens, reducers, clients, and patterns before adding code or dependencies.
- Keep motion smooth, restrained, and compatible with Android reduced-motion settings.
- Keep the composer stable with the IME open. Do not reintroduce the earlier large empty-space or misplaced-composer layout failure.
- Do not add decorative controls, fake data, placeholder routes, or unsupported settings.
- Preserve unknown-event tolerance and capability-gated behaviour.
- Keep secrets out of logs, diagnostics, saved UI state, commits, fixtures, and artifacts.
- Do not weaken Dashboard authentication, TLS validation, private-network policy, WebView isolation, confirmation gates, or session ownership checks.
- Do not add Android platform TTS as a fallback for Hermes TTS.
- Do not add speculative abstractions or dependencies. Prefer the smallest existing seam that solves the real flow.

## Cloud constraints

This run is cloud-only.

- No physical Samsung or other attached Android device is available.
- Do not reference `/home/lu`, `/mnt/hdd`, `/tmp` evidence, local Codex attachments, local screenshots, ADB serials, local Tailscale state, or any other workstation path.
- Do not assume access to the user's personal VPS, credentials, tailnet, browser session, or Hermes instance.
- Do not change, restart, or probe personal, Iniuria, or Olive Hermes services.
- Do not claim physical-device, real-VPS, microphone, audio-route, IME, or visual QA from this cloud run.
- If an Android emulator is genuinely available, it may provide supplementary smoke evidence, but it does not replace the deferred physical-device gate.
- If any test ever reaches a real Hermes agent, the prompt must state that it is a native Android QA test and may be ignored. Do not send such a prompt without explicit access and authority.

Record device-only verification as deferred, with exact steps for the next local pass.

For the MCP slice, the deferred pass must inspect configured, catalog, install-review, credential, background-progress, removal-confirmation and error layouts on phone and expanded widths; exercise TalkBack, 130%+ text, keyboard focus, process recreation with a credential dialog open, and profile switching; then use a disposable pinned backend to install one synchronous and one git-backed non-OAuth catalog entry, verify required/optional credential delivery without local persistence, probe success/failure, enable/disable, confirmed removal, live-session reload without transcript loss, and saved-but-reload-failed recovery. None of those checks are established by the cloud unit/lint/build gate.

For the usage slice, the deferred pass must inspect 7/30/90-day, empty, large-number and partial-context states on phone and expanded widths; exercise TalkBack traversal, 130%+ text and keyboard focus; compare one disposable profile's totals with the pinned Dashboard; and verify an open session's context category total and capacity before and after a message. The cloud gate does not establish those visual, accessibility or live-accounting checks.

For the checkpoint slice, the deferred pass must use a disposable workspace and checkpoint-enabled session on the pinned backend; create at least two file mutations, compare Android's list and raw diff with `/rollback` and `/rollback diff`, verify that an active run disables restore, confirm a full rollback changes only the expected server files, confirm the affected last turn disappears after history reload, and verify the pre-rollback snapshot can restore the prior state. Inspect disabled, empty, long-diff, error and post-restore states at phone and expanded widths with TalkBack, 130%+ text and keyboard focus. File-scoped restore is not exposed. The cloud gate does not establish visual layout, accessibility, live filesystem mutation, or rollback recovery.

For the composer-history slice, the deferred pass must inspect empty, one-entry, duplicate, multiline and 20-entry picker states at phone and expanded widths; verify that selecting an entry updates the composer without sending; verify Ctrl+Up walks older prompts, Ctrl+Down returns toward the present and restores the exact unfinished draft; verify typing resets browse state; then exercise TalkBack, 130%+ text and hardware-keyboard focus. The cloud gate does not establish visual layout, key delivery from real hardware, IME interaction or accessibility traversal.

For the pending-message queue slice, the deferred pass must use the pinned backend to hold a run active, enqueue at least three text turns, edit and remove non-head entries, then verify FIFO submission and authoritative transcript order after each settle. Force one rejected submission to verify bounded retry and manual recovery; kill and recreate Android before the run settles to verify durable scope and no duplicate drain; switch sessions and profiles to verify isolation; and confirm attachments disable queueing with the stated explanation. Inspect empty, full, long, multiline, draining and stuck states at phone and expanded widths with TalkBack, 130%+ text, IME open and hardware-keyboard focus. The cloud gate does not establish layout, accessibility, process-death timing or live runtime ordering.

For the toolset slice, the deferred pass must compare Android's catalogue with the pinned Dashboard for at least one CLI toolset and one platform-restricted toolset; toggle each in a disposable profile, confirm the persisted `platform_toolsets` target and verify a newly created session receives the expected tool inventory while an already-open session is not misrepresented as reloaded. Inspect enabled, disabled, unconfigured, empty, long-description, long-tool-list, error and profile-switch states at phone and expanded widths with TalkBack, 130%+ text and keyboard focus. The cloud gate does not establish visual layout, accessibility or live configuration effects.

For the spawn-tree replay slice, the deferred pass must run a disposable TUI session that completes a nested subagent tree, confirm the TUI persists it, then verify Android lists it without exposing the server path, loads the same parent/child ordering and detail, opens the matching durable session when present, and remains read-only. Also test a deleted archive between list/load, a malformed legacy snapshot, no archives, 30 archives, long labels, profile switching and a server restart. Inspect phone and expanded widths with TalkBack, 130%+ text and keyboard focus. The cloud gate does not establish visual layout, accessibility or real TUI persistence.

For secure screen, the deferred pass must verify on a physical Android device that the default-off state permits an intentional screenshot after preference resolution; enabling the switch immediately blocks screenshots and screen recording, hides or protects the recent-app thumbnail, survives process death and cold start without an unprotected first frame, and disabling restores normal capture. Repeat on phone and expanded widths and verify TalkBack announces the switch state. The cloud gate establishes preference behavior and build integration, not window-manager or capture behavior.

For Server Settings, the deferred pass must compare Android's categories, types, descriptions, options and current values with the pinned Desktop for one disposable profile; edit one boolean, number, select and text field and verify the exact profile file changes without dropping unrelated, custom-provider or MCP keys. Switch profiles with an edit dialog open to verify draft cancellation and isolation; confirm secret-bearing fields, global approval bypass, private-URL/execution relaxations, model and toolsets are absent; and verify a running session is not described as reloaded. Inspect loading, empty, search, long-description, validation, rejected-write and reconnect states on phone and expanded widths with TalkBack, 130%+ text and keyboard focus. The cloud gate does not establish visual layout, accessibility or live configuration effects.

## Working method

1. Pull `origin/main` and inspect `git status`, recent commits, and the actual diff before editing.
2. Read the source-of-truth files above and reconcile the parity matrix against current code.
3. Run the existing baseline gate before relying on the checkout:

   ```bash
   ./gradlew --no-daemon --max-workers=2 \
     :app:testDebugUnitTest \
     :app:lintDebug \
     :app:assembleDebug \
     :app:bundleDebug
   ```

4. Select one cohesive, high-impact, unblocked slice. Trace the real upstream contract and every existing caller before editing.
5. Add the smallest contract, reducer, repository, UI, and test changes that complete the slice. Reuse existing architecture.
6. Update the parity matrix and any affected product, security, architecture, or README documentation in the same commit.
7. Run focused tests, then rerun the full baseline gate. Inspect produced APK and AAB paths. Do not weaken tests.
8. Review `git diff`, `git diff --check`, security boundaries, accessibility basics, formatting, and backward compatibility.
9. Commit with a terse human-maintainer message and no AI attribution.
10. Push the verified slice to `origin/main`, wait for CI, and verify the remote run and both uploaded artifacts. The established repository workflow uses direct verified commits to `main`; do not open an upstream Hermes PR.
11. Continue with another cohesive slice only while the previous slice is green and the repository remains understandable.

If blocked, exhaust safe repository, upstream-source, test, and CI evidence first. Then record the exact blocker and move to another unblocked item. Do not manufacture a feature around a missing server contract.

## Definition of done for an overnight slice

- Real functionality is implemented against a verified Hermes contract.
- Focused tests and the full cloud-safe Gradle gate pass.
- Documentation and parity status match the code.
- No uncommitted or unrelated changes remain.
- The commit is pushed to `origin/main`.
- The corresponding GitHub Actions run is successful and contains both APK and AAB artifacts.
- Physical-device and personal-deployment claims are explicitly left unverified when the cloud cannot prove them.
