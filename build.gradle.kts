plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
}

// Detekt runs through its Gradle plugin instead of a downloaded jar: the jar
// only ever existed inside CI, so the task could not run on a developer
// machine. Applied at the root so one task covers every module source root.
detekt {
    source.setFrom(
        fileTree(projectDir) {
            include("*/src/main/java/**", "*/*/src/main/java/**")
        }
    )
    config.setFrom(file("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    buildUponDefaultConfig = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}
