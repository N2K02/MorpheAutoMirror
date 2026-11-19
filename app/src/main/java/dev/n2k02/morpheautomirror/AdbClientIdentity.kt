package dev.n2k02.morpheautomirror

import android.content.Context
import android.util.AtomicFile
import dev.n2k02.adb.core.AdbIdentity
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

/** LibAdbWire requires an exportable TLS key. Store it atomically in app-private, non-backup storage. */
internal object AdbClientIdentity {
    @Synchronized
    fun load(context: Context): AdbIdentity {
        val storage = AtomicFile(File(context.noBackupFilesDir, "wireless-adb-identity"))
        val certificates = CertificateFactory.getInstance("X.509")
        if (storage.baseFile.exists()) {
            return DataInputStream(storage.openRead()).use { input ->
                val length = input.readInt()
                check(length in 1..8192) { "Invalid stored ADB private key" }
                val key = ByteArray(length).also(input::readFully)
                AdbIdentity(
                    KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(key)),
                    certificates.generateCertificate(input),
                )
            }
        }

        val random = SecureRandom()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, random) }.generateKeyPair()
        val subject = X500Name("CN=MorpheAutoMirror,O=N2K02")
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val certificate = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(128, random).setBit(127),
            Date(now - day),
            Date(now + 30 * 365 * day),
            subject,
            keyPair.public,
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        val output = storage.startWrite()
        try {
            val key = keyPair.private.encoded
            val data = DataOutputStream(output)
            data.writeInt(key.size)
            data.write(key)
            data.write(certificate.encoded)
            data.flush()
            storage.finishWrite(output)
        } catch (error: Exception) {
            storage.failWrite(output)
            throw error
        }
        return AdbIdentity(keyPair.private, certificates.generateCertificate(certificate.encoded.inputStream()))
    }
}
