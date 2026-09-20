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

signing {
    val signingKey = findProperty("signingKey") as String? ?: System.getenv("ORG_GRADLE_PROJECT_signingKey")
    val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("ORG_GRADLE_PROJECT_signingPassword")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}
