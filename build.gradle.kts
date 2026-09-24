plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register<Exec>("detekt") {
    description = "Run detekt static analysis"
    group = "verification"
    workingDir = rootDir
    isIgnoreExitValue = true
    commandLine(
        "java", "-jar", "${rootDir}/.detekt/detekt-cli-1.23.8-all.jar",
        "--input", "app/src/main/java,core/src/main/java,feature/src/main/java",
        "--config", "config/detekt/detekt.yml",
        "--jvm-target", "17",
        "--report", "html:${rootDir}/build/reports/detekt/detekt.html",
        "--report", "sarif:${rootDir}/build/reports/detekt/detekt.sarif",
        "--baseline", "config/detekt/baseline.xml"
    )
}
