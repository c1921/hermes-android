# Android signing and branch flow

All feature and fix work starts on `dev`. Pushes to `dev` run tests and lint, then publish `hermes-android-dev-debug` with the `.debug` application ID and a stable debug certificate. Promote a verified `dev` commit to `main` to produce `hermes-android-release`, which contains a minified release APK, signed AAB, and R8 mapping file.

The two branches use separate signing identities. Meteor's upload key is not shared with Hermes.

| Branch | Package | Key alias | Certificate SHA-256 |
| --- | --- | --- | --- |
| `dev` | `com.nousresearch.hermes.debug` | `hermes-debug` | `2A:46:D0:02:22:F6:D6:FA:F3:0B:86:B9:FC:06:47:99:8B:25:A8:2A:2B:A1:53:1C:70:A8:4A:91:BB:B1:65:0C` |
| `main` | `com.nousresearch.hermes` | `hermes-release` | `31:38:E7:11:EF:88:12:91:C1:C3:94:95:C7:B5:5F:E8:16:B4:15:5A:97:73:51:16:BC:36:A7:20:97:DC:20:2D` |

## GitHub configuration

The stable debug identity is stored as repository Actions secrets:

- `HERMES_DEBUG_KEYSTORE_BASE64`
- `HERMES_DEBUG_KEYSTORE_PASSWORD`

The release identity is restricted to the `release` environment and that environment accepts deployments only from `main`:

- `HERMES_RELEASE_KEYSTORE_BASE64`
- `HERMES_RELEASE_KEYSTORE_PASSWORD`

Aliases are public workflow configuration. The workflow decodes each keystore only into the runner's temporary directory and requires signing configuration explicitly. Android Gradle signs the debug and release APKs plus the release AAB. The workflow signs the debug AAB explicitly because `bundleDebug` does not sign that output. It then verifies each APK with `apksigner`, checks every APK and AAB certificate fingerprint against the table above, and uploads artifacts only after verification succeeds.

Never commit a keystore or password. Back up the external PKCS12 files and their passwords together. Losing the release identity prevents Android from accepting direct updates signed by this project. Google Play distribution should enable Play App Signing and retain this release identity as the upload key.
