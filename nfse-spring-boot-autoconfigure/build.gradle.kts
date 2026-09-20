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
