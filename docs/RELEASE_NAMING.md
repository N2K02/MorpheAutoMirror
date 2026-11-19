# Integrated ADB release filename contract

The `main-adb` branch is reserved for the **integrated ADB implementation**, distinct from the Shizuku Server `main-shizuku` branch.

Once the integrated ADB implementation is present and its APK build is green, publish only these installable, signed release assets:

- Experimental: `MorpheAutoMirror-ADB-<version>-experimental.<sequence>.apk`
- Stable: `MorpheAutoMirror-ADB-<version>.apk`

The Shizuku release branch must publish:

- Experimental: `MorpheAutoMirror-SHIZUKU-<version>-experimental.<sequence>.apk`
- Stable: `MorpheAutoMirror-SHIZUKU-<version>.apk`

The updater must select releases by installed architecture and selected stable/experimental channel. Do not republish the Shizuku implementation under an ADB filename. Do not claim an integrated ADB APK exists until its actual implementation, signing, tests, and release workflow pass.

Stable publication is held until the corresponding experimental changes are approved for merging.
