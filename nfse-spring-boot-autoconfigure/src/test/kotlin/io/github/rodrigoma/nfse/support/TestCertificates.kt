package io.github.rodrigoma.nfse.support

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
import org.bouncycastle.cert.X509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * Certificates minted at test time — nothing is checked into the repository. The emitter certificate mimics an
 * ICP-Brasil e-CNPJ (key usages + the CNPJ `otherName`); the server certificate is a plain `localhost` one.
 */
object TestCertificates {
    const val PASSWORD = "changeit"
    const val CNPJ = "12345678000195"
    const val CPF_OTHER_NAME_VALUE = "1980010112345678909" + "00000000000" + "000000000000000" + "SSPSP"

    data class Identity(
        val keyPair: KeyPair,
        val certificate: X509Certificate,
    ) {
        fun pkcs12(password: String = PASSWORD): ByteArray =
            KeyStore.getInstance("PKCS12").run {
                load(null, null)
                setKeyEntry("emitter", keyPair.private, password.toCharArray(), arrayOf(certificate))
                ByteArrayOutputStream().also { store(it, password.toCharArray()) }.toByteArray()
            }
    }

    @Suppress("LongParameterList")
    fun emitter(
        cnpj: String? = CNPJ,
        cpf: Boolean = false,
        keyUsages: Int? = KeyUsage.digitalSignature or KeyUsage.nonRepudiation or KeyUsage.keyEncipherment,
        ca: Boolean = false,
        expired: Boolean = false,
        version: Int = 3,
    ): Identity {
        val keyPair = newKeyPair()
        val now = Instant.now()
        val notBefore = if (expired) now.minus(400, ChronoUnit.DAYS) else now.minus(1, ChronoUnit.DAYS)
        val notAfter = if (expired) now.minus(1, ChronoUnit.DAYS) else now.plus(365, ChronoUnit.DAYS)
        val subject = X500Name("CN=EMPRESA TESTE:${cnpj ?: "00000000000000"},OU=Certificado PJ A1,O=ICP-Brasil,C=BR")
        val from = Date.from(notBefore)
        val until = Date.from(notAfter)
        val certificate =
            if (version == 1) {
                val builder: X509v1CertificateBuilder =
                    JcaX509v1CertificateBuilder(subject, serial(), from, until, subject, keyPair.public)
                JcaX509CertificateConverter().getCertificate(builder.build(signer(keyPair)))
            } else {
                val builder = JcaX509v3CertificateBuilder(subject, serial(), from, until, subject, keyPair.public)
                builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
                keyUsages?.let { builder.addExtension(Extension.keyUsage, true, KeyUsage(it)) }
                builder.addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth))
                val names = mutableListOf<GeneralName>()
                cnpj?.let { names += otherName("2.16.76.1.3.3", it) }
                if (cpf) names += otherName("2.16.76.1.3.1", CPF_OTHER_NAME_VALUE)
                if (names.isNotEmpty()) {
                    builder.addExtension(Extension.subjectAlternativeName, false, GeneralNames(names.toTypedArray()))
                }
                JcaX509CertificateConverter().getCertificate(builder.build(signer(keyPair)))
            }
        return Identity(keyPair, certificate)
    }

    fun server(host: String = "localhost"): Identity {
        val keyPair = newKeyPair()
        val now = Instant.now()
        val subject = X500Name("CN=$host")
        val builder =
            JcaX509v3CertificateBuilder(
                subject,
                serial(),
                Date.from(now.minus(1, ChronoUnit.DAYS)),
                Date.from(now.plus(30, ChronoUnit.DAYS)),
                subject,
                keyPair.public,
            )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.extendedKeyUsage, false, ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
        builder.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(
                arrayOf(GeneralName(GeneralName.dNSName, host), GeneralName(GeneralName.iPAddress, "127.0.0.1")),
            ),
        )
        return Identity(keyPair, JcaX509CertificateConverter().getCertificate(builder.build(signer(keyPair))))
    }

    fun trustStoreOf(vararg certificates: X509Certificate): KeyStore =
        KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            certificates.forEachIndexed { index, certificate -> setCertificateEntry("trusted-$index", certificate) }
        }

    private fun otherName(
        oid: String,
        value: String,
    ): GeneralName =
        GeneralName(
            GeneralName.otherName,
            DERSequence(arrayOf(ASN1ObjectIdentifier(oid), DERTaggedObject(true, 0, DERPrintableString(value)))),
        )

    private fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun serial(): BigInteger = BigInteger.valueOf(System.nanoTime())

    private fun signer(keyPair: KeyPair) = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
}
