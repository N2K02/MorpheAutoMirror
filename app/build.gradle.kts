plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val appVersion = (findProperty("appVersion") as String?) ?: "1.0"
require(Regex("""\d+\.\d""").matches(appVersion))
android {
    namespace = "dev.n2k02.morpheautomirror"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.n2k02.morpheautomirror"
        minSdk = 26
        targetSdk = 36
        versionName = appVersion + ((findProperty("appVersionSuffix") as String?) ?: "")
        versionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull()
            ?: (appVersion.substringBefore('.').toInt() * 10 + appVersion.substringAfter('.').toInt())
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val key = rootProject.file("signing/release.jks")
            if (key.exists()) {
                signingConfig = signingConfigs.maybeCreate("ci").apply {
                    storeFile = key
                    storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                    keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                    keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                }
            }
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("dev.n2k02:theme:0.1.14")
    implementation("dev.n2k02:updater:0.1.36")
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Optional shell-UID bridge supplied by LocalAdb's embedded Shizuku manager.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}

