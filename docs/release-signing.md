# Android signing and branch flow

All feature and fix work starts on `dev`. Pushes to `dev` run tests and lint, then publish `hermes-android-dev-debug` with the `.debug` application ID and a stable debug certificate. A release-worthy promotion to `main` opens an approval-gated release candidate PR. Merging that PR runs the full release gates and produces `hermes-android-release`, which contains a minified release APK, signed AAB, and R8 mapping file.

## Version and approval flow

The release workflow classifies the merged PR title using Conventional Commit rules: breaking changes bump the major version, `feat` bumps minor, and `fix`, `perf`, `revert`, dependency, build, or refactor changes bump patch. Documentation, test-only, CI-only, chore, style, and unclassified changes do not publish a release. The merged PR title is the release unit for that promotion.

For a release-worthy `main` push, CI increments the semantic version and monotonic Android `versionCode` in a `release/vX.Y.Z` PR. That PR is never auto-merged. Human approval and merge are required before the signed release job can create the `vX.Y.Z` tag or publish a GitHub Release.

The two branches use separate signing identities. Meteor's upload key is not shared with Hermes.

| Branch | Package | Key alias | Certificate SHA-256 |
| --- | --- | --- | --- |
| `dev` | `com.nousresearch.hermes.debug` | `hermes-debug` | `TODO(fork): replace with your own debug keystore SHA-256 (currently unset; CI skips the check until `HERMES_DEBUG_FINGERPRINT` is configured)` |
| `main` | `com.nousresearch.hermes` | `hermes-release` | `TODO(fork): replace with your own release keystore SHA-256 (currently unset; CI skips the check until `HERMES_RELEASE_FINGERPRINT` is configured)` |

## GitHub configuration

The stable debug identity is stored as repository Actions secrets:

- `HERMES_DEBUG_KEYSTORE_BASE64`
- `HERMES_DEBUG_KEYSTORE_PASSWORD`

The release identity is restricted to the `release` environment and that environment accepts deployments only from `main`:

- `HERMES_RELEASE_KEYSTORE_BASE64`
- `HERMES_RELEASE_KEYSTORE_PASSWORD`

The repository Actions settings must also allow workflows to create pull requests:

- Default workflow permissions: **Read and write permissions**.
- **Allow GitHub Actions to create and approve pull requests**: enabled.

The release-planning job requests `contents: write` and `pull-requests: write` for its candidate-branch and candidate-PR operations. The repository setting is required in addition to those workflow permissions.

Aliases are public workflow configuration. The workflow decodes each keystore only into the runner's temporary directory and requires signing configuration explicitly. Android Gradle signs the debug and release APKs plus the release AAB. The workflow signs the debug AAB explicitly because `bundleDebug` does not sign that output. It then verifies each APK with `apksigner`, checks every APK and AAB certificate fingerprint against the table above, and uploads artifacts only after verification succeeds.

Each approved release build publishes one semantic GitHub Release tagged `vX.Y.Z` and marks it as the latest release only after the verified APK, AAB, R8 mapping, `hermes-android-release.provenance.properties`, CycloneDX SBOM, and `hermes-android-release.sha256` manifest are attached. The release job verifies the package, version, versionCode, commit, signing certificate, provenance, and public-download checksums before completing. The release notes embed the same generated provenance properties used by Diagnostics and the support export. The README uses GitHub's stable latest-release URLs for `hermes-android-release.apk` and `hermes-android-release.aab`, so non-release pushes cannot replace the current downloads.

Never commit a keystore or password. Back up the external PKCS12 files and their passwords together. Losing the release identity prevents Android from accepting direct updates signed by this project. Google Play distribution should enable Play App Signing and retain this release identity as the upload key.
