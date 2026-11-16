# Signing secrets

Add GitHub Actions repository secrets SIGNING_KEYSTORE_BASE64 (single-line base64 JKS),
SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD, and N2K02_PACKAGES_TOKEN.

The ephemeral runner writes signing/release.jks temporarily and removes it after APK signing.
Never commit keystores or secret material. Use the **same certificate** for every experimental
and stable build of MorpheAutoMirror. Stable GitHub Releases fail closed if signing is absent.

