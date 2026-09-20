package io.github.rodrigoma.nfse.xml

import io.github.rodrigoma.nfse.exception.NfseException
import io.github.rodrigoma.nfse.support.TestDps
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class XsdValidatorTest {
    private val validator = XsdValidator.dps()

    @Test
    fun `accepts a valid DPS`() {
        assertThat(validator.validate(DpsXmlBuilder().buildUnvalidated(TestDps.minimal()))).isEmpty()
    }

    @Test
    fun `reports every violation with the schema error code`() {
        val document = DpsXmlBuilder().buildUnvalidated(TestDps.minimal())
        document.getElementsByTagNameNS(XmlSupport.NFSE_NAMESPACE, "cTribNac").item(0).textContent = "12"
        document.getElementsByTagNameNS(XmlSupport.NFSE_NAMESPACE, "CPF").item(0).textContent = "abc"

        val errors = validator.validate(document)

        // Xerces reports a pattern violation twice: the facet and the element
        assertThat(errors).hasSize(4)
        assertThat(errors).allMatch { it.code == XsdValidator.SCHEMA_ERROR_CODE }
        assertThat(errors.map { it.description }).anySatisfy { assertThat(it).contains("CPF") }
        assertThat(errors.map { it.description }).anySatisfy { assertThat(it).contains("cTribNac") }
        assertThatThrownBy { validator.validateOrThrow(document) }
            .isInstanceOf(NfseException.Validation::class.java)
            .hasMessageContaining("E1235")
    }

    @Test
    fun `resolves includes from the classpath even when the schemas live in a jar`(
        @TempDir dir: Path,
    ) {
        val jar = dir.resolve("schemas.jar")
        val directory = "META-INF/nfse/xsd/1.01"
        JarOutputStream(Files.newOutputStream(jar)).use { out ->
            listOf(
                "DPS_v1.01.xsd",
                "tiposComplexos_v1.01.xsd",
                "tiposSimples_v1.01.xsd",
                "xmldsig-core-schema.xsd",
            ).forEach { name ->
                out.putNextEntry(JarEntry("$directory/$name"))
                javaClass.classLoader.getResourceAsStream("$directory/$name")!!.use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        // A loader that sees nothing but the jar: every include has to come through the resource resolver
        val loader = URLClassLoader(arrayOf(jar.toUri().toURL()), null)
        val fromJar = XsdValidator("$directory/DPS_v1.01.xsd", loader)
        assertThat(fromJar.validate(DpsXmlBuilder().buildUnvalidated(TestDps.minimal()))).isEmpty()
    }

    @Test
    fun `loads every embedded schema`() {
        XsdValidator.eventRequest()
        XsdValidator.nfse()
        XsdValidator.event()
        assertThatThrownBy { XsdValidator("META-INF/nfse/xsd/missing.xsd") }.hasMessageContaining("missing")
    }
}
