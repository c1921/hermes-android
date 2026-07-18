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
- Future durable destinations: Skills, Automations, Agents, Artifacts and Settings.
- Short tasks: model selection, approval, clarification, rename and backend switch use sheets/dialogs.
- Detail panes: tool output and artifacts may expand side-by-side on wide layouts without stealing focus.

## Visual language

The canonical visual source is the current first-party Hermes Agent site. Its core tokens are Hermes blue `#0000F2`, off-white `#F5F5F5`, paper white `#FFFFFF`, and accent lime `#EDFF45`. Dark mode is an electric-blue field with off-white typography; light mode reverses that relationship. The official etched Hermes hero, headphone portrait badge, and Desktop application icon are bundled from first-party sources rather than recreated.

Display headings use a high-contrast editorial serif. The site uses licensed Sigurd; Android uses the OFL Cormorant Garamond fallback until Nous supplies an app-embedding Sigurd license. Utility labels, body copy, inputs, and technical state use the site's Courier Prime. Controls preserve the site's hairline-outline character while using a shared rounded Android shape scale of 8/12/16/24/32dp. No interactive container has square corners.

The launcher uses Hermes Desktop's official application icon. Product screens use the official site badge and hero artwork. Source and license details live in `THIRD_PARTY_NOTICES.md` and `licenses/`.

## States

- Loading never replaces the whole shell after initial connection.
- Empty session lists offer a real new-session action.
- Reconnecting preserves transcript and draft, disables send, and keeps stop/approval state visible.
- Auth expiry identifies the failed leg and returns to backend re-authentication.
- Unsupported capabilities are absent from primary UI; diagnostics explain the required server version.
- Tool and provider errors retain technical status codes with secrets removed.
- Process recreation restores selected backend metadata and backend/profile/session-scoped drafts; live state and navigation are rehydrated from Hermes rather than trusted from a stale local process snapshot.
- Retry, undo and reset are explicit confirmed session actions. Reset closes the old live runtime before opening a clean one, while its durable transcript remains available in the session list.
- Typing `/` opens Hermes' own categorized command catalogue above the composer. Suggestions and arguments come from the gateway, but Android filters terminal-only built-ins and supports every structured dispatch result: inline output, alias, send, skill, and editable prefill.

## Notifications and permissions

Notification channels will separate approvals/input, run completion/failure and cron/automation results. Content previews default to private. Notification actions must carry a signed, single-use server action token or open the exact in-app prompt; broadcast extras alone never authorise an approval.

Microphone permission is requested only when voice recording begins. Storage uses the system document picker and Storage Access Framework. Camera permission is requested only for direct capture. Notification permission is requested after the value is explained, not during cold start.

## Offline behaviour

The app is not an offline agent. Previously hydrated transcripts and drafts may remain readable. Mutations are not blindly replayed: pending user messages have client IDs and require an explicit retry after authoritative session reconciliation. Approvals are never queued offline because they may expire or refer to a changed run.

## Motion system

- Onboarding: one state machine moves from architecture explanation to backend link. Back reverses the same spatial transition.
- Master/detail: selecting a session moves from atlas to the conversation destination; predictive back reverses it.
- Tool activity: request inserts one stable row, progress modifies it, completion settles the same identity.
- Approval/clarification: a blocking prompt is owned by its originating runtime session and cannot be dismissed into a lost state.
- Connection loss changes a narrow status line; the transcript does not flash or relayout.
- Reduced-motion mode replaces spatial transitions with immediate changes or short fades. Streaming content never animates each token.

Interrupted animations settle to semantic state. Process recreation restores the destination, not an animation phase.

## Accessibility

Minimum 48dp controls, scalable text, semantic headings, labelled icon actions, logical master/detail traversal and no colour-only state. Streaming updates do not continuously steal TalkBack focus. Hardware Enter sends only from the composer; Escape/back affects the topmost owned interaction once. Tool state combines icon, text and colour.
