plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.serialization)
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
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
        }
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

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.activity.compose)
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
