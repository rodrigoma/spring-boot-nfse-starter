package io.github.rodrigoma.nfse.model.dps

/**
 * Check digits of CPF and CNPJ (Receita Federal modulo-11), including the alphanumeric CNPJ of IN RFB 2.229/2024
 * (letters weigh their ASCII code minus 48; the two check digits are always numeric).
 */
object TaxIdCheckDigits {
    private const val CPF_LENGTH = 11
    private const val CNPJ_LENGTH = 14
    private const val ASCII_ZERO = 48
    private const val MODULO = 11
    private const val CPF_FIRST_WEIGHT = 10
    private const val CPF_SECOND_WEIGHT = 11
    private const val MIN_WEIGHT = 2
    private const val MAX_WEIGHT = 9
    private const val CPF_MULTIPLIER = 10

    fun isValidCpf(cpf: String): Boolean {
        if (cpf.length != CPF_LENGTH || !cpf.all(Char::isDigit) || cpf.all { it == cpf[0] }) return false
        val digits = cpf.map { it - '0' }
        val first = cpfDigit(digits.take(CPF_LENGTH - 2), CPF_FIRST_WEIGHT)
        val second = cpfDigit(digits.take(CPF_LENGTH - 1), CPF_SECOND_WEIGHT)
        return digits[CPF_LENGTH - 2] == first && digits[CPF_LENGTH - 1] == second
    }

    fun isValidCnpj(cnpj: String): Boolean {
        val wellFormed =
            cnpj.length == CNPJ_LENGTH &&
                !cnpj.all { it == cnpj[0] } &&
                cnpj.take(CNPJ_LENGTH - 2).all { it.isDigit() || it in 'A'..'Z' } &&
                cnpj.takeLast(2).all(Char::isDigit)
        if (!wellFormed) return false
        val values = cnpj.map { it.code - ASCII_ZERO }
        val first = cnpjDigit(values.take(CNPJ_LENGTH - 2))
        val second = cnpjDigit(values.take(CNPJ_LENGTH - 1))
        return values[CNPJ_LENGTH - 2] == first && values[CNPJ_LENGTH - 1] == second
    }

    private fun cpfDigit(
        digits: List<Int>,
        startWeight: Int,
    ): Int {
        val sum = digits.mapIndexed { index, digit -> digit * (startWeight - index) }.sum()
        return (sum * CPF_MULTIPLIER % MODULO).let { if (it == CPF_MULTIPLIER) 0 else it }
    }

    /** CNPJ weights run 2..9 from the rightmost position leftwards, restarting at 2 after 9. */
    private fun cnpjDigit(values: List<Int>): Int {
        val span = MAX_WEIGHT - MIN_WEIGHT + 1
        val sum = values.mapIndexed { index, value -> value * (MIN_WEIGHT + (values.size - 1 - index) % span) }.sum()
        val remainder = sum % MODULO
        return if (remainder < 2) 0 else MODULO - remainder
    }
}
