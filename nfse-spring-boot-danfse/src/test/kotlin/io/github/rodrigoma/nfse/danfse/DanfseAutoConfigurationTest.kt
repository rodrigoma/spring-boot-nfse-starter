package io.github.rodrigoma.nfse.danfse

import io.github.rodrigoma.nfse.client.DanfsePdfRenderer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class DanfseAutoConfigurationTest {
    private val contextRunner =
        ApplicationContextRunner().withConfiguration(
            AutoConfigurations.of(DanfseAutoConfiguration::class.java),
        )

    @Test
    fun `registers the renderer by default and honours the stub option`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(DanfsePdfRenderer::class.java)
            assertThat(context.getBean(DanfseRenderer::class.java)).isNotNull()
        }
        contextRunner.withPropertyValues("nfse.danfse.stub=true").run { context ->
            val pdf = context.getBean(DanfseRenderer::class.java).render(Fixtures.minimal)
            assertThat(pdf.size).isGreaterThan(1000)
        }
    }

    @Test
    fun `can be disabled and backs off for a custom renderer`() {
        contextRunner.withPropertyValues("nfse.danfse.enabled=false").run { context ->
            assertThat(context).doesNotHaveBean(DanfsePdfRenderer::class.java)
        }
        contextRunner.withUserConfiguration(CustomRenderer::class.java).run { context ->
            assertThat(context).hasSingleBean(DanfsePdfRenderer::class.java)
            assertThat(context).doesNotHaveBean(DanfseRenderer::class.java)
        }
    }

    @Configuration(proxyBeanMethods = false)
    class CustomRenderer {
        @Bean
        fun renderer() = DanfsePdfRenderer { _, _ -> ByteArray(0) }
    }
}
