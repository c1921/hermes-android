# README artwork

The three source PNGs are direct captures from a Samsung SM-S906E running Android 16 in dark mode. The onboarding image is a full-device ADB screenshot. The billing and Command Center images are deterministic Compose captures rendered on the same phone with non-secret showcase state.

`render.sh` adds only a rounded frame, shadow, border, and existing Nous backdrop artwork. The two isolated Compose captures reuse the real dark Samsung navigation bar from the onboarding capture instead of the test activity's pale system chrome. It does not retouch app UI content. The banner uses artwork and fonts already shipped by the app.

Regenerate the presentation assets from the repository root:

```bash
docs/assets/readme/render.sh
```
