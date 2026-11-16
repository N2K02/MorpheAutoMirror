# MorpheAutoMirror

An experimental Android Auto companion for Morphe-patched YouTube, intended for testing while parked. A notification listener detects playback and Android Auto supplies a display Surface. It can request ordinary MediaProjection capture or try the opt-in scrcpy server via an explicitly authorized LocalAdb Shizuku Binder.

## Operation

1. Install MorpheAutoMirror and open it once. In Settings → Monitoring verify that Morphe YouTube is detected and grant notification-listener access.
2. Start LocalAdb, verify its embedded Shizuku manager is running, then open MorpheAutoMirror Settings → Shizuku access and explicitly grant permission. A visible Authorized (UID 2000) badge is required; merely installing LocalAdb is not enough.
3. Connect Android Auto, open MorpheAutoMirror on the car launcher and play Morphe on the phone while parked.
4. When the car Surface and playback are ready, authorized Shizuku enables the **experimental** scrcpy 3.3.4 server → on-device H.264 decoder → car Surface path. If Shizuku is not authorized, the previous MediaProjection → VirtualDisplay → car Surface path remains available and requires an on-phone consent prompt.
5. A pause does not cause Morphe playback controls to fire. Disconnecting, stopping playback or Quit ends the mirroring service. Audio is not captured or duplicated.

Android Auto cannot be forced to foreground this app, and OEMs may reject the navigation Surface. The current car Surface integration is **not independently tested** on a physical head unit. Do not use video while driving. Neither scrcpy nor Shizuku guarantees bypass of protected-video black frames or Android Auto host restrictions.

## Constraints

- Unofficial Android Auto integration uses the navigation surface privilege for personal testing. Not eligible for Play Store distribution as a video or browser app.
- MediaProjection fallback requires consent for each capture session on Android 14+. scrcpy instead requires an explicit LocalAdb/Shizuku permission grant (not silent authorization).
- Android Auto must launch this app to create the surface; Morphe playback detection itself does not launch the host screen.
- On some head units the app may not appear even with developer options. Debug on Android Auto Desktop Head Unit first.
- Two renderers are available: experimental scrcpy H.264 (requires authorized Shizuku shell), and a MediaProjection direct-surface fallback. scrcpy starts an upstream server on the same Android device and renders through MediaCodec to the car Surface; its extra encoding/decoding may increase latency.
- Audio stays with Morphe/Android Auto. The mirror does not capture or replay audio.

## Signing and releases

Tests run on every PR and main commit. The single reusable experimental PR uses mRadio's experimental naming, monotonic version codes, stable +0.1 version increments, changelog generation, and GitHub Releases.

The signing configuration now matches **mRadio exactly**: the committed encrypted `signing/keys.tar.enc` uses `SIGNING_PASSPHRASE` in repository Actions secrets, decrypts only inside CI, and uses the same key rotation lineage to sign experimental and stable APKs. The encrypted bundle is intentionally identical to mRadio's, as requested. Add `SIGNING_PASSPHRASE` in this repository's Actions secrets; no per-app JKS secrets are required. Do not commit the passphrase or decrypted keys.

Monitoring uses PackageManager discovery and actual notification-listener access checks; badges refresh on returning to the app. Ordinary YouTube is never treated as Morphe. Logs remain in Android Logcat for McpAdbAndroidLogger, not an in-app Logs page.

## Rendering architecture and scrcpy

```
Morphe media session → NotificationListenerService → MirrorGateway
Android Auto CarAppService → SurfaceCallback → car Surface
LocalAdb embedded Shizuku (explicit grant, UID 2000)
    → scrcpy 3.3.4 shell server → local abstract socket → MediaCodec H.264 → car Surface
Fallback without Shizuku
    → Android MediaProjection (on-phone consent) → VirtualDisplay → car Surface
```

The upstream scrcpy 3.3.4 server is downloaded only at build time from Genymobile's official release; `scripts/fetch-scrcpy-server.sh` verifies SHA-256 `8588238c9a5a00aa542906b6ec7e6d5541d9ffb9b5d0f6e1bc0e365e2303079e` before embedding the jar. scrcpy is Apache-2.0 licensed. The client implements only the pinned H.264/video socket protocol: audio and input injection are disabled. A server and client version mismatch is not supported.

Shizuku start-up and the local abstract Unix socket may be restricted by Android/OEM SELinux policies. Failure is logged under SCRCPY and must not be presented as successful mirroring. Physical Android Auto video and LocalAdb end-to-end tests are still required.

The CI checks out the shared N2K02 theme/updater source and uses Gradle composite builds. Releases remain unverified until a green workflow and signed APK are available. Review the experimental PR before merging.

## Local build

Requires Java 17, Gradle 9.9, Android SDK 37. Clone the public theme/updater repositories into `shared/n2k02theme` and `shared/n2k02updater` (or use a GitHub Packages token).

```bash
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PuseSharedSources=true
```

No claim of physical-head-unit testing is made until verified on an actual car.

