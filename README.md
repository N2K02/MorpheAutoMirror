# MorpheAutoMirror

A standalone **parked-only experimental** Android Auto companion for Morphe-patched YouTube. A notification listener watches only the automatically detected Morphe YouTube package, checks its media session and starts a MediaProjection session after a fresh user consent. An Android Auto Car App Library session supplies a surface for mirroring.

## Operation

1. Install and open the application. Monitoring automatically looks for Morphe YouTube (including `app.morphe.android.youtube`) without a package-name field.
2. Open Settings → Monitoring: verify the Morphe **Installed** badge and notification-listener **Granted** badge; use **Grant access** if needed. Enable Automatic Morphe listener.
3. Open Morphe YouTube and play a video.
4. Connect Android Auto and open **MorpheAutoMirror** in its launcher (the Android Auto host cannot be forced to switch apps).
5. Confirm the vehicle is parked, then grant screen-capture permission on the phone for that session.
6. Video is mirrored through a direct MediaProjection → car surface; choose GeckoView experiment for the slower local MJPEG path.
7. Notification provides Stop mirroring. Pausing, stopping Morphe, disconnecting or Quit tears down capture.

Video **never bypasses DRM**: secure/protected frames may be black. Never interact with video while driving. Android Auto does not provide a universal parked-state API; the required user confirmation is a safeguard, not vehicle-motion detection.

## Constraints

- Unofficial Android Auto integration uses the navigation surface privilege for personal testing. Not eligible for Play Store distribution as a video or browser app.
- User permission is required per MediaProjection session on Android 14+. Reconnection may require consent again.
- Android Auto must launch this app to create the surface; Morphe playback detection itself does not launch the host screen.
- On some head units the app may not appear even with developer options. Debug on Android Auto Desktop Head Unit first.
- GeckoView experimental renderer is limited to 480p (or less), 5fps, JPEG compression; **direct renderer recommended**.
- Audio stays with Morphe/Android Auto. The mirror does not capture or replay audio.

## Signing and releases

Tests run on every PR and main commit. The single reusable experimental PR uses mRadio's experimental naming, monotonic version codes, stable +0.1 version increments, changelog generation, and GitHub Releases.

The signing configuration now matches **mRadio exactly**: the committed encrypted `signing/keys.tar.enc` uses `SIGNING_PASSPHRASE` in repository Actions secrets, decrypts only inside CI, and uses the same key rotation lineage to sign experimental and stable APKs. The encrypted bundle is intentionally identical to mRadio's, as requested. Add `SIGNING_PASSPHRASE` in this repository's Actions secrets; no per-app JKS secrets are required. Do not commit the passphrase or decrypted keys.

Monitoring uses PackageManager discovery and actual notification-listener access checks; badges refresh on returning to the app. Ordinary YouTube is never treated as Morphe. Logs remain in Android Logcat for McpAdbAndroidLogger, not an in-app Logs page.

GeckoView uses the pinned Mozilla stable `org.mozilla.geckoview:geckoview:160.0.20261104181552` library, which significantly increases download and APK size. The experimental renderer uses a private session, disabled JavaScript, a TextureView on a car-directed virtual display, and a token-protected localhost-only HTML + MJPEG endpoint. It has **not** been validated on a physical Android Auto host; the direct renderer remains the default and preferred low-latency option. This is not a way to bypass Android Auto video restrictions or protected/DRM video.

The CI checks out the shared N2K02 theme/updater source and uses Gradle composite builds. Releases remain unverified until a green workflow and signed APK are available. Review the experimental PR before merging.

## Local build

Requires Java 17, Gradle 9.9, Android SDK 37. Clone the public theme/updater repositories into `shared/n2k02theme` and `shared/n2k02updater` (or use a GitHub Packages token).

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PuseSharedSources=true
```

No claim of physical-head-unit testing is made until verified on an actual car.

