package io.github.rodrigoma.nfse.support

/** NFS-e and event XMLs shaped like the ones the Sefin returns (unsigned — the parser does not verify). */
object TestXml {
    const val ACCESS_KEY = "35503082212345678000195000000000012320260900000001"
    private const val NS = "http://www.sped.fazenda.gov.br/nfse"

    /**
     * `IBSCBS` as the Sefin computes it, with every optional group filled. The values are deliberately all
     * different — and `pIBSUF`/`vIBSUF` appear under both `uf`/`gIBSUFTot` and `gTribCompraGov` with distinct
     * numbers — so a parser that looked up descendants instead of direct children would read the wrong one.
     */
    const val IBS_CBS: String =
        "<IBSCBS><cLocalidadeIncid>3550308</cLocalidadeIncid><xLocalidadeIncid>São Paulo</xLocalidadeIncid>" +
            "<pRedutor>30.00</pRedutor>" +
            "<valores><vBC>1000.00</vBC><vCalcReeRepRes>50.00</vCalcReeRepRes>" +
            "<uf><pIBSUF>0.10</pIBSUF><pRedAliqUF>0.001</pRedAliqUF><pAliqEfetUF>0.09</pAliqEfetUF></uf>" +
            "<mun><pIBSMun>0.20</pIBSMun><pRedAliqMun>0.002</pRedAliqMun><pAliqEfetMun>0.18</pAliqEfetMun></mun>" +
            "<fed><pCBS>0.90</pCBS><pRedAliqCBS>0.003</pRedAliqCBS><pAliqEfetCBS>0.87</pAliqEfetCBS></fed>" +
            "</valores>" +
            "<totCIBS><vTotNF>1234.56</vTotNF>" +
            "<gIBS><vIBSTot>2.70</vIBSTot>" +
            "<gIBSCredPres><pCredPresIBS>1.50</pCredPresIBS><vCredPresIBS>0.04</vCredPresIBS></gIBSCredPres>" +
            "<gIBSUFTot><vDifUF>0.01</vDifUF><vIBSUF>0.90</vIBSUF></gIBSUFTot>" +
            "<gIBSMunTot><vDifMun>0.02</vDifMun><vIBSMun>1.80</vIBSMun></gIBSMunTot></gIBS>" +
            "<gCBS><gCBSCredPres><pCredPresCBS>2.50</pCredPresCBS><vCredPresCBS>0.22</vCredPresCBS></gCBSCredPres>" +
            "<vDifCBS>0.03</vDifCBS><vCBS>8.70</vCBS></gCBS>" +
            "<gTribRegular><pAliqEfeRegIBSUF>0.11</pAliqEfeRegIBSUF><vTribRegIBSUF>1.10</vTribRegIBSUF>" +
            "<pAliqEfeRegIBSMun>0.22</pAliqEfeRegIBSMun><vTribRegIBSMun>2.20</vTribRegIBSMun>" +
            "<pAliqEfeRegCBS>0.99</pAliqEfeRegCBS><vTribRegCBS>9.90</vTribRegCBS></gTribRegular>" +
            "<gTribCompraGov><pIBSUF>0.12</pIBSUF><vIBSUF>1.20</vIBSUF>" +
            "<pIBSMun>0.23</pIBSMun><vIBSMun>2.30</vIBSMun>" +
            "<pCBS>0.98</pCBS><vCBS>9.80</vCBS></gTribCompraGov></totCIBS></IBSCBS>"

    fun nfse(
        accessKey: String = ACCESS_KEY,
        ibsCbs: String = "",
    ): String =
        """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<NFSe xmlns="$NS" versao="1.01"><infNFSe Id="NFS$accessKey">""" +
            "<xLocEmi>São Paulo</xLocEmi><xLocPrestacao>São Paulo</xLocPrestacao><nNFSe>123</nNFSe>" +
            "<cLocIncid>3550308</cLocIncid><xLocIncid>São Paulo</xLocIncid><xTribNac>Consultoria</xTribNac>" +
            "<verAplic>SefinNacional</verAplic><ambGer>2</ambGer><tpEmis>1</tpEmis><cStat>100</cStat>" +
            "<dhProc>2026-09-19T13:00:00Z</dhProc><nDFSe>1</nDFSe>" +
            "<emit><CNPJ>12345678000195</CNPJ><xNome>EMPRESA TESTE</xNome>" +
            "<enderNac><xLgr>Av. Paulista</xLgr><nro>1000</nro><xBairro>Bela Vista</xBairro><cMun>3550308</cMun>" +
            "<UF>SP</UF><CEP>01310100</CEP></enderNac></emit>" +
            "<valores><vBC>100.00</vBC><pAliqAplic>2.00</pAliqAplic><vISSQN>2.00</vISSQN><vLiq>98.00</vLiq></valores>" +
            ibsCbs +
            """<DPS versao="1.01"><infDPS Id="DPS355030821234567800019500001000000000000001"><tpAmb>2</tpAmb>""" +
            "<valores><vServPrest><vServ>100.00</vServ></vServPrest></valores></infDPS></DPS>" +
            "</infNFSe></NFSe>"

    fun event(accessKey: String = ACCESS_KEY): String =
        """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<evento xmlns="$NS" versao="1.01"><infEvento Id="EVT${accessKey}101101001">""" +
            "<verAplic>SefinNacional</verAplic><ambGer>2</ambGer><nSeqEvento>1</nSeqEvento>" +
            "<dhProc>2026-09-20T10:00:00-03:00</dhProc><nDFSe>1</nDFSe>" +
            """<pedRegEvento versao="1.01"><infPedReg Id="PRE${accessKey}101101"><tpAmb>2</tpAmb>""" +
            "<verAplic>test/1.0</verAplic><dhEvento>2026-09-20T09:59:00-03:00</dhEvento>" +
            "<CNPJAutor>12345678000195</CNPJAutor>" +
            "<chNFSe>$accessKey</chNFSe><e101101><xDesc>Cancelamento de NFS-e</xDesc><cMotivo>1</cMotivo>" +
            "<xMotivo>Nota emitida com valor incorreto</xMotivo></e101101></infPedReg></pedRegEvento>" +
            "</infEvento></evento>"
}
