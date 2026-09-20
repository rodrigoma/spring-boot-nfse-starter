package io.github.rodrigoma.nfse.xml

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.xml.crypto.KeySelector
import javax.xml.crypto.KeySelectorResult
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.crypto.dsig.DigestMethod
import javax.xml.crypto.dsig.SignatureMethod
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLSignature
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMSignContext
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.spec.TransformParameterSpec

/**
 * XML-DSig (enveloped) signer for the NFS-e documents, built only on `javax.xml.crypto`.
 *
 * RSA-SHA256 with SHA-256 digests (Anexo I names no algorithm; this is what the national emitter uses, and the JDK
 * refuses SHA-1 by default). The signature references the `Id` of the signed element (`infDPS`, `infPedReg`) with
 * the enveloped-signature and inclusive C14N transforms, uses inclusive C14N for `SignedInfo`, and carries the
 * certificate in `KeyInfo/X509Data`. It is marshalled in the default xmldsig namespace — the Sefin rejects
 * namespace prefixes (rule E1228).
 */
class XmlSigner(
    private val privateKey: PrivateKey,
    private val certificate: X509Certificate,
) {
    /**
     * Signs [target] (which must carry an `Id` attribute) and appends the `Signature` element to [parent].
     *
     * @return [document], for chaining.
     */
    fun sign(
        document: Document,
        target: Element,
        parent: Element,
    ): Document {
        val id = requireNotNull(target.getAttribute(ID_ATTRIBUTE).takeIf { it.isNotEmpty() }) { "Element has no Id" }
        target.setIdAttribute(ID_ATTRIBUTE, true)

        val factory = XMLSignatureFactory.getInstance("DOM")
        val reference =
            factory.newReference(
                "#$id",
                factory.newDigestMethod(DigestMethod.SHA256, null),
                listOf(
                    factory.newTransform(Transform.ENVELOPED, null as TransformParameterSpec?),
                    factory.newTransform(CanonicalizationMethod.INCLUSIVE, null as TransformParameterSpec?),
                ),
                null,
                null,
            )
        val signedInfo =
            factory.newSignedInfo(
                factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, null as C14NMethodParameterSpec?),
                factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                listOf(reference),
            )
        val keyInfoFactory = factory.keyInfoFactory
        val keyInfo = keyInfoFactory.newKeyInfo(listOf(keyInfoFactory.newX509Data(listOf(certificate))))

        val context = DOMSignContext(privateKey, parent)
        factory.newXMLSignature(signedInfo, keyInfo).sign(context)
        return document
    }

    companion object {
        const val ID_ATTRIBUTE = "Id"

        /**
         * Verifies the first `Signature` of [document] with the certificate embedded in its `KeyInfo`.
         * Elements carrying an `Id` attribute are registered so `#id` references resolve.
         */
        fun verify(document: Document): Boolean {
            registerIds(document.documentElement)
            val signatureNode =
                document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature").item(0) ?: return false
            val context = DOMValidateContext(X509KeySelector, signatureNode)
            val signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context)
            return signature.validate(context)
        }

        private fun registerIds(element: Element) {
            if (element.hasAttribute(ID_ATTRIBUTE)) element.setIdAttribute(ID_ATTRIBUTE, true)
            val children = element.childNodes
            for (i in 0 until children.length) {
                (children.item(i) as? Element)?.let { registerIds(it) }
            }
        }
    }

    /** Picks the public key of the first `X509Certificate` in `KeyInfo`. */
    private object X509KeySelector : KeySelector() {
        override fun select(
            keyInfo: javax.xml.crypto.dsig.keyinfo.KeyInfo?,
            purpose: Purpose?,
            method: javax.xml.crypto.AlgorithmMethod?,
            context: javax.xml.crypto.XMLCryptoContext?,
        ): KeySelectorResult {
            val certificate =
                keyInfo
                    ?.content
                    ?.filterIsInstance<javax.xml.crypto.dsig.keyinfo.X509Data>()
                    ?.flatMap { it.content }
                    ?.filterIsInstance<X509Certificate>()
                    ?.firstOrNull()
                    ?: throw javax.xml.crypto.KeySelectorException("No X509Certificate in KeyInfo")
            return KeySelectorResult { certificate.publicKey }
        }
    }
}
