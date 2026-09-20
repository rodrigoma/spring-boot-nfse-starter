package io.github.rodrigoma.nfse.danfse

internal object Fixtures {
    fun xml(name: String): String =
        requireNotNull(Fixtures::class.java.getResourceAsStream("/danfse/$name")) { "$name missing" }
            .readBytes()
            .toString(Charsets.UTF_8)

    val minimal: String get() = xml("nfse-minimal.xml")
    val complete: String get() = xml("nfse-complete.xml")
}
