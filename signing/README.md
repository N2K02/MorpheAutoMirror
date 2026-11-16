# Signing — shared mRadio identity

MorpheAutoMirror uses the same encrypted signing bundle and APK signature lineage as mRadio.

- Encrypted bundle: `signing/keys.tar.enc` (identical Git blob to N2K02/MRadio).
- Repository Actions secret: `SIGNING_PASSPHRASE` (same passphrase used for mRadio).
- OpenSSL: AES-256-CBC, PBKDF2, 600000 iterations.
- During CI, decrypt into a private runner temp directory, create the lineage from the old test key to the release key, sign using `apksigner`, then destroy temporary key files.
- Experimental and stable builds share this signing identity, as explicitly requested. Preserve the key and passphrase for future updates.
- Never commit decrypted keys, passphrase, or keystore passwords.

The private signing secret still needs to be configured in this repository's GitHub Actions settings. Separate apps have distinct package IDs even when the signer is shared.
