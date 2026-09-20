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

    fun danfseBaseUrl(): String =
        when (this) {
            PRODUCTION -> "https://adn.nfse.gov.br/danfse"
            RESTRICTED_PRODUCTION -> "https://adn.producaorestrita.nfse.gov.br/danfse"
        }
}
