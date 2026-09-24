pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Privnum"
include(":app")
include(":core:ui")
include(":core:data")
include(":core:caller")
include(":feature:contacts")
include(":feature:editor")
include(":feature:search")
include(":feature:settings")
include(":feature:lock")
include(":feature:preview")
