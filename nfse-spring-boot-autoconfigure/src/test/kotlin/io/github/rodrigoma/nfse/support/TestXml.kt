package io.github.rodrigoma.nfse.support

/** NFS-e and event XMLs shaped like the ones the Sefin returns (unsigned — the parser does not verify). */
object TestXml {
    const val ACCESS_KEY = "35503082212345678000195000000000012320260900000001"
    private const val NS = "http://www.sped.fazenda.gov.br/nfse"

    fun nfse(accessKey: String = ACCESS_KEY): String =
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
