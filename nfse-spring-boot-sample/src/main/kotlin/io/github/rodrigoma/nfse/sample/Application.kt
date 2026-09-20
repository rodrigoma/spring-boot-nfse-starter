package io.github.rodrigoma.nfse.sample

import io.github.rodrigoma.nfse.sample.local.LocalSefin
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.MapPropertySource

@SpringBootApplication
class Application

private const val LOCAL_PROFILE = "local"
private const val LOCAL_CNPJ = "12345678000195"

/**
 * With `--spring.profiles.active=local` (or `SPRING_PROFILES_ACTIVE=local`) a fake Sefin Nacional is started first
 * and the starter is pointed at it, so the whole flow can be tried without an ICP-Brasil certificate.
 */
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    val profiles =
        (
            args.firstOrNull { it.startsWith("--spring.profiles.active=") }?.substringAfter("=")
                ?: System.getenv("SPRING_PROFILES_ACTIVE")
        )?.split(",")
            .orEmpty()
    val builder = SpringApplicationBuilder(Application::class.java)
    if (LOCAL_PROFILE in profiles) {
        val sandbox = LocalSefin.start(LOCAL_CNPJ)
        // Highest precedence: overrides the placeholder certificate path of application.yml
        builder.initializers(
            ApplicationContextInitializer<ConfigurableApplicationContext> {
                it.environment.propertySources.addFirst(MapPropertySource("local-sefin", sandbox))
            },
        )
    }
    builder.run(*args)
}
