# Mobile product specification

## Concepts considered

| Direction | Hermes fidelity | Mobile use | Tablet scale | Accessibility | Cost |
| --- | --- | --- | --- | --- | --- |
| Dense terminal cockpit | High visual familiarity, poor product fidelity | Weak for touch and long reading | Good density | Poor scaling/focus | Medium |
| Conventional chat with settings drawer | Easy to learn but generic | Strong basic chat | Wastes wide layouts | Strong | Low |
| Session atlas plus focused run surface | Preserves Hermes session/tool hierarchy | Fast switching and clear interrupts | Natural two-pane/master-detail | Strong with explicit semantics | Medium |

The session-atlas direction is selected. Chat remains home, while tools, approvals and agent activity emerge from the run that produced them. Management nouns become real destinations only when their backend path is implemented.

## Information architecture

- Phone: session atlas → conversation. Predictive back returns to the atlas without closing the live session.
- Tablet/foldable expanded: persistent 330dp session rail plus conversation.
- Durable management destinations: Skills, Cron, Profiles, Providers, Files, Diagnostics, Messaging, MCP, Usage and Command Center. Artifacts and broader structured Settings join only when their backend paths are complete.
- Short tasks: model selection, approval, clarification, rename and backend switch use sheets/dialogs.
- Detail panes: tool output and artifacts may expand side-by-side on wide layouts without stealing focus.

## Visual language

The canonical visual source is the current first-party Hermes Agent site. Its core tokens are Hermes blue `#0000F2`, off-white `#F5F5F5`, paper white `#FFFFFF`, and accent lime `#EDFF45`. Dark mode is an electric-blue field with off-white typography; light mode reverses that relationship. The official etched Hermes hero, headphone portrait badge, and Desktop application icon are bundled from first-party sources rather than recreated.

Display headings use a high-contrast editorial serif. The site uses licensed Sigurd; Android uses the OFL Cormorant Garamond fallback until Nous supplies an app-embedding Sigurd license. Utility labels, body copy, inputs, and technical state use the site's Courier Prime. Controls preserve the site's hairline-outline character while using a shared rounded Android shape scale of 8/12/16/24/32dp. No interactive container has square corners.

The launcher uses Hermes Desktop's official application icon. Product screens use the official site badge and hero artwork. Source and license details live in `THIRD_PARTY_NOTICES.md` and `licenses/`.

Dark surfaces carry a low-opacity carousel of six purpose-built Nous field plates: three long-held anchor compositions and three short bridge compositions. The bridge plates share geometry with both neighbours so the eight-second crossfade reads as one evolving technical engraving rather than unrelated wallpaper. The centre stays quiet behind conversation content, the sequence advances only while the app is resumed, and Android's zero-duration motion setting freezes it on the first plate.

## States

- Loading never replaces the whole shell after initial connection.
- Empty session lists offer a real new-session action.
- Reconnecting preserves transcript and draft, disables send, and keeps stop/approval state visible.
- Auth expiry identifies the failed leg and returns to backend re-authentication.
- Unsupported capabilities are absent from primary UI; diagnostics explain the required server version.
- Tool and provider errors retain technical status codes with secrets removed.
- Process recreation restores selected backend metadata and backend/profile/session-scoped drafts; live state and navigation are rehydrated from Hermes rather than trusted from a stale local process snapshot.
- Retry, undo and reset are explicit confirmed session actions. Reset closes the old live runtime before opening a clean one, while its durable transcript remains available in the session list.
- Checkpoint restore lives with session actions: Android lists only checkpoints advertised by the open runtime, requires a raw diff preview, rechecks the bounded diff before mutation, disables restore during an active run, and presents a second confirmation explaining both server-workspace mutation and last-turn removal. A changed diff cancels restore and requires a new review. After success the conversation is rehydrated from authoritative Hermes history; a failed history reload clears stale local content and requires reopening the session.
- Composer history is session-scoped and derived from the visible authoritative user-message timeline, newest first. A bounded history picker supports touch, while Ctrl+Up/Ctrl+Down mirrors Desktop's backward/forward cursor and restores the exact unfinished draft when returning to the present. The browse cursor resets on direct editing, submit, or session change; history is not copied into another local transcript store.
- While the selected session is running, a distinct queue action stores text-only follow-up turns under the hashed backend/profile/durable-session scope. The queue remains editable and removable, drains FIFO only when the authenticated socket is open and the runtime is idle, and removes an entry only after the audited `prompt.submit` status is accepted. Four failed automatic attempts leave the entry visible for edit, removal, or explicit retry. Attachments are excluded because Android cannot safely persist Hermes' current process-local attachment references across process death; queues for off-screen sessions wait until that session is reopened.
- Typing `/` opens Hermes' own categorized command catalogue above the composer. Suggestions and arguments come from the gateway, but Android filters terminal-only built-ins and supports every structured dispatch result: inline output, alias, send, skill, and editable prefill.
- Nous is the default appearance. A lower-priority cosmetic follow-up will expose the exact client-owned Hermes Desktop presets (`nous`, `midnight`, `ember`, `mono`, `cyberpunk`, and `slate`) through a native appearance picker without changing server configuration.
- Voice input is a composer-owned interaction. Holding the rounded microphone control records press-to-talk; a quick tap or upward slide locks recording, and a leftward slide cancels it. Recording clears composer focus and hides the IME so the microphone state never competes with the keyboard. An always-visible cancel/transcribe surface provides a non-gesture alternative.
- Completed assistant messages expose a labelled read-aloud action. The app sends Desktop-equivalent sanitised reply text to Hermes `/api/audio/speak`, then plays only the audio returned by Hermes' configured TTS provider. Android provides pause, resume, stop and the system media-output switcher; it does not silently replace Hermes TTS with platform synthesis.
- Messaging management is a server-control surface, not an Android chat adapter. A rounded searchable catalogue shows the selected profile's Hermes gateway platforms and their exact reported states. Platform detail can replace or remove only server-advertised fields, enable or disable the adapter, run the backend connection check, and restart the selected profile's gateway only after an explicit impact confirmation. Android never claims to execute gateway delivery locally.
- MCP management is a server-control surface, not an Android MCP runtime. It shows the selected profile's configured servers and Nous-approved catalog metadata, asks Hermes to probe enabled servers, installs a catalog entry only after source/target/bootstrap review, and requires explicit confirmation before removal or enable/disable. Catalog credential fields are generated only from advertised env requirements, use non-saveable password state, and are sent once to Hermes. Git-backed installs are polled through the exact server action identity before refresh/reload. A successful write to a matching live session or current profile is followed by `reload.mcp`; an inactive-profile write is labelled for next-start application. Android never launches stdio/bootstrap commands locally. Remote OAuth and unsafe redacted whole-map edits remain omitted.
- Toolsets live beside Skills and Hub under Capabilities because they describe what a Hermes profile can do, not an Android-local plugin system. The list and tool names are generated entirely by Hermes, platform-restricted entries retain their server-reported target, and Android toggles only an identity returned for the selected profile. The audited REST mutation changes configuration but has no live-session reload contract, so the UI explicitly says new sessions use the change.
- Usage is an authenticated, read-only server view. Period controls request exact 7/30/90-day profile analytics from Hermes; totals retain the distinction between unavailable legacy counters and zero. When a runtime session is open, `session.context_breakdown` adds capacity and category rows. A context RPC failure leaves the profile analytics readable with a precise partial-result message, and category meaning is always stated in text rather than encoded only by server colours.
- Command Center is an orchestration surface, not a decorative agents dashboard. It reads Hermes' global delegation status and cross-session subagent events, preserves parent-child order, exposes emitted tool/output/file/token/cost detail, and lists only background processes owned by the open runtime session. It also lists the bounded spawn-tree snapshots that Hermes' TUI persisted and loads only a path returned by that authenticated list; server filesystem paths are never displayed. Archived snapshots are read-only and clearly separate from live work. Pausing affects future spawns only. Subagent interrupt and process stop are separate, confirmed operations, and each row states its exact consequence.

## Notifications and permissions

Notification channels will separate approvals/input, run completion/failure and cron/automation results. Content previews default to private. Notification actions must carry a signed, single-use server action token or open the exact in-app prompt; broadcast extras alone never authorise an approval.

Microphone permission is requested only when voice recording begins. Denial leaves the conversation usable and exposes an inline route to Android App Info for recovery. Recordings are bounded to two minutes and 25 MiB, kept only in app-private cache, deleted after transcription or cancellation, and stopped when audio focus is lost or the conversation is left. Spoken audio is MIME-checked, base64-checked, capped at 25 MiB and deleted after stop, failure or completion. Storage uses the system document picker and Storage Access Framework. Camera permission is requested only for direct capture. Notification permission is requested after the value is explained, not during cold start.

Secure screen is a device-local privacy preference exposed in Diagnostics. The activity starts protected until DataStore resolves, then applies or clears Android `FLAG_SECURE` from the durable setting. Enabling it protects all Hermes surfaces, including the recent-app thumbnail; it does not claim to prevent photography, rooted-device capture or server-side access.

Messaging tokens and IDs are entered into non-saveable Compose state and submitted directly to the authenticated Hermes profile. Android does not write them to DataStore, saved instance state, diagnostics or logs. Existing values are displayed only through the server's redacted representation.

MCP summaries deliberately omit server environment and header maps even though Hermes redacts them. Configured stdio arguments are reduced to a count and endpoint query/user-info data is removed before display. Catalog install credential drafts are non-saveable and cleared on submit or dismissal. Probes, installs, removals and runtime reloads execute on the authenticated Hermes backend; Android stores no MCP configuration or credentials.

Toolset management stores no local catalogue or configuration. It renders only the authenticated backend response, accepts mutations only for a currently advertised identity, requires the acknowledgement to match name, platform and requested state, and does not claim an active session was reconfigured.

## Offline behaviour

The app is not an offline agent. Previously hydrated transcripts, drafts and explicitly queued text follow-ups may remain readable. A pending message drains only for its selected authenticated runtime after connection and authoritative session rehydration; a bounded failure state requires explicit user recovery. Other mutations are not replayed. Approvals are never queued offline because they may expire or refer to a changed run.

## Motion system

- Onboarding: one state machine moves from architecture explanation to backend link. Back reverses the same spatial transition.
- Master/detail: selecting a session moves from atlas to the conversation destination; predictive back reverses it.
- Tool activity: request inserts one stable row, progress modifies it, completion settles the same identity.
- Approval/clarification: a blocking prompt is owned by its originating runtime session and cannot be dismissed into a lost state.
- Connection loss changes a narrow status line; the transcript does not flash or relayout.
- Reduced-motion mode replaces spatial transitions with immediate changes or short fades. Streaming content never animates each token.
- Ambient field art holds each anchor for 150 seconds, crosses through a 12-second bridge plate, pauses off-screen, and remains static when the system motion scale is zero.
- Recording feedback uses a stable level bar rather than decorative waveform motion. Reduced-motion users receive the same elapsed time, recording mode and text instructions without relying on animation.

Interrupted animations settle to semantic state. Process recreation restores the destination, not an animation phase.

## Accessibility

Minimum 48dp controls, scalable text, semantic headings, labelled icon actions, logical master/detail traversal and no colour-only state. Voice actions expose TalkBack labels for record, stop, read, pause, resume and output selection; recording mode and interruption state are also written as text. Command Center uses explicit status labels in addition to colour, exposes a labelled pause switch, and requires confirmation before intervention. Streaming updates do not continuously steal TalkBack focus. Hardware Enter sends only from the composer; Escape/back affects the topmost owned interaction once. Tool state combines icon, text and colour.
