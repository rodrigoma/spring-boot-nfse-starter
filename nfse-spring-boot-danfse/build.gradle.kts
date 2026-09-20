plugins {
    id("nfse.kotlin-library")
    id("nfse.publish")
}

group = "io.github.rodrigoma"

dependencies {
    api(project(":nfse-spring-boot-autoconfigure"))
    // The JDK has no PDF or QR Code API; both libraries are Apache 2.0 and stay confined to this optional module.
    implementation("org.apache.pdfbox:pdfbox:3.0.7")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes("Implementation-Title" to project.name, "Implementation-Version" to project.version)
    }
}

// Renders the test fixtures to build/danfse/*.pdf for a visual check: ./gradlew :nfse-spring-boot-danfse:renderSamples
tasks.register<JavaExec>("renderSamples") {
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("io.github.rodrigoma.nfse.danfse.RenderSmoke")
    args(
        layout.buildDirectory
            .dir("danfse")
            .get()
            .asFile.absolutePath,
    )
}
