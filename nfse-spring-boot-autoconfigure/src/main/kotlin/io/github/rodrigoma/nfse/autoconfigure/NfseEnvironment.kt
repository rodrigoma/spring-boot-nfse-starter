package io.github.rodrigoma.nfse.autoconfigure

/**
 * Sistema Nacional NFS-e environments. [code] is the `tpAmb` value written into every DPS and event request.
 */
enum class NfseEnvironment(
    val code: String,
) {
    /** `tpAmb = 1`. Only reached when spelled out in `nfse.environment`. */
    PRODUCTION("1"),

    /** `tpAmb = 2` — "produção restrita", the official test environment. Default. */
    RESTRICTED_PRODUCTION("2"),
    ;

    fun sefinBaseUrl(): String =
        when (this) {
            PRODUCTION -> "https://sefin.nfse.gov.br/SefinNacional"
            RESTRICTED_PRODUCTION -> "https://sefin.producaorestrita.nfse.gov.br/SefinNacional"
        }

    /** ADN Contribuintes — distribution by NSU and events by access key. */
    fun adnBaseUrl(): String =
        when (this) {
            PRODUCTION -> "https://adn.nfse.gov.br/contribuintes"
            RESTRICTED_PRODUCTION -> "https://adn.producaorestrita.nfse.gov.br/contribuintes"
        }

    /** ADN DANFSe — the PDF (official generation suspended by NT 008/2026, see README). */
    fun danfseBaseUrl(): String =
        when (this) {
            PRODUCTION -> "https://adn.nfse.gov.br/danfse"
            RESTRICTED_PRODUCTION -> "https://adn.producaorestrita.nfse.gov.br/danfse"
        }

    /** ADN Parâmetros Municipais — agreement, rates, benefits, special regimes and withholdings. */
    fun municipalParametersBaseUrl(): String =
        when (this) {
            PRODUCTION -> "https://adn.nfse.gov.br/parametrizacao"
            RESTRICTED_PRODUCTION -> "https://adn.producaorestrita.nfse.gov.br/parametrizacao"
        }
}
