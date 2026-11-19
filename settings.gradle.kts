pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io"); content { includeGroupAndSubgroups("com.github.MuntashirAkon") } }
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
            maven {
                url = uri("https://maven.pkg.github.com/N2K02/n2k02AdbCoreLibrary")
                credentials { username = "N2K02"; password = token }
                content { includeModule("dev.n2k02", "adb-core") }
            }
        }
    }
}
rootProject.name = "MorpheAutoMirror"
include(":app")


if (providers.gradleProperty("useSharedSources").orNull == "true") {
    // Public, reproducible source builds let CI test even before a Packages token exists.
    includeBuild("shared/n2k02theme") {
        dependencySubstitution { substitute(module("dev.n2k02:theme")).using(project(":theme")) }
    }
    includeBuild("shared/n2k02updater") {
        dependencySubstitution { substitute(module("dev.n2k02:updater")).using(project(":updater")) }
    }
    includeBuild("shared/n2k02AdbCoreLibrary") {
        dependencySubstitution { substitute(module("dev.n2k02:adb-core")).using(project(":adb-core")) }
    }
}
