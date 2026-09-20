package io.github.rodrigoma.nfse.danfse.layout

/** Portuguese descriptions of the code tables printed on the DANFSe (Anexo I of the manual, XSD 1.01). */
internal object Descriptions {
    val simplesNacional =
        mapOf(
            "1" to "Não Optante",
            "2" to "Optante - Microempreendedor Individual (MEI)",
            "3" to "Optante - Microempresa ou Empresa de Pequeno Porte (ME/EPP)",
        )
    val simplesNacionalAssessment =
        mapOf(
            "1" to "Regime de apuração dos tributos federais e municipal pelo SN",
            "2" to "Regime de apuração dos tributos federais pelo SN e ISSQN por fora do SN conforme respectiva " +
                "legislação municipal do tributo",
            "3" to "Regime de apuração dos tributos federais e municipal por fora do SN conforme respectivas " +
                "legislações federal e municipal de cada tributo",
        )
    val specialRegime =
        mapOf(
            "0" to "Nenhum",
            "1" to "Ato Cooperado (Cooperativa)",
            "2" to "Estimativa",
            "3" to "Microempresa Municipal",
            "4" to "Notário ou Registrador",
            "5" to "Profissional Autônomo",
            "6" to "Sociedade de Profissionais",
            "9" to "Outros",
        )
    val issqnTaxation =
        mapOf(
            "1" to "Operação tributável",
            "2" to "Imunidade",
            "3" to "Exportação de serviço",
            "4" to "Não Incidência",
        )
    val immunity =
        mapOf(
            "0" to "Imunidade (tipo não informado na nota de origem)",
            "1" to "Patrimônio, renda ou serviços, uns dos outros (CF88, Art 150, VI, a)",
            "2" to "Templos de qualquer culto (CF88, Art 150, VI, b)",
            "3" to "Patrimônio, renda ou serviços dos partidos políticos, inclusive suas fundações, das " +
                "entidades sindicais dos trabalhadores, das instituições de educação e de assistência social, " +
                "sem fins lucrativos (CF88, Art 150, VI, c)",
            "4" to "Livros, jornais, periódicos e o papel destinado a sua impressão (CF88, Art 150, VI, d)",
            "5" to "Fonogramas e videofonogramas musicais produzidos no Brasil (CF88, Art 150, VI, e)",
        )
    val suspension =
        mapOf(
            "1" to "Exigibilidade Suspensa por Decisão Judicial",
            "2" to "Exigibilidade Suspensa por Processo Administrativo",
        )
    val municipalBenefit =
        mapOf(
            "1" to "Isenção",
            "2" to "Redução da BC em %",
            "3" to "Redução da BC em R$",
            "4" to "Alíquota Diferenciada",
        )
    val issqnWithholding =
        mapOf("1" to "Não Retido", "2" to "Retido pelo Tomador", "3" to "Retido pelo Intermediário")
    val pisCofinsWithholding =
        mapOf(
            "0" to "PIS/COFINS/CSLL Não Retidos",
            "1" to "PIS/COFINS Retidos",
            "2" to "PIS/COFINS Não Retidos",
            "3" to "PIS/COFINS/CSLL Retidos",
            "4" to "PIS/COFINS Retidos, CSLL Não Retido",
            "5" to "PIS Retido, COFINS/CSLL Não Retido",
            "6" to "COFINS Retido, PIS/CSLL Não Retido",
            "7" to "PIS Não Retido, COFINS/CSLL Retidos",
            "8" to "PIS/COFINS Não Retidos, CSLL Retido",
            "9" to "COFINS Não Retido, PIS/CSLL Retidos",
        )
    val status =
        mapOf(
            "100" to "NFS-e Gerada",
            "102" to "NFS-e de Decisão Judicial ou Administrativa",
            "103" to "NFS-e Avulsa",
            "107" to "NFS-e MEI",
        )
    val emitter = mapOf("1" to "Prestador", "2" to "Tomador", "3" to "Intermediário")
    val purpose = mapOf("0" to "NFS-e regular")
    val generatingEnvironment = mapOf("1" to "Prefeitura", "2" to "Sistema Nacional da NFS-e")
    val environment = mapOf("1" to "Produção", "2" to "Produção Restrita (Homologação)")

    fun of(
        table: Map<String, String>,
        code: String?,
    ): String? = code?.let { table[it] ?: it }
}
