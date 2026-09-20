package io.github.rodrigoma.nfse.danfse.layout

/** IBGE municipality and ISO country names (Anexo A of the manual), loaded once from the bundled tables. */
internal object Places {
    data class Municipality(
        val name: String,
        val uf: String,
    )

    private val municipalities: Map<String, Municipality> by lazy {
        rows("municipios-ibge.csv").associate { (code, name, uf) -> code to Municipality(name, uf) }
    }
    private val countries: Map<String, String> by lazy {
        rows("paises-iso2.csv").associate { (iso, name) ->
            iso to name
        }
    }

    fun municipality(ibge: String?): Municipality? = ibge?.let { municipalities[it.padStart(IBGE_LENGTH, '0')] }

    fun country(iso: String?): String? = iso?.let { countries[it.uppercase()] ?: it }

    /** `Município / UF` for an IBGE code, or the code itself when unknown. */
    fun municipalityLabel(ibge: String?): String? =
        ibge?.let { code -> municipality(code)?.let { "${it.name} / ${it.uf}" } ?: code }

    private fun rows(resource: String): List<List<String>> =
        requireNotNull(Places::class.java.getResourceAsStream("/io/github/rodrigoma/nfse/danfse/$resource")) {
            "$resource missing"
        }.bufferedReader(Charsets.UTF_8)
            .useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split(';') }.toList()
            }

    private const val IBGE_LENGTH = 7
}
