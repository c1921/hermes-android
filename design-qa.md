# Design QA

Source visual truth:

- `/tmp/codex-clipboard-5d707171-d7f5-4d6d-8c6a-c11b1fbcd3af.png`
- `/tmp/codex-clipboard-a9c22e0d-24a3-4898-b6ea-60e912680679.png`
- `/tmp/codex-clipboard-b3100465-2e09-4250-b14d-fe29d67a2ff6.png`
- Live token and asset source: `https://hermes-agent.nousresearch.com/`

Implementation evidence:

- Onboarding: `/tmp/hermes-android-qa-qxuQgq/nous-brand-onboarding-v3.png`
- Backend form: `/tmp/hermes-android-qa-qxuQgq/nous-brand-backend-link-v1.png`
- Dark session atlas: `/tmp/hermes-android-qa-qxuQgq/nous-brand-session-atlas-final.png`
- Light session atlas: `/tmp/hermes-android-qa-qxuQgq/nous-brand-session-atlas-light-v1.png`
- Skills management: `/tmp/hermes-android-qa-qxuQgq/nous-brand-skills-final-v3.png`
- Session atlas at 130% text: `/tmp/hermes-android-qa-qxuQgq/nous-brand-atlas-text130.png`
- Skills management at 130% text: `/tmp/hermes-android-qa-qxuQgq/nous-brand-skills-text130.png`
- Launcher icon: `/tmp/hermes-android-qa-qxuQgq/hermes-launcher-search.png`
- Rounded session search: `/tmp/hermes-android-qa-qxuQgq/nous-brand-session-search-v1.png`
- Full comparison: `/tmp/hermes-android-qa-qxuQgq/design-qa-full-v2.png`
- Focused comparison: `/tmp/hermes-android-qa-qxuQgq/design-qa-focus-v2.png`

Viewport: physical Samsung SM-S906E, 1080 x 2340 pixels at 450 dpi, approximately 360dp wide. The source references are desktop views, so the comparison normalizes visual language, content order, hierarchy, and asset treatment while treating the single-column mobile layout as an intentional platform adaptation.

State: dark onboarding for the primary comparison; dark and light authenticated empty-session states plus the rounded credential form were checked as supporting states.

## Findings

No actionable P0, P1, or P2 visual mismatch remains.

- Fonts and typography: the implementation reproduces the high-contrast editorial display/monospaced utility pairing, weight contrast, uppercase labels, and compact line height. Courier Prime is exact. Cormorant Garamond is an intentional open-licensed substitute because the site's Sigurd license does not permit unverified app embedding.
- Spacing and layout rhythm: the desktop split hero becomes a single-column mobile sequence without hiding the primary action. The CTA precedes the dominant artwork, margins are consistent, touch targets remain at least 48dp, and all interactive containers use the shared rounded shape scale.
- Colors and visual tokens: `#0000F2`, `#F5F5F5`, `#FFFFFF`, and `#EDFF45` map directly from the live site. Light mode uses the source's paper/blue reversal; dark mode uses the electric-blue field and off-white foreground.
- Image quality and asset fidelity: the hero, badge, and launcher artwork are official first-party files. They are sharp at device density, preserve transparency and aspect ratio, and are not generated or code-drawn substitutes.
- Copy and content: site language anchors the onboarding hierarchy while Android-specific copy accurately describes the existing backend relationship and security boundary.
- Icons and controls: Material outlined icons provide a consistent native stroke family. Rounded buttons and fields are an intentional requirement from the product owner, replacing the site's square web controls without changing hierarchy.
- Accessibility and responsiveness: the implementation was checked in dark and light modes, at 130% text, with the IME visible, and with animator scale disabled. The onboarding and form scroll, the CTA remains reachable, and no labels clip on the 360dp device.

## Comparison history

### Iteration 1

Earlier finding: P2, onboarding hero was too small relative to the source and appeared before the primary CTA, weakening the source composition and making the CTA feel secondary.

Fix: moved the CTA directly below the heading/body, increased the official hero artwork from a 180dp to 280dp maximum height, and moved the connection architecture strip below the image.

Post-fix evidence: `/tmp/hermes-android-qa-qxuQgq/design-qa-full-v2.png` and `/tmp/hermes-android-qa-qxuQgq/design-qa-focus-v2.png`. The artwork now owns the lower half of the mobile hero, the CTA is above it and immediately reachable, and the heading-to-art proportions track the source while fitting the phone viewport.

## Open questions

- If Nous supplies a Sigurd application-embedding license and font file, replace the Cormorant Garamond fallback for exact display-glyph parity.

## Implementation checklist

- [x] Exact live-site palette tokens
- [x] Real first-party hero and badge assets
- [x] Official Desktop launcher icon
- [x] Courier Prime utility typography
- [x] Licensed high-contrast display fallback
- [x] Rounded shape system across controls and text fields
- [x] Physical-device dark/light, text-scale, IME, motion, and connection checks
- [x] Full and focused source-to-device comparison

## Follow-up polish

- P3: add the site's subtle paper/noise treatment only if Nous provides a reusable licensed raster texture appropriate for Android; do not recreate it with custom SVG or procedural art.

final result: passed
