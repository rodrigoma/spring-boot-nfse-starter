package io.github.rodrigoma.nfse.support

import io.github.rodrigoma.nfse.autoconfigure.NfseEnvironment
import io.github.rodrigoma.nfse.autoconfigure.NfseProperties
import io.github.rodrigoma.nfse.model.dps.AdditionalInfo
import io.github.rodrigoma.nfse.model.dps.Address
import io.github.rodrigoma.nfse.model.dps.AddressLocation
import io.github.rodrigoma.nfse.model.dps.Amounts
import io.github.rodrigoma.nfse.model.dps.BenefitReduction
import io.github.rodrigoma.nfse.model.dps.BrazilianState
import io.github.rodrigoma.nfse.model.dps.Construction
import io.github.rodrigoma.nfse.model.dps.ConstructionReference
import io.github.rodrigoma.nfse.model.dps.DeductionDocument
import io.github.rodrigoma.nfse.model.dps.DeductionDocumentReference
import io.github.rodrigoma.nfse.model.dps.DeductionType
import io.github.rodrigoma.nfse.model.dps.Deductions
import io.github.rodrigoma.nfse.model.dps.Deferral
import io.github.rodrigoma.nfse.model.dps.Dps
import io.github.rodrigoma.nfse.model.dps.EventActivity
import io.github.rodrigoma.nfse.model.dps.EventActivityReference
import io.github.rodrigoma.nfse.model.dps.FederalId
import io.github.rodrigoma.nfse.model.dps.FederalTax
import io.github.rodrigoma.nfse.model.dps.ForeignTrade
import io.github.rodrigoma.nfse.model.dps.GovernmentEntityType
import io.github.rodrigoma.nfse.model.dps.GovernmentOperationType
import io.github.rodrigoma.nfse.model.dps.IbsCbs
import io.github.rodrigoma.nfse.model.dps.IbsCbsAmounts
import io.github.rodrigoma.nfse.model.dps.IbsCbsTax
import io.github.rodrigoma.nfse.model.dps.IssqnTaxation
import io.github.rodrigoma.nfse.model.dps.IssqnWithholding
import io.github.rodrigoma.nfse.model.dps.MunicipalBenefit
import io.github.rodrigoma.nfse.model.dps.MunicipalTax
import io.github.rodrigoma.nfse.model.dps.NationalDocumentKeyType
import io.github.rodrigoma.nfse.model.dps.PartiesRelationship
import io.github.rodrigoma.nfse.model.dps.Person
import io.github.rodrigoma.nfse.model.dps.PisCofins
import io.github.rodrigoma.nfse.model.dps.PisCofinsWithholding
import io.github.rodrigoma.nfse.model.dps.PropertyInfo
import io.github.rodrigoma.nfse.model.dps.PropertyReference
import io.github.rodrigoma.nfse.model.dps.ProvisionMode
import io.github.rodrigoma.nfse.model.dps.Recipient
import io.github.rodrigoma.nfse.model.dps.RecipientIndicator
import io.github.rodrigoma.nfse.model.dps.RegularTaxation
import io.github.rodrigoma.nfse.model.dps.ReimbursementDocument
import io.github.rodrigoma.nfse.model.dps.ReimbursementDocumentReference
import io.github.rodrigoma.nfse.model.dps.ReimbursementSupplier
import io.github.rodrigoma.nfse.model.dps.ReimbursementType
import io.github.rodrigoma.nfse.model.dps.ServiceCode
import io.github.rodrigoma.nfse.model.dps.ServiceInfo
import io.github.rodrigoma.nfse.model.dps.ServiceLocation
import io.github.rodrigoma.nfse.model.dps.ServiceProvider
import io.github.rodrigoma.nfse.model.dps.SimpleAddress
import io.github.rodrigoma.nfse.model.dps.SimpleAddressLocation
import io.github.rodrigoma.nfse.model.dps.SimplesNacionalAssessment
import io.github.rodrigoma.nfse.model.dps.SimplesNacionalOption
import io.github.rodrigoma.nfse.model.dps.SpecialTaxRegime
import io.github.rodrigoma.nfse.model.dps.Substitution
import io.github.rodrigoma.nfse.model.dps.SubstitutionReason
import io.github.rodrigoma.nfse.model.dps.SuspendedEnforceability
import io.github.rodrigoma.nfse.model.dps.SuspensionType
import io.github.rodrigoma.nfse.model.dps.TaxRegime
import io.github.rodrigoma.nfse.model.dps.Taxes
import io.github.rodrigoma.nfse.model.dps.TemporaryGoodsMovement
import io.github.rodrigoma.nfse.model.dps.TotalTaxes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

object TestDps {
    const val CNPJ = TestCertificates.CNPJ
    const val MUNICIPALITY = 3550308
    val issuedAt: OffsetDateTime = OffsetDateTime.of(2026, 9, 19, 10, 0, 0, 0, ZoneOffset.ofHours(-3))
    val competence: LocalDate = LocalDate.of(2026, 9, 19)

    val provider =
        ServiceProvider(
            id = FederalId.Cnpj(CNPJ),
            municipalRegistration = "12345",
            taxRegime = TaxRegime(SimplesNacionalOption.NOT_OPTING),
        )

    val taker = Person(id = FederalId.Cpf("12345678909"), name = "Fulano de Tal")

    fun minimal(): Dps =
        Dps(
            environment = NfseEnvironment.RESTRICTED_PRODUCTION,
            issuedAt = issuedAt,
            applicationVersion = "test/1.0",
            series = 1,
            number = 1,
            competenceDate = competence,
            emitterMunicipalityIbge = MUNICIPALITY,
            provider = provider,
            taker = taker,
            service =
                ServiceInfo(
                    location = ServiceLocation.Municipality(MUNICIPALITY),
                    code = ServiceCode(nationalTaxCode = "010701", description = "Consultoria"),
                ),
            amounts =
                Amounts(
                    serviceAmount = BigDecimal("100.00"),
                    taxes = Taxes(municipal = MunicipalTax(rate = BigDecimal("2.00"))),
                ),
        )

    private val nationalAddress =
        Address(
            location = AddressLocation.National(MUNICIPALITY, "01310100"),
            street = "Av. Paulista",
            number = "1000",
            complement = "10º andar",
            district = "Bela Vista",
        )

    private val simpleAddress =
        SimpleAddress(SimpleAddressLocation.ZipCode("01310100"), "Rua da Obra", "10", null, "Centro")

    @Suppress("LongMethod")
    fun complete(): Dps =
        minimal().copy(
            substitution = Substitution("1".repeat(50), SubstitutionReason.OTHER, "Correção de valores do serviço"),
            provider =
                provider.copy(
                    caepf = "12345678901234",
                    address = nationalAddress,
                    phone = "11999998888",
                    email = "financeiro@empresa.com.br",
                    taxRegime =
                        TaxRegime(
                            SimplesNacionalOption.ME_EPP,
                            SimplesNacionalAssessment.SIMPLES_NACIONAL,
                            SpecialTaxRegime.NONE,
                        ),
                ),
            taker =
                Person(
                    id = FederalId.Nif("GB123456789"),
                    name = "Foreign Customer Ltd",
                    address =
                        Address(
                            location = AddressLocation.Foreign("GB", "SW1A 1AA", "London", "England"),
                            street = "Downing Street",
                            number = "10",
                            district = "Westminster",
                        ),
                    email = "buyer@customer.co.uk",
                ),
            intermediary =
                Person(FederalId.Cnpj("98765432000198"), municipalRegistration = "777", name = "Intermediária SA"),
            service =
                ServiceInfo(
                    location = ServiceLocation.Country("GB"),
                    code =
                        ServiceCode(
                            nationalTaxCode = "010701",
                            municipalTaxCode = "123",
                            description = "Consultoria em TI\nLinha 2",
                            nbsCode = "115011000",
                            internalCode = "SVC001",
                        ),
                    foreignTrade =
                        ForeignTrade(
                            provisionMode = ProvisionMode.CROSS_BORDER,
                            partiesRelationship = PartiesRelationship.NONE,
                            currencyCode = "978",
                            amountInCurrency = BigDecimal("1500.50"),
                            providerSupportMechanism = "00",
                            takerSupportMechanism = "00",
                            temporaryGoodsMovement = TemporaryGoodsMovement.NO,
                            importDeclaration = "DI123",
                            exportRegistration = "RE456",
                            shareWithMdic = true,
                        ),
                    construction =
                        Construction(
                            propertyRegistration = "IMOB-1",
                            reference = ConstructionReference.Location(simpleAddress),
                        ),
                    eventActivity =
                        EventActivity(
                            "Feira",
                            LocalDate.of(2026, 9, 1),
                            LocalDate.of(2026, 9, 5),
                            EventActivityReference.Id("EVT-1"),
                        ),
                    additionalInfo =
                        AdditionalInfo(
                            technicalDocumentId = "ART-1",
                            referenceDocument = "Contrato 42",
                            purchaseOrder = "PO-1",
                            purchaseOrderItems = listOf("1", "2"),
                            text = "Informações complementares",
                        ),
                ),
            amounts =
                Amounts(
                    serviceAmount = BigDecimal("1000"),
                    receivedAmount = BigDecimal("900"),
                    unconditionalDiscount = BigDecimal("50"),
                    conditionalDiscount = BigDecimal("25"),
                    deductions =
                        Deductions.Documents(
                            listOf(
                                DeductionDocument(
                                    reference = DeductionDocumentReference.NfseKey("2".repeat(50)),
                                    type = DeductionType.MATERIALS,
                                    issueDate = LocalDate.of(2026, 8, 1),
                                    deductibleAmount = BigDecimal("100"),
                                    deductedAmount = BigDecimal("80"),
                                    supplier = Person(FederalId.Cnpj("11222333000181"), name = "Fornecedor"),
                                ),
                                DeductionDocument(
                                    reference =
                                        DeductionDocumentReference.MunicipalNfse(
                                            MUNICIPALITY,
                                            "1".repeat(15),
                                            "ABC123",
                                        ),
                                    type = DeductionType.OTHER,
                                    otherDescription = "Outra dedução",
                                    issueDate = LocalDate.of(2026, 8, 2),
                                    deductibleAmount = BigDecimal("10"),
                                    deductedAmount = BigDecimal("10"),
                                ),
                            ),
                        ),
                    taxes =
                        Taxes(
                            municipal =
                                MunicipalTax(
                                    taxation = IssqnTaxation.TAXABLE,
                                    suspendedEnforceability =
                                        SuspendedEnforceability(SuspensionType.JUDICIAL_DECISION, "123456"),
                                    municipalBenefit =
                                        MunicipalBenefit("1".repeat(14), BenefitReduction.Percentage(BigDecimal("10"))),
                                    withholding = IssqnWithholding.WITHHELD_BY_TAKER,
                                    rate = BigDecimal("5"),
                                ),
                            federal =
                                FederalTax(
                                    pisCofins =
                                        PisCofins(
                                            cst = "01",
                                            calculationBase = BigDecimal("1000"),
                                            pisRate = BigDecimal("0.65"),
                                            cofinsRate = BigDecimal("3"),
                                            pisAmount = BigDecimal("6.50"),
                                            cofinsAmount = BigDecimal("30"),
                                            withholding = PisCofinsWithholding.PIS_COFINS_WITHHELD,
                                        ),
                                    withheldSocialSecurity = BigDecimal("110"),
                                    withheldIncomeTax = BigDecimal("15"),
                                    withheldCsll = BigDecimal("10"),
                                ),
                            total = TotalTaxes.Percentages(BigDecimal("13.45"), BigDecimal("0"), BigDecimal("5")),
                        ),
                ),
            ibsCbs =
                IbsCbs(
                    personalUse = false,
                    operationIndicatorCode = "010101",
                    governmentOperationType = GovernmentOperationType.SUPPLY_WITH_LATER_PAYMENT,
                    referencedNfse = listOf("3".repeat(50)),
                    governmentEntityType = GovernmentEntityType.MUNICIPAL,
                    recipientIndicator = RecipientIndicator.OTHER_RECIPIENT,
                    recipient =
                        Recipient(
                            FederalId.Cpf("98765432100"),
                            "Destinatário",
                            nationalAddress,
                            "1133334444",
                            "dest@x.com",
                        ),
                    property = PropertyInfo("INSC-1", PropertyReference.Location(simpleAddress)),
                    amounts =
                        IbsCbsAmounts(
                            reimbursements =
                                listOf(
                                    ReimbursementDocument(
                                        reference =
                                            ReimbursementDocumentReference.NationalDocument(
                                                NationalDocumentKeyType.NFE,
                                                "NF-e",
                                                "4".repeat(44),
                                            ),
                                        supplier =
                                            ReimbursementSupplier(FederalId.Cnpj("11222333000181"), "Fornecedor"),
                                        issueDate = LocalDate.of(2026, 8, 1),
                                        competenceDate = LocalDate.of(2026, 8, 1),
                                        type = ReimbursementType.OTHER,
                                        typeDescription = "Repasse",
                                        amount = BigDecimal("12.34"),
                                    ),
                                ),
                            tax =
                                IbsCbsTax(
                                    cst = "000",
                                    classificationCode = "000001",
                                    presumedCreditCode = "01",
                                    regularTaxation = RegularTaxation("000", "000001"),
                                    deferral = Deferral(BigDecimal("1"), BigDecimal("2"), BigDecimal("3")),
                                ),
                        ),
                ),
        )

    fun properties(
        pfxPath: String,
        sefinBaseUrl: String? = null,
        trustStorePath: String? = null,
    ): NfseProperties =
        NfseProperties(
            certificate =
                NfseProperties.Certificate(
                    pfxPath = pfxPath,
                    password = TestCertificates.PASSWORD,
                    trustStorePath = trustStorePath,
                    trustStorePassword = trustStorePath?.let { TestCertificates.PASSWORD },
                ),
            emitter =
                NfseProperties.Emitter(
                    cnpj = CNPJ,
                    municipalRegistration = "12345",
                    municipalityIbge = MUNICIPALITY,
                    address =
                        NfseProperties.Emitter.Address(
                            street = "Av. Paulista",
                            number = "1000",
                            district = "Bela Vista",
                            zipCode = "01310100",
                            state = BrazilianState.SP,
                        ),
                    email = "financeiro@empresa.com.br",
                    phone = "11999998888",
                ),
            applicationVersion = "test/1.0",
            baseUrl = NfseProperties.BaseUrl(sefin = sefinBaseUrl, danfse = sefinBaseUrl?.let { "$it/danfse" }),
        ).also { it.afterPropertiesSet() }
}
