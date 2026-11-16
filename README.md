# MorpheAutoMirror

A standalone **parked-only experimental** Android Auto companion for Morphe-patched YouTube. A notification listener watches only the configured Morphe package, checks its media session and starts a MediaProjection session after a fresh user consent. An Android Auto Car App Library session supplies a surface for mirroring.

## Operation

1. Install and open the application, grant Notification access, and turn on Automatic monitoring.
2. Open Morphe YouTube (`app.morphe.android.youtube` by default). Play a video.
3. Connect Android Auto and open **MorpheAutoMirror** in its launcher (the Android Auto host cannot be forced to switch apps).
4. Confirm the vehicle is parked, then grant screen-capture permission on the phone for that session.
5. Video is mirrored through a direct MediaProjection → car surface; choose WebView experiment for the slower local MJPEG path.
6. Notification provides Stop mirroring. Pausing, stopping Morphe, disconnecting or Quit tears down capture.

Video **never bypasses DRM**: secure/protected frames may be black. Never interact with video while driving. Android Auto does not provide a universal parked-state API; the required user confirmation is a safeguard, not vehicle-motion detection.

## Constraints

- Unofficial Android Auto integration uses the navigation surface privilege for personal testing. Not eligible for Play Store distribution as a video or browser app.
- User permission is required per MediaProjection session on Android 14+. Reconnection may require consent again.
- Android Auto must launch this app to create the surface; Morphe playback detection itself does not launch the host screen.
- On some head units the app may not appear even with developer options. Debug on Android Auto Desktop Head Unit first.
- WebView experimental renderer is limited to 480p (or less), 5fps, JPEG compression; **direct renderer recommended**.
- Audio stays with Morphe/Android Auto. The mirror does not capture or replay audio.

## Signing and releases

Tests run on every PR and main commit. One reusable experimental PR is used per app. Signed experimental prereleases and stable GitHub Releases currently use the four app-specific signing secrets:

`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

Unlike mRadio, which decrypts its tracked private signing bundle using `SIGNING_PASSPHRASE`, this new app keeps its own keystore provided through repository secrets until an app-specific encrypted bundle is prepared. Do not copy mRadio signing material without explicitly choosing shared-signature identity. 

The CI checks out the public shared N2K02 theme/updater and uses Gradle composite builds, so no package token is required for verification or release compilation. A `N2K02_PACKAGES_TOKEN` can be used for Maven builds instead. These credentials are read by CI only and are never checked into source. Use the same keystore for **all versions of this app** (do not inadvertently change the certificate after installing). Release versioning now follows mRadio: experimental APKs use the next stable base with sequential `-experimental.N` naming; releases have monotonic version codes, generated changelogs, and stable versions advance by 0.1. Experimental tags remain `experimental-pr-<PR>-run-<run>`. The workflow reuses PR #1. Review CI before merging.

## Local build

Requires Java 17, Gradle 9.9, Android SDK 37. Clone the public theme/updater repositories into `shared/n2k02theme` and `shared/n2k02updater` (or use a GitHub Packages token).

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PuseSharedSources=true
```

No claim of physical-head-unit testing is made until verified on an actual car.

