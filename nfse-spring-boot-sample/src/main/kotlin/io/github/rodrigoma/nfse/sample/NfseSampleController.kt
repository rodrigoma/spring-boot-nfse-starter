package io.github.rodrigoma.nfse.sample

import io.github.rodrigoma.nfse.client.NfseClient
import io.github.rodrigoma.nfse.model.dps.Amounts
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.dps.MunicipalTax
import io.github.rodrigoma.nfse.model.dps.Person
import io.github.rodrigoma.nfse.model.dps.Taxes
import io.github.rodrigoma.nfse.model.event.CancellationReason
import io.github.rodrigoma.nfse.model.event.NfseEvent
import io.github.rodrigoma.nfse.model.request.DpsRequest
import io.github.rodrigoma.nfse.model.request.ServiceRequest
import io.github.rodrigoma.nfse.model.response.Nfse
import io.github.rodrigoma.nfse.model.response.NfseResult
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/**
 * Emits a demo NFS-e in restricted production from the values in `application.yml`.
 *
 * The DPS number comes from an in-memory counter — a real application persists it (the library keeps no state).
 */
@RestController
@RequestMapping("/sample/nfse")
class NfseSampleController(
    private val nfseClient: NfseClient,
) {
    private val nextNumber = AtomicLong(1)

    data class DemoRequest(
        val takerDocument: String,
        val takerName: String,
        val nationalTaxCode: String,
        val description: String,
        val amount: BigDecimal,
        val rate: BigDecimal? = null,
    )

    data class CancelRequest(
        val reason: CancellationReason = CancellationReason.ISSUANCE_ERROR,
        val justification: String,
    )

    @PostMapping
    fun emit(
        @RequestBody demo: DemoRequest,
    ): NfseResult =
        nfseClient.emit(
            DpsRequest(
                number = nextNumber.getAndIncrement(),
                competenceDate = LocalDate.now(),
                taker = Person(id = FederalId.cnpjOrCpf(demo.takerDocument), name = demo.takerName),
                service = ServiceRequest(nationalTaxCode = demo.nationalTaxCode, description = demo.description),
                amounts =
                    Amounts(
                        serviceAmount = demo.amount,
                        taxes = Taxes(municipal = MunicipalTax(rate = demo.rate)),
                    ),
            ),
        )

    @GetMapping("/{accessKey}")
    fun get(
        @PathVariable accessKey: String,
    ): Nfse = nfseClient.get(accessKey)

    @GetMapping("/{accessKey}/events")
    fun events(
        @PathVariable accessKey: String,
    ): List<NfseEvent> = nfseClient.events(accessKey)

    @GetMapping("/{accessKey}/danfse", produces = [MediaType.APPLICATION_PDF_VALUE])
    fun danfse(
        @PathVariable accessKey: String,
    ): ByteArray = nfseClient.danfse(accessKey)

    @DeleteMapping("/{accessKey}")
    fun cancel(
        @PathVariable accessKey: String,
        @RequestBody request: CancelRequest,
    ): NfseEvent = nfseClient.cancel(accessKey, request.reason, request.justification)
}
