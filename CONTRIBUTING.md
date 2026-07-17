# Contributing

Use JDK 17 and Android SDK 35. Keep changes scoped and run `./gradlew check` before proposing them.

Protocol work must cite the matching Hermes source or contract test. Do not add guessed fields, hard-coded model catalogues, silent compatibility fallbacks, raw secret logging, unbounded retries, or UI controls without a working backend path.

When changing protocol behaviour:

1. Add or update a pinned JSON fixture.
2. Add a reducer or transport contract test.
3. Update the parity matrix.
4. State the oldest verified Hermes version.
5. Preserve unknown-event tolerance.

Do not open upstream Hermes PRs from automation. Upstream patches belong in reviewable local branches or patch files until a maintainer of this repository chooses to submit them.

