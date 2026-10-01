plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hcmdz.privnum.data"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    // MigrationTestHelper loads exported schemas from test assets.
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.libphonenumber)
    implementation(libs.ez.vcard)
    implementation(libs.exifinterface)

    // ez-vcard declares jsoup, FreeMarker and Jackson as optional, for hCard,
    // jCard and templating, which this app never uses. Its 0.12.2 POM still
    // pins versions carrying known advisories, and no newer release exists yet:
    // upstream has open pull requests for Jackson (#161) and FreeMarker (#163).
    // Pinning the fixed versions keeps the advisory out of the graph without
    // touching behaviour, since R8 already strips these classes from the APK.
    constraints {
        implementation(libs.jsoup)
        implementation(libs.freemarker)
        implementation(libs.jackson.core)
    }

    implementation(libs.datastore.preferences)
    // Contacts are the app's whole value: the database is encrypted at rest
    // with a key held in the platform keystore.
    implementation(libs.sqlcipher.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.core.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.room.testing)
    // The passcode store gates its key on a strong biometric; the device test
    // needs the same API to skip when the device has none enrolled.
    androidTestImplementation(libs.biometric)
}
