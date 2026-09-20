plugins {
    id("nfse.kotlin-library")
    id("org.springframework.boot")
}

group = "io.github.rodrigoma"
version = rootProject.version

dependencies {
    implementation(project(":nfse-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")
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
