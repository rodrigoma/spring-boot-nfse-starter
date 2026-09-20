// Convention plugin that applies OWASP Dependency-Check to any subproject.
// Apply at root level to scan all dependency configurations across the project.

plugins {
    id("org.owasp.dependencycheck")
}

dependencyCheck {
    // Fail the build when a dependency has a CVSS score >= 7 (High or Critical).
    failBuildOnCVSS = 7f

    // Report formats: HTML for human review, JSON for CI artifact parsing.
    formats = listOf("HTML", "JSON")

    // Directory where reports are written (relative to each subproject's build dir).
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.absolutePath

    nvd {
        // Set NVD_API_KEY env var or Gradle property to avoid rate limiting.
        apiKey = System.getenv("NVD_API_KEY") ?: ""
    }
}
