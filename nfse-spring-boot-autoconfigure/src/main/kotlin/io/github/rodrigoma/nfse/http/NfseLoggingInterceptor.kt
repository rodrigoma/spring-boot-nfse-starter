package io.github.rodrigoma.nfse.http

import org.slf4j.LoggerFactory
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Logs outgoing NFS-e requests and responses at `DEBUG` when `nfse.log-requests=true`.
 *
 * Headers are never logged and bodies go through [NfseBodyMasker], so the compressed XML payloads (with the parties'
 * personal data) never reach the log.
 */
class NfseLoggingInterceptor : ClientHttpRequestInterceptor {
    private val log = LoggerFactory.getLogger(NfseLoggingInterceptor::class.java)

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        if (log.isDebugEnabled) {
            log.debug(
                "--> {} {}{}",
                request.method,
                request.uri,
                if (body.isNotEmpty()) "\n${NfseBodyMasker.mask(body)}" else "",
            )
        }

        val response = execution.execute(request, body)

        if (log.isDebugEnabled) {
            val responseBody = response.body.readBytes()
            log.debug("<-- {} {}\n{}", response.statusCode.value(), request.uri, NfseBodyMasker.mask(responseBody))
            return BufferedClientHttpResponse(response, responseBody)
        }

        return response
    }
}

private class BufferedClientHttpResponse(
    private val delegate: ClientHttpResponse,
    private val bodyBytes: ByteArray,
) : ClientHttpResponse by delegate {
    override fun getBody(): InputStream = ByteArrayInputStream(bodyBytes)
}
