package io.github.rodrigoma.nfse.danfse.layout

import io.github.rodrigoma.nfse.danfse.DanfseOptions
import io.github.rodrigoma.nfse.danfse.NoteStatus
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.Color

/**
 * The DANFSe v2.0 page (NT 008/2026, Anexo I and table 2.4.5): fixed header and identification blocks, then the
 * party/service/tax/total blocks flowing down with the suppressions the NT allows, "Informações Complementares"
 * taking the space that is left, and the optional stub at the bottom. All measures are centimetres.
 */
@Suppress("TooManyFunctions")
internal class DanfseLayout(
    private val canvas: PdfCanvas,
    private val view: NfseView,
    private val options: DanfseOptions,
    private val logo: PDImageXObject?,
) {
    private class Cell(
        val x: Float,
        val width: Float,
        val label: String?,
        val value: String,
        val grey: Boolean = false,
        val upperLabel: Boolean = false,
    )

    fun draw(status: NoteStatus) {
        // The watermark goes first so the content stays legible on top of it
        when (status) {
            NoteStatus.CANCELLED -> canvas.watermark("CANCELADA")
            NoteStatus.SUBSTITUTED -> canvas.watermark("SUBSTITUÍDA")
            NoteStatus.ACTIVE -> Unit
        }
        canvas.pageBorder(PAGE_MARGIN, PAGE_MARGIN, PAGE_WIDTH - 2 * PAGE_MARGIN, PAGE_HEIGHT - 2 * PAGE_MARGIN)
        header()
        identification()
        var y = IDENTIFICATION_BOTTOM + GAP
        y = provider(y)
        y = party(y, "TOMADOR / ADQUIRENTE", view.taker, "TOMADOR/ADQUIRENTE DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e")
        y = recipient(y)
        y =
            party(
                y,
                "INTERMEDIÁRIO DA OPERAÇÃO",
                view.intermediary,
                "INTERMEDIÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e",
            )
        val fixedBelow =
            municipalHeight() + federalHeight() + IBS_CBS_ROWS * ROW + TOTALS_ROWS * TOTALS_ROW + INFO_TITLE +
                stubHeight()
        y = service(y, fixedBelow)
        y = municipal(y)
        y = federal(y)
        y = ibsCbs(y)
        y = totals(y)
        additionalInformation(y)
        stub()
    }

    // --- fixed blocks ---------------------------------------------------------------------------------------

    private fun header() {
        canvas.rect(LEFT, TOP, WIDTH, HEADER_HEIGHT, fill = GREY)
        logo?.let { canvas.image(it, LOGO_X, LOGO_Y, LOGO_WIDTH, LOGO_WIDTH * it.height / it.width) }
        val center = X[1] + W2 / 2
        canvas.centeredText(center, TOP + TITLE_BASELINE, "DANFSe v2.0", TITLE)
        canvas.centeredText(center, TOP + SUBTITLE_BASELINE, "Documento Auxiliar da NFS-e", TITLE)
        if (view.restrictedProduction) {
            canvas.centeredText(center, TOP + WARNING_BASELINE, "NFS-e SEM VALIDADE JURÍDICA", WARNING)
        }
        val right = X4 + PAD
        val municipality =
            listOfNotNull(
                view.emitterMunicipality.takeIf {
                    it.isNotEmpty()
                },
                view.emitterUf,
            ).joinToString(" / ")
        val municipalityText = fit("Município: $municipality", W1 - 2 * PAD, MUNICIPALITY)
        canvas.text(right, TOP + MUNICIPALITY_BASELINE, municipalityText, MUNICIPALITY_TEXT)
        canvas.text(right, TOP + ENVIRONMENT_BASELINE, "Ambiente Gerador: ${view.generatingEnvironment}", SMALL_TEXT)
        val environmentY = TOP + ENVIRONMENT_BASELINE + SMALL_LEADING
        canvas.text(right, environmentY, "Tipo de Ambiente: ${view.environment}", SMALL_TEXT)
    }

    private fun identification() {
        val top = IDENTIFICATION_TOP
        cells(top, KEY_ROW, listOf(Cell(X[0], W3, "CHAVE DE ACESSO DA NFS-E", view.accessKey, upperLabel = true)))
        val rows =
            listOf(
                listOf(
                    "NÚMERO DA NFS-e" to view.number,
                    "COMPETÊNCIA DA NFS-e" to view.competence,
                    "DATA E HORA DA EMISSÃO DA NFS-E" to view.processedAt,
                ),
                listOf(
                    "NÚMERO DA DPS" to view.dpsNumber,
                    "SÉRIE DA DPS" to view.dpsSeries,
                    "DATA E HORA DA EMISSÃO DA DPS" to view.dpsIssuedAt,
                ),
                listOf(
                    "EMITENTE DA NFS-e" to view.emitterType,
                    "SITUAÇÃO DA NFS-E" to view.status,
                    "FINALIDADE" to view.purpose,
                ),
            )
        rows.forEachIndexed { index, row ->
            val y = top + KEY_ROW + GAP + index * (TOTALS_ROW + GAP)
            cells(
                y,
                TOTALS_ROW,
                row.mapIndexed { column, (label, value) ->
                    Cell(X[column], W1, label, value, grey = index == EMITTER_ROW && column == 0, upperLabel = true)
                },
            )
        }
        canvas.rect(X4, top, W1, IDENTIFICATION_BOTTOM - top)
        QrCode.draw(canvas, view.accessKey, QR_X, QR_Y, QR_SIZE)
        val note =
            canvas.wrap(
                "A autenticidade desta NFS-e pode ser verificada pela leitura deste código QR ou pela consulta da " +
                    "chave de acesso no portal nacional da NFS-e",
                QR_NOTE_WIDTH,
                LABEL,
            )
        note.forEachIndexed { index, line ->
            canvas.text(QR_NOTE_X, QR_NOTE_Y + index * SMALL_LEADING, line, SMALL_TEXT)
        }
    }

    // --- parties -----------------------------------------------------------------------------------------------

    private fun provider(top: Float): Float {
        val p =
            view.provider
                ?: return notice(top, "PRESTADOR / FORNECEDOR", "PRESTADOR/FORNECEDOR NÃO IDENTIFICADO NA NFS-e")
        var y = top
        y = partyRows(y, "PRESTADOR / FORNECEDOR", p, municipalRegistration = true)
        cells(
            y,
            ROW,
            listOf(
                Cell(X[0], W1, "Simples Nacional na Data de Competência", view.simplesNacional),
                Cell(X[1], W3, "Regime de Apuração Tributária pelo SN", view.simplesNacionalAssessment),
            ),
        )
        return y + ROW + GAP
    }

    private fun party(
        top: Float,
        title: String,
        party: NfseView.Party?,
        absent: String,
    ): Float = party?.let { partyRows(top, title, it, municipalRegistration = true) } ?: notice(top, title, absent)

    private fun recipient(top: Float): Float {
        val title = "DESTINATÁRIO DA OPERAÇÃO"
        val recipient = view.recipient
        return when {
            recipient != null -> partyRows(top, title, recipient, municipalRegistration = false)
            view.recipientIsTaker ->
                notice(top, title, "O DESTINATÁRIO É O PRÓPRIO TOMADOR/ADQUIRENTE DA OPERAÇÃO")
            else -> notice(top, title, "DESTINATÁRIO DA OPERAÇÃO NÃO IDENTIFICADO NA NFS-e")
        }
    }

    private fun partyRows(
        top: Float,
        title: String,
        party: NfseView.Party,
        municipalRegistration: Boolean,
    ): Float {
        val first =
            if (municipalRegistration) {
                listOf(
                    Cell(X[0], W1, null, title, grey = true),
                    Cell(X[1], W1, "CNPJ / CPF / NIF", party.document),
                    Cell(X[2], W1, "Indicador Municipal (Inscrição)", party.municipalRegistration),
                    Cell(X4, W1, "Telefone", party.phone),
                )
            } else {
                listOf(
                    Cell(X[0], W1, null, title, grey = true),
                    Cell(X[1], W2, "CNPJ / CPF / NIF", party.document),
                    Cell(X4, W1, "Telefone", party.phone),
                )
            }
        cells(top, ROW, first)
        var y = top + ROW + GAP
        cells(
            y,
            ROW,
            listOf(
                Cell(X[0], W2, "Nome / Nome Empresarial", party.name),
                Cell(X[2], W1, "Município / Sigla UF", party.municipality),
                Cell(X4, W1, "Código IBGE / CEP", party.ibgeCep),
            ),
        )
        y += ROW + GAP
        cells(y, ROW, listOf(Cell(X[0], W2, "Endereço", party.address), Cell(X[2], W2, "E-mail", party.email)))
        return y + ROW + GAP
    }

    private fun notice(
        top: Float,
        title: String,
        text: String,
    ): Float {
        cells(top, NOTICE_ROW, listOf(Cell(X[0], W1, null, title, grey = true), Cell(X[1], W3, null, text)))
        return top + NOTICE_ROW + GAP
    }

    // --- service ---------------------------------------------------------------------------------------------

    private fun service(
        top: Float,
        fixedBelow: Float,
    ): Float {
        cells(
            top,
            ROW,
            listOf(
                Cell(X[0], W1, null, "SERVIÇO PRESTADO", grey = true),
                Cell(X[1], W1, "Código de Tributação Nacional / Municipal", view.taxCodes),
                Cell(X[2], W1, "Código da NBS", view.nbs),
                Cell(X4, W1, "Local da Prestação / Sigla UF / País", view.serviceLocation),
            ),
        )
        var y = top + ROW + GAP
        canvas.rect(LEFT, y, WIDTH, TAX_DESCRIPTION_ROW)
        val taxDescription = fit(view.taxCodeDescription, WIDTH - 2 * PAD, VALUE)
        canvas.text(LEFT + PAD, y + TAX_DESCRIPTION_ROW - BOTTOM_PAD, taxDescription, VALUE_TEXT)
        y += TAX_DESCRIPTION_ROW + GAP
        val lines = canvas.wrap(view.serviceDescription.ifBlank { Formats.DASH }, WIDTH - 2 * PAD, VALUE)
        val available = CONTENT_BOTTOM - y - fixedBelow - INFO_MIN_CONTENT - GAP
        val maxLines = ((available - LABEL_BASELINE - BOTTOM_PAD) / LEADING).toInt().coerceAtLeast(1)
        val shown = truncateLines(lines, maxLines)
        val height = maxOf(ROW, LABEL_BASELINE + shown.size * LEADING + BOTTOM_PAD)
        canvas.rect(LEFT, y, WIDTH, height)
        canvas.text(LEFT + PAD, y + LABEL_BASELINE, "Descrição do Serviço", LABEL_TEXT)
        shown.forEachIndexed {
            index,
            line,
            ->
            canvas.text(LEFT + PAD, y + LABEL_BASELINE + (index + 1) * LEADING, line, VALUE_TEXT)
        }
        return y + height + GAP
    }

    // --- taxation ---------------------------------------------------------------------------------------------

    private fun municipalHeight(): Float =
        if (!view.subjectToIssqn) {
            NOTICE_ROW + GAP
        } else {
            (MUNICIPAL_ROWS - (if (benefitRowEmpty()) 1 else 0)) *
                (ROW + GAP)
        }

    private fun benefitRowEmpty(): Boolean =
        listOf(view.municipalBenefit, view.benefitCalculation, view.deductions, view.unconditionalDiscount).all {
            it ==
                null
        }

    private fun municipal(top: Float): Float {
        val title = "TRIBUTAÇÃO MUNICIPAL (ISSQN)"
        if (!view.subjectToIssqn) {
            return notice(
                top,
                title,
                "TRIBUTAÇÃO MUNICIPAL (ISSQN) - OPERAÇÃO NÃO SUJEITA AO ISSQN",
            )
        }
        var y = top
        cells(
            y,
            ROW,
            listOf(
                Cell(X[0], W1, null, title, grey = true),
                Cell(X[1], W1, "Tipo de Tributação do ISSQN", view.issqnTaxation),
                Cell(X[2], W2, "Município / Sigla UF / País de Incidência do ISSQN", view.issqnIncidence),
            ),
        )
        y += ROW + GAP
        cells(
            y,
            ROW,
            listOf(
                Cell(X[0], W1, "Regime Especial de Tributação do ISSQN", Formats.orDash(view.specialRegime)),
                Cell(X[1], W1, "Tipo de Imunidade do ISSQN", Formats.orDash(view.immunity)),
                Cell(X[2], W1, "Suspensão da Exigibilidade do ISSQN", Formats.orDash(view.suspension)),
                Cell(X4, W1, "Número Processo Suspensão", Formats.orDash(view.suspensionProcess)),
            ),
        )
        y += ROW + GAP
        if (!benefitRowEmpty()) {
            cells(
                y,
                ROW,
                listOf(
                    Cell(X[0], W1, "Benefício Municipal", Formats.orDash(view.municipalBenefit)),
                    Cell(X[1], W1, "Cálculo do BM", Formats.orDash(view.benefitCalculation)),
                    Cell(X[2], W1, "Total Deduções/Reduções", Formats.orDash(view.deductions)),
                    Cell(X4, W1, "Desconto Incondicionado", Formats.orDash(view.unconditionalDiscount)),
                ),
            )
            y += ROW + GAP
        }
        cells(
            y,
            ROW,
            listOf(
                Cell(X[0], W1, "BC ISSQN", view.calculationBase),
                Cell(X[1], W1, "Alíquota Aplicada", view.appliedRate),
                Cell(X[2], W1, "Retenção do ISSQN", view.issqnWithholding),
                Cell(X4, W1, "ISSQN Apurado", view.issqnAmount),
            ),
        )
        return y + ROW + GAP
    }

    private fun federalHeight(): Float = if (view.printFederalTaxation) FEDERAL_ROWS * (ROW + GAP) else 0f

    private fun federal(top: Float): Float {
        if (!view.printFederalTaxation) return top
        cells(
            top,
            ROW,
            listOf(
                Cell(X[0], W1, null, "TRIBUTAÇÃO FEDERAL (EXCETO CBS)", grey = true),
                Cell(X[1], W1, "IRRF", view.irrf),
                Cell(X[2], W1, "Contribuição Previdenciária - Retida", view.socialSecurity),
                Cell(X4, W1, "Contribuições Sociais - Retidas", view.socialContributions),
            ),
        )
        val y = top + ROW + GAP
        cells(
            y,
            ROW,
            listOf(
                Cell(X[0], W1, "PIS - Débito Apuração Própria", view.pis),
                Cell(X[1], W1, "COFINS - Débito Apuração Própria", view.cofins),
                Cell(X[2], W2, "Descrição Contrib. Sociais - Retidas", view.pisCofinsWithholding),
            ),
        )
        return y + ROW + GAP
    }

    private fun ibsCbs(top: Float): Float {
        var y = top
        cells(
            y,
            ROW,
            listOf(
                Cell(X[0], W1, null, "TRIBUTAÇÃO IBS / CBS", grey = true),
                Cell(X[1], W1, "CST / cClassTrib", view.cstClassification),
                Cell(
                    X[2],
                    W2,
                    "Indicador de Operação / Código IBGE Incidência / Município Incidência / Sigla UF",
                    view.ibsCbsIncidence,
                ),
            ),
        )
        y += ROW + GAP
        cells(
            y,
            ROW,
            listOf(
                Cell(X[0], W1, "Exclusões e Reduções da Base de Cálculo", view.baseExclusions),
                Cell(X[1], W1, "Base de Cálculo Após Exclusões e Reduções", view.ibsCbsBase),
                Cell(X[2], W1, "Red. Alíquota IBS / Red. Alíquota CBS", view.rateReductions),
                Cell(X4, W1, "Alíquota - IBS UF / IBS Mun", view.ibsRates),
            ),
        )
        y += ROW + GAP
        cells(
            y,
            ROW,
            listOf(
                Cell(X[0], W1, "Alíq. Efetiva Municipal - IBS", view.ibsMunicipalEffectiveRate),
                Cell(X[1], W1, "Valor Apurado Municipal - IBS", view.ibsMunicipalAmount),
                Cell(X[2], W1, "Alíq. Efetiva Estadual - IBS", view.ibsStateEffectiveRate),
                Cell(X4, W1, "Valor Apurado Estadual - IBS", view.ibsStateAmount),
            ),
        )
        y += ROW + GAP
        cells(
            y,
            ROW,
            listOf(
                Cell(X[0], W1, "Valor Total Apurado - IBS", view.ibsTotal),
                Cell(X[1], W1, "Alíquota - CBS", view.cbsRate),
                Cell(X[2], W1, "Alíquota Efetiva - CBS", view.cbsEffectiveRate),
                Cell(X4, W1, "Valor Total Apurado - CBS", view.cbsTotal),
            ),
        )
        return y + ROW + GAP
    }

    private fun totals(top: Float): Float {
        cells(
            top,
            TOTALS_ROW,
            listOf(
                Cell(X[0], W1, null, "VALOR TOTAL DA NFS-E", grey = true),
                Cell(X[1], W1, "Valor da Operação / Serviço", view.serviceAmount),
                Cell(X[2], W1, "Desconto Incondicionado", Formats.orDash(view.unconditionalDiscount)),
                Cell(X4, W1, "Desconto Condicionado", view.conditionalDiscount),
            ),
        )
        val y = top + TOTALS_ROW + GAP
        cells(
            y,
            TOTALS_ROW,
            listOf(
                Cell(X[0], W1, "Total das Retenções (ISSQN / Federais)", view.totalWithholdings),
                Cell(X[1], W1, "Valor Líquido da NFS-e", view.netAmount),
                Cell(X[2], W1, "Total do IBS/CBS", view.ibsCbsTotal),
                Cell(X4, W1, "Valor Líquido da NFS-e + IBS/CBS", view.netAmountWithIbsCbs, grey = true),
            ),
        )
        return y + TOTALS_ROW + GAP
    }

    // --- bottom ------------------------------------------------------------------------------------------------

    private fun additionalInformation(top: Float) {
        canvas.rect(LEFT, top, WIDTH, INFO_TITLE, fill = GREY)
        canvas.text(LEFT + PAD, top + INFO_TITLE - BOTTOM_PAD, "INFORMAÇÕES COMPLEMENTARES", BLOCK_TITLE_TEXT)
        val y = top + INFO_TITLE + GAP
        val height = CONTENT_BOTTOM - stubHeight() - y
        canvas.rect(LEFT, y, WIDTH, height)
        val maxLines = ((height - PAD) / LEADING).toInt().coerceAtLeast(1)
        val lines = additionalInformationLines(maxLines)
        lines.forEachIndexed {
            index,
            line,
            ->
            canvas.text(LEFT + PAD, y + LABEL_BASELINE + index * LEADING, line, VALUE_TEXT)
        }
    }

    private fun stubHeight(): Float = if (options.stub) STUB_ROW + GAP else 0f

    private fun stub() {
        if (!options.stub) return
        val y = CONTENT_BOTTOM - STUB_ROW
        cells(
            y,
            STUB_ROW,
            listOf(
                Cell(X[0], W1, "DATA CIENTIFICAÇÃO:", "", upperLabel = true),
                Cell(X[1], W1, "IDENTIFICAÇÃO E ASSINATURA", "", upperLabel = true),
                Cell(X[2], W2, "Nº NFS-e / CHAVE NFS-e", "${view.number} / ${view.accessKey}", upperLabel = true),
            ),
        )
    }

    // --- primitives -------------------------------------------------------------------------------------------

    private fun cells(
        y: Float,
        height: Float,
        cells: List<Cell>,
    ) {
        cells.forEach { cell ->
            canvas.rect(cell.x, y, cell.width, height, fill = if (cell.grey) GREY else null)
            val label = cell.label
            if (label == null) {
                // A block title: 7 pt bold, vertically centred
                val title = fit(cell.value, cell.width - 2 * PAD, BLOCK_TITLE, bold = true)
                canvas.text(cell.x + PAD, y + height / 2 + BLOCK_TITLE_OFFSET, title, BLOCK_TITLE_TEXT)
            } else {
                val labelStyle = if (cell.upperLabel) BLOCK_TITLE_TEXT else LABEL_TEXT
                val innerWidth = cell.width - 2 * PAD
                val fittedLabel = fit(label, innerWidth, labelStyle.size, bold = true)
                canvas.text(cell.x + PAD, y + LABEL_BASELINE, fittedLabel, labelStyle)
                canvas.text(cell.x + PAD, y + height - BOTTOM_PAD, fit(cell.value, innerWidth, VALUE), VALUE_TEXT)
            }
        }
    }

    /** Cuts [text] with an ellipsis until it fits in [width]. */
    private fun fit(
        text: String,
        width: Float,
        size: Float,
        bold: Boolean = false,
    ): String {
        var candidate = text
        while (candidate.isNotEmpty() && canvas.width(candidate, size, bold) > width) {
            candidate = Formats.truncate(candidate, candidate.length - 1)
        }
        return candidate
    }

    /**
     * The whole text when it fits; otherwise the notes truncated with an ellipsis, so that the mandatory approximate
     * taxes line (Lei nº 12.741/2012) is always printed.
     */
    private fun additionalInformationLines(maxLines: Int): List<String> {
        val width = WIDTH - 2 * PAD
        val all = canvas.wrap(view.additionalInformation, width, VALUE)
        if (all.size <= maxLines) return all
        val taxes = canvas.wrap(view.approximateTaxes, width, VALUE)
        val notes =
            truncateLines(canvas.wrap(view.additionalNotes, width, VALUE), (maxLines - taxes.size).coerceAtLeast(0))
        return (notes + taxes).take(maxLines)
    }

    /** The first [max] lines, the last one ending with an ellipsis when something was left out. */
    private fun truncateLines(
        lines: List<String>,
        max: Int,
    ): List<String> =
        when {
            lines.size <= max -> lines
            max == 0 -> emptyList()
            else -> lines.take(max - 1) + fit(lines[max - 1] + " …", WIDTH - 2 * PAD, VALUE)
        }

    private companion object {
        const val PAGE_WIDTH = 21.0f
        const val PAGE_HEIGHT = 29.7f
        const val PAGE_MARGIN = 0.20f
        const val LEFT = 0.30f
        const val TOP = 0.30f
        const val WIDTH = 20.40f
        const val CONTENT_BOTTOM = PAGE_HEIGHT - LEFT
        val X = floatArrayOf(0.30f, 5.41f, 10.51f, 15.62f)

        /** The last of the four columns (the only one addressed by index above the `ignoreNumbers` threshold). */
        val X4 = X.last()
        const val W1 = 5.09f
        const val W2 = 10.19f
        const val W3 = 15.30f
        const val GAP = 0.02f
        const val PAD = 0.08f
        const val BOTTOM_PAD = 0.13f
        const val ROW = 0.63f
        const val TOTALS_ROW = 0.67f
        const val STUB_ROW = 0.67f
        const val NOTICE_ROW = 0.32f
        const val KEY_ROW = 0.77f
        const val TAX_DESCRIPTION_ROW = 0.38f
        const val INFO_TITLE = 0.39f
        const val INFO_MIN_CONTENT = 0.70f
        const val HEADER_HEIGHT = 1.16f
        const val IDENTIFICATION_TOP = 1.48f
        const val IDENTIFICATION_BOTTOM = 4.32f
        const val LOGO_X = 0.49f
        const val LOGO_Y = 0.44f
        const val LOGO_WIDTH = 4.00f
        const val QR_X = 17.48f
        const val QR_Y = 1.67f
        const val QR_SIZE = 1.52f
        const val QR_NOTE_X = 15.80f
        const val QR_NOTE_Y = 3.52f
        const val QR_NOTE_WIDTH = 4.72f
        const val EMITTER_ROW = 2
        const val TITLE_SIZE = 9f
        const val MUNICIPALITY = 8f
        const val BLOCK_TITLE = 7f
        const val VALUE = 7f
        const val LABEL = 6f
        val TITLE = PdfCanvas.TextStyle(TITLE_SIZE, bold = true)
        val WARNING = PdfCanvas.TextStyle(TITLE_SIZE, bold = true, color = Color(0xE0, 0x00, 0x00))
        val BLOCK_TITLE_TEXT = PdfCanvas.TextStyle(BLOCK_TITLE, bold = true)
        val LABEL_TEXT = PdfCanvas.TextStyle(LABEL, bold = true)
        val VALUE_TEXT = PdfCanvas.TextStyle(VALUE)
        val MUNICIPALITY_TEXT = PdfCanvas.TextStyle(MUNICIPALITY)
        val SMALL_TEXT = PdfCanvas.TextStyle(LABEL)
        const val LEADING = 0.30f
        const val SMALL_LEADING = 0.24f
        const val LABEL_BASELINE = 0.22f
        const val BLOCK_TITLE_OFFSET = 0.11f
        const val TITLE_BASELINE = 0.45f
        const val SUBTITLE_BASELINE = 0.78f
        const val WARNING_BASELINE = 1.08f
        const val MUNICIPALITY_BASELINE = 0.42f
        const val ENVIRONMENT_BASELINE = 0.72f
        const val MUNICIPAL_ROWS = 4
        const val FEDERAL_ROWS = 2
        const val IBS_CBS_ROWS = 4
        const val TOTALS_ROWS = 2
        const val GREY = 0.95f
    }
}
