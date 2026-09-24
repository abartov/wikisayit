import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing is optional at the Gradle level: build_and_sign.sh generates
// keystore.properties (and the keystore itself) on first run. Neither is ever
// committed, so debug/test builds and CI checkout must keep working without them.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

// The single source of truth for the app's version, so it never drifts out of sync
// across the manifest, the About screen, and anywhere else it's displayed.
val appVersionName = rootProject.file("VERSION").readText().trim()

// Interface languages offered in Settings, computed from which `values-<lang>` resource
// directories actually exist — so translations landing from Translatewiki.net become
// selectable with nothing but a rebuild, per s-614. Locale aliases Android normalizes away
// (e.g. "iw" for Hebrew) are excluded so they don't duplicate their modern-code entry.
val legacyLocaleAliases = setOf("iw", "in", "ji")
val languageDirPattern = Regex("^values-([a-z]{2,3})(-r([A-Z]{2}))?$")
val bcp47DirPattern = Regex("^values-b\\+(.+)$")
val supportedInterfaceLanguages: List<String> =
    run {
        val tags = mutableSetOf("en")
        file("src/main/res").listFiles { f -> f.isDirectory }?.forEach { dir ->
            languageDirPattern.matchEntire(dir.name)?.let { match ->
                val (lang, _, region) = match.destructured
                if (lang !in legacyLocaleAliases) tags += if (region.isEmpty()) lang else "$lang-$region"
            }
            bcp47DirPattern.matchEntire(dir.name)?.let { match ->
                tags += match.groupValues[1].replace("+", "-")
            }
        }
        tags.sorted()
    }

// versionCode lives in its own file as a plain integer so F-Droid's update checker can
// read it (it can't evaluate Gradle), and it must be deterministic for F-Droid to rebuild
// a release. It's derived from VERSION as 2_000_000 + major*10_000 + minor*100 + patch;
// the 2_000_000 offset keeps it above the minutes-since-2024 codes used before 0.2.9.
// The build fails if the two files drift, so a release bumps both together.
val appVersionCode = rootProject.file("VERSION_CODE").readText().trim().toInt()
run {
    val (major, minor, patch) = appVersionName.split(".").map { it.toInt() }
    val expected = 2_000_000 + major * 10_000 + minor * 100 + patch
    check(appVersionCode == expected) {
        "VERSION_CODE is $appVersionCode but VERSION $appVersionName requires $expected"
    }
}

android {
    namespace = "wiki.asaf.wikisayit"
    compileSdk = 36

    defaultConfig {
        applicationId = "wiki.asaf.wikisayit"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPPORTED_INTERFACE_LANGUAGES",
            "\"${supportedInterfaceLanguages.joinToString(",")}\"",
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // AGP otherwise embeds a dependency-metadata block encrypted with Google's key, which
    // F-Droid rejects as an opaque blob. Play only needs it in the bundle.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    sourceSets {
        getByName("main").kotlin.srcDirs("src/main/kotlin")
        getByName("test").kotlin.srcDirs("src/test/kotlin")
        getByName("androidTest").kotlin.srcDirs("src/androidTest/kotlin")
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.appcompat)
    implementation(libs.axet.vorbis)
    implementation(libs.androidx.browser)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
