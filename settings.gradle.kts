pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        val token = providers.environmentVariable("N2K02_PACKAGES_TOKEN").orNull
        if (!token.isNullOrBlank()) {
            maven {
                url = uri("https://maven.pkg.github.com/N2K02/n2k02theme")
                credentials { username = "N2K02"; password = token }
                content { includeModule("dev.n2k02", "theme") }
            }
            maven {
                url = uri("https://maven.pkg.github.com/N2K02/n2k02updater")
                credentials { username = "N2K02"; password = token }
                content { includeModule("dev.n2k02", "updater") }
            }
        }
    }
}
rootProject.name = "MorpheAutoMirror"
include(":app")

