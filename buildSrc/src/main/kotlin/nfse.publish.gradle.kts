plugins {
    `maven-publish`
    signing
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set(project.name)
                description.set("Spring Boot starter for the Brazilian national NFS-e system (Sistema Nacional NFS-e)")
                url.set("https://github.com/rodrigoma/spring-boot-nfse-starter")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("rodrigoma")
                        name.set("Rodrigo Montanha")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/rodrigoma/spring-boot-nfse-starter.git")
                    developerConnection.set("scm:git:ssh://github.com:rodrigoma/spring-boot-nfse-starter.git")
                    url.set("https://github.com/rodrigoma/spring-boot-nfse-starter")
                }
            }
        }
    }

    repositories {
        maven {
            name = "local"
            url = uri(rootProject.layout.buildDirectory.dir("local-repo"))
        }
    }
}

/**
 * A Gradle property or the matching environment variable, `null` when neither carries a value. Blank counts as
 * absent: `System.getenv` of a secret that was never created returns an empty string, and an empty key reached
 * `useInMemoryPgpKeys` as if it were real, failing with `Could not read PGP secret key` — a message about the
 * key's contents for what is actually a missing secret.
 */
fun secret(name: String): String? =
    (findProperty(name) as String? ?: System.getenv("ORG_GRADLE_PROJECT_$name"))?.takeIf { it.isNotBlank() }

val signingKey = secret("signingKey")
val signingPassword = secret("signingPassword")
val signingConfigured = signingKey != null && signingPassword != null

signing {
    if (signingConfigured) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}

// Publishing to a repository is what a release does, and the Maven Central Portal rejects a bundle without
// signatures — so refuse it here, where the cause can be named, instead of after the upload. `publishToMavenLocal`
// is a different task type and stays unsigned, which is what local development and `./gradlew build` need.
tasks.withType<PublishToMavenRepository>().configureEach {
    doFirst {
        check(signingConfigured) {
            "Cannot publish $path without a signing key. Set the Gradle properties `signingKey` and " +
                "`signingPassword`, or the environment variables ORG_GRADLE_PROJECT_signingKey and " +
                "ORG_GRADLE_PROJECT_signingPassword (the SIGNING_KEY and SIGNING_PASSWORD secrets in CI). " +
                "Export the key with `gpg --armor --export-secret-keys <key-id>`, newlines included."
        }
    }
}
