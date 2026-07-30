# GD User List App security review

Review date: 2026-07-30  
Scope: Android application source, merged manifests, local storage, networking,
logging, signing configuration, and GitHub Actions.

## Data and trust boundaries

The Room cache contains public-directory data (names, contact details and employer
details) plus user-created personal notes. Notes and authentication state are
sensitive. The access token is sensitive but process-only; it is not persisted.
Firebase `google-services.json`, the DummyJSON base URL and the WebSocket URL are
public Android client configuration, not credentials. Their abuse must be limited
server-side with API restrictions, authorization, quotas and monitoring; hiding
them in the APK is not a security control.

The app trusts its own private sandbox, Android Keystore, system certificate
authorities in production, and the configured backend. Debug builds additionally
trust user-installed certificate authorities for intentional proxy inspection.

## Findings and remediation

| ID | Finding and source | Potential impact | Priority | MASVS area | Resolution |
|---|---|---|---|---|---|
| S-01 | Personal notes were plaintext in `users.db` (`user_notes.note`). | A database disclosure, backup mistake, rooted-device access or forensic extraction reveals private notes. | High | MASVS-STORAGE, MASVS-CRYPTO | Fixed: Tink AES-256-GCM AEAD, versioned payloads, user ID as associated data, encrypted keyset protected by an Android Keystore master key. |
| S-02 | Debug `QualityTestReceiver` was exported without permission. | Another app could request telemetry or deliberately crash a debug installation. | High | MASVS-PLATFORM | Fixed: receiver is debug-only and `exported=false`. |
| S-03 | Production did not declare an explicit Network Security Configuration. | A later target/build change could weaken an implicit cleartext/trust assumption without an obvious review point. | Medium | MASVS-NETWORK | Fixed: release policy blocks cleartext and trusts system CAs only; debug resource override additionally trusts user CAs. |
| S-04 | OkHttp logging library was an `implementation` dependency, although enabled only behind `BuildConfig.DEBUG`. | A future configuration error could enable sensitive request logging in release; the release artifact unnecessarily contained logging code. | Medium | MASVS-PRIVACY, MASVS-NETWORK | Fixed: dependency and implementation are debug-only; BASIC logging does not log bodies/headers and sensitive headers are redacted defensively. |
| S-05 | Local release signing existed, but no ephemeral CI reconstruction and verification flow existed. | Manual key handling increases leakage and unsigned/wrongly signed artifact risk. | High | MASVS-CODE | Fixed: workflow reconstructs the upload keystore in `RUNNER_TEMP`, builds and verifies the AAB, deletes the keystore, and uploads only the AAB. |
| S-06 | Firebase API key is committed in `google-services.json`. | The value can be copied from either the repository or APK and used against insufficiently restricted Google APIs. | Medium | MASVS-AUTH, MASVS-RESILIENCE | Accepted as public client configuration. Restrict the key to the Android package/signing certificate and only required APIs in Google Cloud/Firebase; monitor quotas. |
| S-07 | The app caches directory PII beyond the user-created note. | Device compromise exposes cached names/contact/company data even though notes are encrypted. | Medium | MASVS-STORAGE, MASVS-PRIVACY | Accepted for the offline product requirement. All app data is excluded from backup and device transfer; consider retention/eviction and database encryption if the threat model expands. |

## Implemented controls

- `allowBackup=false` is defense in depth. Android 11-and-lower
  `fullBackupContent` and Android 12+ `dataExtractionRules` independently exclude
  files, databases, shared preferences and root-domain data from cloud backup; the
  Android 12+ rules also exclude device-to-device transfer. This covers Room,
  authentication DataStore, avatar files and the encrypted Tink keyset.
- Notes use a string envelope `enc:v1:<base64 ciphertext>`. AEAD associated data is
  `user-note:<userId>:1`, so moving ciphertext to another user fails
  authentication. Existing values without the prefix are treated as legacy
  plaintext, returned without data loss, encrypted, and replaced with a
  compare-and-set SQL update. Re-reading encrypted data is a no-op, making the
  migration idempotent.
- The Tink keyset contains the data-encryption key used for note payloads. The
  serialized keyset is itself encrypted by the master key whose key material is
  held by Android Keystore. Losing or invalidating that master key means existing
  notes cannot be decrypted; silently generating a replacement key would destroy
  recoverability and is intentionally not attempted.
- Release accepts signing values only from ignored `keystore.properties` or the
  `RELEASE_*` environment variables. CI uses GitHub Secrets named
  `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
  `RELEASE_KEY_PASSWORD`.

## Certificate pinning decision

Pinning is not appropriate for the current DummyJSON/Postman-hosted training
backends. The app does not control their certificate lifecycle, there is no
operational backup-pin/rotation process, and pinning would add availability risk
without fixing authentication or authorization. System trust, HTTPS-only policy
and normal certificate validation are proportionate. Reconsider pinning only for
an owned high-value backend with a documented rotation and emergency recovery
process.

## Validation

- Unit tests cover AEAD encryption/decryption, lack of plaintext in the envelope,
  user-ID binding, tamper detection and legacy plaintext recognition.
- Room instrumented tests cover encrypted-at-rest persistence, transparent
  decryption, data preservation and idempotent plaintext migration. A platform
  integration test verifies that the Android Keystore-backed master key produces
  a usable encrypted Tink keyset.
- Debug and release Kotlin compilation passed.
- All debug unit tests passed. All 26 instrumented tests passed on the API 33
  `Medium_Phone` AVD, including the encrypted-at-rest and migration cases.
- Debug and release lint passed. `assembleDebug` and `bundleRelease` passed.
- Merged debug/release manifests were inspected: the debug receiver is not
  exported and both variants reference the backup/data-extraction/network rules.
  The release bundle contains all three packaged XML resources.
- Gradle dependency inspection confirmed `logging-interceptor` is absent from
  `releaseRuntimeClasspath` and present only in `debugRuntimeClasspath`.
- The locally built AAB was signed with the ignored local upload key and
  `jarsigner -verify` reported `jar verified`.
- GitHub-hosted workflow execution is a remaining external validation: repository
  secrets and a manual `workflow_dispatch` run are required.

## Accepted risks and limitations

- Android Keystore protects keys from extraction but cannot protect plaintext
  while the unlocked app process is using it. Root, runtime instrumentation,
  screenshots, accessibility abuse and compromised OS scenarios are outside this
  exercise.
- Encryption does not provide rollback protection for an attacker capable of
  restoring an older database snapshot.
- GitHub Secrets mask registered values in logs and restrict normal workflow
  access, but repository administrators, compromised actions, malicious workflow
  changes and secrets deliberately transformed before output remain relevant
  threats. The workflow therefore has read-only permissions, uses official
  actions, does not print secrets, keeps the keystore outside the workspace and
  uploads only the signed bundle.
- The workflow currently references versioned official action tags. For stronger
  supply-chain control, pin actions to reviewed full commit SHAs and use an update
  bot.

## Review references

- [Android security checklist](https://developer.android.com/privacy-and-security/security-tips)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)
- [OWASP MASVS](https://mas.owasp.org/MASVS/)
- [Tink AEAD](https://developers.google.com/tink/aead)
- [GitHub Actions secrets](https://docs.github.com/actions/security-guides/using-secrets-in-github-actions)
