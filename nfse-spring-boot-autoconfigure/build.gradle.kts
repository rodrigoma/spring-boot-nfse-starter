plugins {
    id("nfse.kotlin-library")
    id("nfse.publish")
    kotlin("kapt")
}

group = "io.github.rodrigoma"

dependencies {
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.springframework.boot:spring-boot-starter-actuator")
    kapt(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    kapt("org.springframework.boot:spring-boot-configuration-processor")
    kapt("org.springframework.boot:spring-boot-health")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    // Test-only: the JDK has no public API to mint X.509 certificates, and the mTLS stub server and the
    // ICP-Brasil extension checks need certificates generated at test time (no .pfx in the repository).
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.80")
}

// The default `verAplic` (nfse.application-version) is read from this manifest entry at runtime
tasks.named<Jar>("jar") {
    manifest {
        attributes("Implementation-Title" to project.name, "Implementation-Version" to project.version)
    }
}

// A PKCS#12 minted by the build for the `classpath:` and `spring.ssl.bundle.*` tests — never a real certificate,
// and never committed. keytool ships with the JDK, so nothing else is needed to produce it.
val generatedTestCertificates = layout.buildDirectory.dir("generated-test-resources")

val generateTestCertificate by tasks.registering {
    val output = generatedTestCertificates.map { it.file("certificate/emitter.pfx") }
    outputs.file(output)
    doLast {
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.delete()
        providers
            .exec {
                commandLine(
                    "${System.getProperty("java.home")}/bin/keytool",
                    "-genkeypair",
                    "-alias",
                    "emitter",
                    "-keyalg",
                    "RSA",
                    "-keysize",
                    "2048",
                    "-storetype",
                    "PKCS12",
                    "-storepass",
                    "changeit",
                    "-keypass",
                    "changeit",
                    "-validity",
                    "365",
                    "-dname",
                    "CN=EMPRESA TESTE:12345678000195,O=ICP-Brasil,C=BR",
                    "-ext",
                    "KeyUsage=digitalSignature,nonRepudiation",
                    "-ext",
                    "BasicConstraints=ca:false",
                    "-keystore",
                    file.absolutePath,
                )
            }.result
            .get()
    }
}

sourceSets.test { resources.srcDir(generatedTestCertificates) }
tasks.named("processTestResources") { dependsOn(generateTestCertificate) }
