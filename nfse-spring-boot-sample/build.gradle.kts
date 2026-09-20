plugins {
    id("nfse.kotlin-library")
    id("org.springframework.boot")
}

group = "io.github.rodrigoma"
version = rootProject.version

dependencies {
    implementation(project(":nfse-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Only for the `local` profile: mints the sandbox certificates (the JDK has no public API for that)
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")
}

// Spring Boot produces the fat jar; disable the plain jar task
tasks.named<Jar>("jar") {
    enabled = false
}
tasks.named("sourcesJar") { enabled = false }
tasks.named("javadocJar") { enabled = false }

// Disable the JaCoCo coverage gate for the sample module
tasks.named("jacocoTestCoverageVerification") {
    enabled = false
}
