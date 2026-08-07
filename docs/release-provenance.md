# Android build provenance

Every Hermes Android variant gets its build identity from the Gradle metadata
defined in [`app/build.gradle.kts`](../app/build.gradle.kts). The generated
`BuildConfig` fields are exposed through
[`BuildProvenanceSource`](../app/src/main/java/com/nousresearch/hermes/provenance/BuildProvenance.kt),
which is the shared source for Diagnostics and the redacted support report.

The CI workflow also runs `:app:writeBuildProvenance` and retains the generated
variant properties file with the APK/AAB. Main release notes embed that same
file, so the release description, attached artifact, Diagnostics, and support
export cannot silently drift.

The metadata includes:

- semantic Android version, monotonic version code, variant channel, package,
  and Android commit;
- the full audited Hermes commit, Agent/Desktop versions, and exact audited
  version ranges;
- the SHA-256 toolchain/dependency-input digest, CI/build identity, and signing
  certificate fingerprint when a configured keystore is available; and
- the generated author `luinbytes`.

Local builds use explicit `unknown`, `local`, or `unsigned` fallbacks when CI,
Git, or signing inputs are unavailable. These values are tested as fallbacks;
they are never presented as verified release metadata.
