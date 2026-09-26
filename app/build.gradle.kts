buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("app.cash.licensee:licensee-gradle-plugin:1.14.1")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.serialization)
}

apply(plugin = "app.cash.licensee")

// The allow list is the audit. Every SPDX identifier below was read from the
// dependency's own metadata or licence file, never assumed; a `because` string
// documents every exception and lands in reports/licensee/validation.txt.
// The extension is configured through its type because a plugin applied with
// `apply(plugin = ...)` gets no type-safe script accessor.
extensions.configure<app.cash.licensee.LicenseeExtension> {
    allow("Apache-2.0")
    allow("BSD-3-Clause")
    allow("MIT")

    // Three artifacts ship a vendor URL instead of an SPDX id, so each is
    // accepted with the licence that was read out of the artifact itself.
    allowUrl("https://www.zetetic.net/sqlcipher/license/") {
        because("BSD-3-Clause, verbatim in the AAR's LICENSE; the vendor page carries no SPDX id")
    }
    allowUrl("https://jsoup.org/license") {
        because("MIT, read in META-INF/jsoup/LICENSE inside the jar; transitive via ez-vcard")
    }
    allowUrl("http://opensource.org/licenses/bsd-license.php") {
        because("BSD-2-Clause, read in ezvcard/ez-vcard.license inside the jar: two conditions, no endorsement clause. Same jar also vendors Apache Commons Codec under ezvcard/commons-codec.license")
    }
}

android {
    namespace = "com.hcmdz.privnum"
    compileSdk = 37
    base.archivesName = "Privnum"
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.hcmdz.privnum"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
        }
    }

    androidResources {
        localeFilters += listOf(
            "en",
            "fr",
            "es",
            "de",
            "pt-rBR",
            "ar",
            "hi",
            "in",
            "ja",
            "ko",
            "b+zh+Hans"
        )
    }

    signingConfigs {
        create("release") {
            val storeFilePath = providers.gradleProperty("RELEASE_STORE_FILE").getOrElse("")
            if (storeFilePath.isNotEmpty()) {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").getOrElse("")
                keyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").get()
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/INDEX.LIST",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:caller"))
    implementation(project(":feature:contacts"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:lock"))
    implementation(project(":feature:preview"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.fragment.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
