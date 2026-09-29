package io.github.rodrigoma.nfse.client

/**
 * The service code the ADN Parâmetros Municipais expects: the six digits of `cTribNac` followed by the three of
 * the municipal complement (`cTribMun`), written with separators — `01.09.02.001`.
 *
 * The service answers anything else with HTTP 400 and *"Chamada mal formada. O código do serviço deve ser
 * composto por nove dígitos"*. The message is literal: it counts **digits**, and the separators are part of the
 * expected form, so `010902001` and `010902.001` are both refused. Normalising here turns that into a clear
 * failure before the request instead of a puzzling rejection after it.
 */
object ServiceCodes {
    private const val ITEM = 2
    private const val SUBITEM = 2
    private const val NATIONAL_DETAIL = 2
    private const val MUNICIPAL_COMPLEMENT = 3

    /** `01` item + `09` subitem + `02` national detail + `001` municipal complement. */
    private val PARTS = intArrayOf(ITEM, SUBITEM, NATIONAL_DETAIL, MUNICIPAL_COMPLEMENT)
    private val DIGITS = PARTS.sum()

    /**
     * `01.09.02.001` from either spelling — `010902001` or the dotted form itself.
     *
     * @throws IllegalArgumentException when [code] does not hold exactly nine digits.
     */
    fun complete(code: String): String {
        val digits = code.filter(Char::isDigit)
        require(digits.length == DIGITS) {
            "The municipal parameters service takes the complete service code — six digits of cTribNac plus the " +
                "three of the municipal complement, as in '01.09.02.001'. Got '$code' with ${digits.length} " +
                "digits; the cTribNac alone (six digits) is not enough."
        }
        require(code.all { it.isDigit() || it == '.' }) {
            "The service code may only hold digits and dots, as in '01.09.02.001'. Got '$code'."
        }
        var start = 0
        return PARTS.joinToString(".") { size -> digits.substring(start, start + size).also { start += size } }
    }
}
