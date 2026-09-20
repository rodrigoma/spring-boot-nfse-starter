plugins {
    id("nfse.kotlin-library")
    id("nfse.publish")
}

group = "io.github.rodrigoma"

dependencies {
    api(project(":nfse-spring-boot-autoconfigure"))
    implementation("org.springframework.boot:spring-boot-starter")
}
