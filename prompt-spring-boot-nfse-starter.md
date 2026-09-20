# Prompt — `rodrigoma/spring-boot-nfse-starter`

Você vai construir, do zero, a biblioteca **`spring-boot-nfse-starter`** no repositório `rodrigoma/spring-boot-nfse-starter` (já existe, só com o README). É um Spring Boot starter **público e genérico** que integra uma aplicação ao **Sistema Nacional NFS-e** (padrão nacional da Nota Fiscal de Serviço eletrônica, gov.br/nfse): emitir NFS-e a partir de uma DPS, consultar, cancelar e obter o DANFSE (PDF). A biblioteca conhece os **campos** do padrão, nunca valores de uma empresa: tudo que é do emitente (certificado, CNPJ, inscrição municipal, município, regime tributário, série, código de serviço, alíquota) vem de configuração ou do chamador.

Ela é irmã de `rodrigoma/spring-boot-pagbank-starter`. **Antes de escrever qualquer coisa, leia esse repositório inteiro** — `buildSrc/`, `settings.gradle.kts`, os três módulos, `.github/workflows/`, `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `detekt.yml` — e reproduza as mesmas convenções: mesma estrutura de módulos, mesmos plugins de convenção, mesmas versões, mesmo estilo de código, de teste, de documentação e de publicação. Onde este prompt e aquele repositório divergirem em convenção, o repositório vence; onde divergirem em requisito, este prompt vence.

## 1. Estrutura e stack (espelhar a lib do PagBank)

- Módulos: `nfse-spring-boot-autoconfigure` (o código), `nfse-spring-boot-starter` (só a dependência transitiva), `nfse-spring-boot-sample` (app de exemplo que emite uma NFS-e em produção restrita a partir de `application.yml`).
- `buildSrc` com os plugins de convenção (`nfse.kotlin-library`, `nfse.publish`, `nfse.security`), Kotlin e Spring Boot nas mesmas versões da lib do PagBank, JDK 21 mínimo, Spotless/ktlint, Detekt, OWASP dependency-check, JaCoCo com o mesmo piso de cobertura.
- Grupo Maven `io.github.rodrigoma`, versão inicial `1.0.0-RC1` em `gradle.properties`, publicação no Maven Central pelos mesmos workflows (`ci.yml`, `release.yml`, `security.yml`).
- Auto-configuração registrada em `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, `@ConditionalOnProperty` para poder desligar, `spring-boot-configuration-processor` para as propriedades aparecerem no IDE, health indicator opcional como no PagBank.
- Código, identificadores, contrato e KDoc em **inglês**; README, CHANGELOG e CONTRIBUTING em inglês como na lib do PagBank. Sem dependência de XML pesada (JAXB só se for realmente necessário; preferir `javax.xml` da JDK + `org.w3c.dom` para montar e assinar).

## 2. O padrão nacional — o que a lib precisa saber

Documentação oficial: <https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica> → "Documentação Atual (Produção)". Baixe e leia:

- **API - Manual de Contribuintes - Emissor Público** (`manual-contribuintes-emissor-publico-api-sistema-nacional-nfs-e-v1-2-out2025.pdf`)
- **API - Manual de Contribuintes - Guia de Utilização das APIs do ADN**
- **NFSe-ESQUEMAS_XSD-v1.01** (zip com `DPS_v1.01.xsd`, `NFSe_v1.01.xsd`, `pedRegEvento_v1.01.xsd`, `evento_v1.01.xsd`, `tiposComplexos/Simples/Eventos_v1.01.xsd`, `xmldsig-core-schema.xsd`) — **embarque os XSD no jar** e valide a DPS contra eles antes de enviar.
- **ANEXO I** (leiaute e regras de negócio da DPS/NFS-e) e **ANEXO II** (eventos), planilhas xlsx.
- Links das APIs (Swagger, só abre com certificado): <https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/apis-prod-restrita-e-producao>.

Fatos que já confirmei nos manuais e nos XSD; verifique e complete com a documentação:

**Ambientes e bases**

| | Produção restrita (`tpAmb=2`) | Produção (`tpAmb=1`) |
|---|---|---|
| SEFIN Nacional (emissão, consulta, eventos, DPS, parâmetros municipais) | `https://sefin.producaorestrita.nfse.gov.br/SefinNacional` | `https://sefin.nfse.gov.br/SefinNacional` |
| ADN Contribuintes (distribuição por NSU, eventos por chave) | `https://adn.producaorestrita.nfse.gov.br/contribuintes` | `https://adn.nfse.gov.br/contribuintes` |
| DANFSE (PDF) | `https://adn.producaorestrita.nfse.gov.br/danfse` | `https://adn.nfse.gov.br/danfse` |

Confirme os caminhos exatos (com ou sem `/API`, versão) no Swagger de produção restrita usando um certificado; se o prefixo divergir, torne-o configurável com esses defaults.

**Autenticação**: mTLS com certificado digital ICP-Brasil **A1** (e-CNPJ, arquivo PKCS#12 `.pfx`) do emitente. Não há token. O **mesmo** certificado assina o XML. Regras do certificado (Anexo I): X.509 v3, KeyUsage com "Digital Signature" e "Non Repudiation" para assinar e "Client Authentication" para a conexão, não pode ser certificado de AC.

**Endpoints da SEFIN Nacional** (manual do Emissor Público, seção 1):

- `POST /nfse` — recebe a DPS e gera a NFS-e **de forma síncrona**. Corpo JSON com o XML da DPS **assinado, comprimido com GZip e codificado em Base64** (campo `dpsXmlGZipB64`; confirme o nome no Swagger). Resposta: ambiente, versão do aplicativo, data/hora de processamento, `idDps`, **`chaveAcesso`** (50 dígitos) e o XML da NFS-e (`nfseXmlGZipB64`); rejeição: lista de erros com código e descrição. Uma DPS com o grupo `subst` (chave da NFS-e a substituir) gera o evento de cancelamento por substituição e a nota substituta.
- `GET /nfse/{chaveAcesso}` — consulta a NFS-e.
- `GET /dps/{id}` e `HEAD /dps/{id}` — recupera a chave a partir do **identificador da DPS** (`"DPS" + cód. município IBGE (7) + tipo de inscrição (1) + inscrição federal (14, CPF com zeros à esquerda) + série (5) + número (15)`), para reconciliar quando a resposta do POST se perdeu.
- `POST /nfse/{chaveAcesso}/eventos` — registra um evento; o corpo é o **Pedido de Registro de Evento** (XML assinado, gzip+base64). O que importa aqui é o **cancelamento** (evento de cancelamento pelo emitente — código e leiaute no Anexo II e em `tiposEventos_v1.01.xsd`); `GET …/eventos`, `…/eventos/{tipo}`, `…/eventos/{tipo}/{seq}` para consultar.
- `GET /parametros_municipais/{codigoMunicipio}/convenio` e `GET /parametros_municipais/{codigoMunicipio}/{codigoServico}` — parâmetros do convênio e alíquotas/regimes por subitem da lista de serviço.

**Identificadores**: o `Id` da DPS é `"DPS" + 42 dígitos` (acima); o da NFS-e é `"NFS" + 50` — a numeração da **NFS-e é da SEFIN**, sequencial por emitente; a numeração da **DPS é do emitente** (série + número), e a lib **não** guarda estado: quem chama informa série e número (a aplicação persiste o contador).

**Leiaute da DPS** (`TCInfDPS`, v1.01): `tpAmb`, `dhEmi` (UTC), `verAplic`, `serie`, `nDPS`, `dCompet`, `tpEmit` (1 = prestador), `cLocEmi` (IBGE), `subst?`, `prest` (CNPJ/CPF, IM, nome, endereço, fone, e-mail, `regTrib` com opção pelo Simples Nacional e regime especial), `toma` (CPF/CNPJ/NIF, nome, endereço, e-mail), `interm?`, `serv` (`locPrest` com município IBGE da prestação, `cServ` com `cTribNac` (código de tributação nacional, LC 116), `cTribMun?`, `xDescServ`, `cNBS`, `cIntContrib?`, `infoCompl?`), `valores` (`vServPrest`, descontos, deduções, `trib` com `tribMun` — `tribISSQN`, `tpRetISSQN`, `pAliq` — e `totTrib` com `pTotTribSN` para o Simples), `IBSCBS?` (reforma tributária; deixe modelado e opcional). Modele **tudo** o que o XSD permite, mas com defaults e builders que tornem o caso comum curto.

## 3. API pública da biblioteca

Desenhe para o consumidor que não quer saber de XML. Sugestão (ajuste nomes se o padrão pedir):

```kotlin
// Propriedades: prefixo nfse.*
nfse.enabled=true
nfse.environment=PRODUCAO_RESTRITA | PRODUCAO
nfse.certificate.pfx-base64=...        // ou nfse.certificate.pfx-path=...
nfse.certificate.password=...
nfse.emitter.cnpj=...                  // ou cpf
nfse.emitter.municipal-registration=...
nfse.emitter.name=...
nfse.emitter.municipality-ibge=3548807
nfse.emitter.address.* (logradouro, número, complemento, bairro, CEP, município IBGE, UF)
nfse.emitter.email=..., nfse.emitter.phone=...
nfse.emitter.tax-regime=SIMPLES_NACIONAL | ...   // opSimpNac e regEspTrib do XSD
nfse.emitter.dps-series=1
nfse.application-version=...           // verAplic; default = nome+versão da lib
nfse.log-requests=false
nfse.base-url.sefin / .adn / .danfse   // sobrescrevem os defaults do ambiente
```

```kotlin
interface NfseClient {
    fun emit(request: DpsRequest): NfseResult             // POST /nfse; DpsRequest = o que varia por nota
    fun get(accessKey: String): Nfse                      // GET /nfse/{chave}
    fun accessKeyOf(dpsId: DpsId): String?                // GET /dps/{id}
    fun exists(dpsId: DpsId): Boolean                     // HEAD /dps/{id}
    fun cancel(accessKey: String, reason: CancellationReason, justification: String? = null): NfseEvent
    fun events(accessKey: String): List<NfseEvent>
    fun danfse(accessKey: String): ByteArray              // PDF
    fun municipalParameters(municipalityIbge: Int, serviceCode: String): MunicipalParameters
}
```

- `DpsRequest`: número da DPS (e série, se sobrescrever), data de competência, **tomador** (documento, nome, endereço, e-mail — tomador estrangeiro com NIF), **serviço** (`cTribNac`, `cTribMun?`, `cNBS`, descrição, município da prestação, info complementar), **valores** (valor do serviço em centavos ou `BigDecimal` — escolha um e documente; descontos; ISS: tributável/não tributável/isento/imune/exigibilidade suspensa, retido ou não, alíquota), `pTotTribSN?`, `substitutes: String?` (chave da nota a substituir), e o grupo IBS/CBS opcional. Os dados do **prestador vêm das propriedades**, mas permita sobrescrever por chamada (uma aplicação pode emitir para mais de um CNPJ).
- `NfseResult`: chave de acesso, número da NFS-e, data/hora, o XML da NFS-e (string) e o XML da DPS enviada (para a aplicação guardar).
- Também exponha as peças de baixo nível para quem quiser: `DpsXmlBuilder` (modelo → XML válido pelo XSD), `XmlSigner` (XML-DSig conforme o padrão: canonicalização C14N, RSA-SHA256, SHA-256, `Reference` ao `Id` da DPS, `KeyInfo` com o certificado — confirme o algoritmo exigido no Anexo I), `Gzip+Base64`, e o `RestClient` configurado (`nfseRestClient`), como a lib do PagBank expõe o dela.
- Exceções tipadas, uma hierarquia: `NfseException` → `NfseRejectedException(errors: List<NfseError(code, description)>)` (a SEFIN recusou a DPS/evento — erro de quem pediu), `NfseUnavailableException` (rede, 5xx, timeout), `NfseCertificateException` (certificado inválido/expirado/senha errada — falhe **no arranque**, não na primeira nota), `NfseValidationException` (a DPS não passou no XSD localmente — nem sai). Nunca logar a senha nem o conteúdo do certificado; `toString()` das propriedades sem segredos.

## 4. Testes

- Unitários: montagem do XML (comparar com XML esperado de um caso completo e de um mínimo), validação contra o XSD embarcado, identificador da DPS, assinatura (assinar e **verificar** com `javax.xml.crypto`), gzip/base64, mapeamento de cada resposta e de cada rejeição.
- Integração com **servidor HTTP stub** (como o `PagBankStub` da outra lib / MockWebServer / WireMock): emitir, consultar, cancelar, DANFSE, parâmetros municipais; 4xx com lista de erros; 5xx; timeout. O stub também deve exigir o certificado de cliente num caso (mTLS de verdade, com um certificado **gerado no próprio teste** com `keytool`/BouncyCastle — sem `.pfx` versionado no repositório).
- Teste de auto-configuração (`ApplicationContextRunner`): liga, desliga, propriedades obrigatórias, certificado inválido derruba o contexto com mensagem clara.
- Cobertura no mesmo piso da lib do PagBank.

## 5. Documentação e entrega

- `README.md`: o que é, instalação (Gradle/Maven), todas as propriedades numa tabela, exemplo de emissão + cancelamento + PDF, como testar na produção restrita, o que a lib **não** faz (numeração persistente, decisões fiscais).
- `CHANGELOG.md` e `CONTRIBUTING.md` no molde do PagBank; LICENSE igual.
- Um commit por passo lógico, Conventional Commits em inglês (como no repositório do PagBank). Ao terminar: build verde (`./gradlew build`), `spotlessCheck`, `detekt`, dependency-check sem alta, e a tag/release `1.0.0-RC1` pelo workflow de release.

## 6. O que NÃO fazer

- Não guardar estado (numeração, notas emitidas): é responsabilidade da aplicação.
- Não embutir valores fiscais (alíquotas, códigos de serviço, município): só os campos e as tabelas de domínio que vêm dos XSD (enums de `tribISSQN`, `tpRetISSQN`, regimes, tipos de evento).
- Não depender de biblioteca de terceiros para falar com a API além do Spring (`RestClient`) e da JDK para XML/assinatura; BouncyCastle só se a JDK não bastar, e documente o porquê.
- Não abrir a produção real em teste: `PRODUCAO` só por configuração explícita.

Se algo na documentação oficial contradisser este prompt, siga a documentação e registre a diferença no README (seção "Notes on the official docs").
