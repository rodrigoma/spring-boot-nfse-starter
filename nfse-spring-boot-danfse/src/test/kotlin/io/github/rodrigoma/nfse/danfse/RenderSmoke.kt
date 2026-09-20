package io.github.rodrigoma.nfse.danfse

import java.nio.file.Files
import java.nio.file.Path

/** Not a test: writes the fixtures as PDFs for a visual check (`./gradlew :nfse-spring-boot-danfse:renderSamples`). */
object RenderSmoke {
    @JvmStatic
    fun main(args: Array<String>) {
        val out = Path.of(args.firstOrNull() ?: "build/danfse")
        Files.createDirectories(out)
        val renderer = DanfseRenderer(DanfseOptions(stub = true))
        Files.write(out.resolve("minimal.pdf"), renderer.render(Fixtures.minimal))
        Files.write(out.resolve("complete.pdf"), renderer.render(Fixtures.complete, NoteStatus.SUBSTITUTED))
        Files.write(
            out.resolve("minimal-cancelled.pdf"),
            DanfseRenderer().render(Fixtures.minimal, NoteStatus.CANCELLED),
        )
    }
}
