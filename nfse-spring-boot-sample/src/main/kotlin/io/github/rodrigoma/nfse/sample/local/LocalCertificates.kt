package io.github.rodrigoma.nfse.sample.local

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERPrintableString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Self-signed certificates for the local sandbox: an "e-CNPJ" for the emitter (with the ICP-Brasil CNPJ extension
 * the library reads) and a `localhost` certificate for the fake Sefin. Nothing here is accepted by the real
 * Sistema Nacional — that needs an ICP-Brasil A1 certificate.
 */
internal object LocalCertificates {
    const val PASSWORD = "local"
    private const val CNPJ_OID = "2.16.76.1.3.3"
    private const val KEY_SIZE = 2048
    private const val VALIDITY_DAYS = 365L

    class Identity(
        val keyPair: KeyPair,
        val certificate: X509Certificate,
    ) {
        fun keyStore(alias: String): KeyStore =
            KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry(alias, keyPair.private, PASSWORD.toCharArray(), arrayOf(certificate))
            }
    }

    fun emitter(cnpj: String): Identity =
        build(X500Name("CN=EMITENTE LOCAL:$cnpj,O=Sandbox,C=BR"), KeyPurposeId.id_kp_clientAuth) { builder ->
            val otherName =
                GeneralName(
                    GeneralName.otherName,
                    DERSequence(
                        arrayOf(ASN1ObjectIdentifier(CNPJ_OID), DERTaggedObject(true, 0, DERPrintableString(cnpj))),
                    ),
                )
            builder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(otherName))
        }

    fun server(): Identity =
        build(X500Name("CN=localhost"), KeyPurposeId.id_kp_serverAuth) { builder ->
            val names =
                arrayOf(GeneralName(GeneralName.dNSName, "localhost"), GeneralName(GeneralName.iPAddress, "127.0.0.1"))
            builder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(names))
        }

    fun trustStoreFile(
        directory: Path,
        certificate: X509Certificate,
    ): Path {
        val store = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        store.setCertificateEntry("local-sefin", certificate)
        val file = directory.resolve("trust.p12")
        Files.newOutputStream(file).use { store.store(it, PASSWORD.toCharArray()) }
        return file
    }

    fun pfxFile(
        directory: Path,
        identity: Identity,
    ): Path {
        val file = directory.resolve("emitter.pfx")
        Files.newOutputStream(file).use { identity.keyStore("emitter").store(it, PASSWORD.toCharArray()) }
        return file
    }

    private fun build(
        subject: X500Name,
        purpose: KeyPurposeId,
        extensions: (JcaX509v3CertificateBuilder) -> Unit,
    ): Identity {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE) }.generateKeyPair()
        val now = Instant.now()
        val builder =
            JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(VALIDITY_DAYS, ChronoUnit.DAYS)),
                subject,
                keyPair.public,
            )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.nonRepudiation or KeyUsage.keyEncipherment),
        )
        builder.addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(purpose))
        extensions(builder)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return Identity(keyPair, JcaX509CertificateConverter().getCertificate(builder.build(signer)))
    }

/** Server-side TLS context: presents [serverIdentity] and accepts only [emitter] as client certificate. */
    fun mutualTls(
        serverIdentity: Identity,
        emitter: Identity,
    ): HttpsConfigurator {
        val keyManagers =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(serverIdentity.keyStore("server"), PASSWORD.toCharArray())
            }
        val trustedClients = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        trustedClients.setCertificateEntry("emitter", emitter.certificate)
        val trustManagers =
            TrustManagerFactory
                .getInstance(
                    TrustManagerFactory.getDefaultAlgorithm(),
                ).apply { init(trustedClients) }
        val sslContext =
            SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, trustManagers.trustManagers, null) }
        return object : HttpsConfigurator(sslContext) {
            override fun configure(params: HttpsParameters) {
                val parameters = sslContext.defaultSSLParameters
                parameters.needClientAuth = true
                params.setSSLParameters(parameters)
            }
        }
    }
}
