package io.github.rodrigoma.nfse.danfse.layout

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Value formats of the DANFSe: `R$ 1.234,56`, `2,00 %`, `DD/MM/AAAA hh:mm:ss`, document masks, truncation. */
internal object Formats {
    const val DASH = "-"
    private val symbols = DecimalFormatSymbols(Locale.of("pt", "BR"))
    private val money = DecimalFormat("#,##0.00", symbols)
    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    fun money(value: String?): String = value?.toBigDecimalOrNull()?.let { "R$ " + money.format(it) } ?: DASH

    fun money(value: BigDecimal?): String = value?.let { "R$ " + money.format(it) } ?: DASH

    fun percent(value: String?): String = value?.toBigDecimalOrNull()?.let { money.format(it) + " %" } ?: DASH

    fun date(value: String?): String =
        value?.let { runCatching { LocalDate.parse(it).format(date) }.getOrNull() } ?: DASH

    fun dateTime(value: String?): String =
        value?.let { runCatching { OffsetDateTime.parse(it).format(dateTime) }.getOrNull() } ?: DASH

    fun cnpj(value: String): String = mask(value, "##.###.###/####-##")

    fun cpf(value: String): String = mask(value, "###.###.###-##")

    fun cep(value: String): String = mask(value, "#####-###")

    /** `nn.nn.nn` for the national code. */
    fun nationalTaxCode(value: String): String = mask(value, "##.##.##")

    /** `n.nnnn.nn.nn` for the NBS. */
    fun nbs(value: String): String = mask(value, "#.####.##.##")

    /** Applies [pattern] (`#` = one character) when [value] has exactly as many characters as the pattern expects. */
    private fun mask(
        value: String,
        pattern: String,
    ): String {
        if (value.length != pattern.count { it == '#' }) return value
        val characters = value.iterator()
        return pattern.map { if (it == '#') characters.next() else it }.joinToString("")
    }

    fun orDash(value: String?): String = value?.takeIf { it.isNotBlank() } ?: DASH

    /** Cuts to [max] characters with an ellipsis, as the NT allows for every field. */
    fun truncate(
        value: String,
        max: Int,
    ): String = if (value.length <= max) value else value.take(max - 1).trimEnd() + "…"
}
